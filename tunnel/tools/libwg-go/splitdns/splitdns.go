// SPDX-License-Identifier: Apache-2.0
//
// Package splitdns implements per-domain split-tunnel DNS for the VPN backend.
//
// It wraps the OS tun.Device that sits underneath the wireguard device. Outbound
// DNS queries (UDP/53) are inspected: queries whose name matches the configured
// domain list are passed straight through so they are resolved by the tunnel's
// DNS server (over the tunnel), while all other queries are resolved locally
// against the underlying system DNS servers using a tunnel-bypassing socket and
// answered directly. Non-DNS traffic is untouched.
package splitdns

import (
	"context"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/tun"
	"github.com/miekg/dns"
)

const (
	resolveTimeout  = 5 * time.Second
	// Per-server slice of the overall budget. UDP "dials" succeed instantly even
	// toward unreachable addresses, so without this a single dead server would
	// consume the entire resolveTimeout in its read and starve every fallback.
	perServerTimeout = 2 * time.Second
	maxInFlight      = 64
	maxResponseSize  = 65535
)

// LogFunc is a logging hook. The caller wires these to the platform logger;
// they default to no-ops so this package builds without any cgo dependency.
type LogFunc func(format string, args ...any)

var (
	logDebug LogFunc = func(string, ...any) {}
	logError LogFunc = func(string, ...any) {}
)

// SetLoggers installs debug/error logging hooks for the package.
func SetLoggers(debug, errorf LogFunc) {
	if debug != nil {
		logDebug = debug
	}
	if errorf != nil {
		logError = errorf
	}
}

// ContextDialer dials connections. *net.Dialer satisfies it; callers pass a
// dialer configured to bypass the tunnel (protected socket).
type ContextDialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}

// Device wraps an inner tun.Device, intercepting non-matching DNS queries.
// Methods we do not customize (File, MTU, Name, Events, BatchSize) promote
// through the embedded tun.Device.
type Device struct {
	tun.Device

	matcher *Matcher
	dialer  ContextDialer

	serversMu sync.RWMutex
	servers   []string

	writeMu sync.Mutex
	sem     chan struct{}

	ctx    context.Context
	cancel context.CancelFunc
}

// NewDevice wraps inner so that DNS queries not matching the configured domains
// are resolved against the system DNS servers via the bypassing dialer.
func NewDevice(inner tun.Device, matcher *Matcher, systemServers []string, dialer ContextDialer) *Device {
	ctx, cancel := context.WithCancel(context.Background())
	return &Device{
		Device:  inner,
		matcher: matcher,
		servers: normalizeServers(systemServers),
		dialer:  dialer,
		sem:     make(chan struct{}, maxInFlight),
		ctx:     ctx,
		cancel:  cancel,
	}
}

// SetServers replaces the system DNS server list, e.g. after the underlying
// network changes. It only affects where non-matching queries are resolved;
// queries matching the split domain list always pass through to the tunnel.
func (d *Device) SetServers(servers []string) {
	normalized := normalizeServers(servers)
	d.serversMu.Lock()
	d.servers = normalized
	d.serversMu.Unlock()
	logDebug("system DNS servers updated: %v", normalized)
}

func (d *Device) currentServers() []string {
	d.serversMu.RLock()
	defer d.serversMu.RUnlock()
	return d.servers
}

func normalizeServers(servers []string) []string {
	out := make([]string, 0, len(servers))
	for _, s := range servers {
		if _, _, err := net.SplitHostPort(s); err != nil {
			s = net.JoinHostPort(s, "53")
		} else {
			// Clone entries we keep as-is: callers may pass strings that alias a
			// borrowed cgo/JNI buffer which is freed after the call returns.
			s = strings.Clone(s)
		}
		out = append(out, s)
	}
	return out
}

// Read pulls packets from the inner device, intercepting DNS queries that should
// be resolved outside the tunnel. Intercepted queries are removed from the batch
// returned to the wireguard device and handled asynchronously.
func (d *Device) Read(bufs [][]byte, sizes []int, offset int) (int, error) {
	for {
		n, err := d.Device.Read(bufs, sizes, offset)
		if err != nil || n == 0 {
			return n, err
		}

		w := 0
		for i := 0; i < n; i++ {
			pkt := bufs[i][offset : offset+sizes[i]]
			if d.intercept(pkt) {
				continue
			}
			if w != i {
				copy(bufs[w][offset:], bufs[i][offset:offset+sizes[i]])
				sizes[w] = sizes[i]
			}
			w++
		}

		if w > 0 {
			return w, nil
		}
		// Every packet in this batch was intercepted; read again rather than
		// returning zero packets to the device read loop.
	}
}

// intercept returns true if pkt was a non-matching DNS query that we took
// ownership of (and therefore must be dropped from the wireguard read path).
func (d *Device) intercept(pkt []byte) bool {
	q, err := parseDNSQuery(pkt)
	if err != nil {
		return false
	}

	name, ok := questionName(q.payload)
	if !ok {
		// Unparseable DNS — let it flow to the tunnel resolver.
		return false
	}

	if d.matcher.Matches(name) {
		return false // resolve through the tunnel
	}

	// Copy the query bytes; the underlying buffer is reused by the read loop.
	query := make([]byte, len(q.payload))
	copy(query, q.payload)
	meta := *q
	meta.payload = query

	select {
	case d.sem <- struct{}{}:
		go d.resolveAndReply(&meta)
		return true
	default:
		// Too many in-flight resolutions; fall back to tunnel resolution.
		logDebug("resolver saturated, passing %s to tunnel", name)
		return false
	}
}

func questionName(payload []byte) (string, bool) {
	var msg dns.Msg
	if err := msg.Unpack(payload); err != nil {
		return "", false
	}
	if len(msg.Question) == 0 {
		return "", false
	}
	return msg.Question[0].Name, true
}

// buildServfail builds a SERVFAIL response to the given DNS query payload.
func buildServfail(query []byte) ([]byte, bool) {
	var req dns.Msg
	if err := req.Unpack(query); err != nil {
		return nil, false
	}
	var res dns.Msg
	res.SetRcode(&req, dns.RcodeServerFailure)
	out, err := res.Pack()
	if err != nil {
		return nil, false
	}
	return out, true
}

func (d *Device) resolveAndReply(q *udpDatagram) {
	defer func() { <-d.sem }()

	reply, err := d.resolve(q.payload)
	if err != nil {
		logError("failed to resolve via system DNS: %v", err)
		// Answer SERVFAIL instead of dropping so the client fails fast and
		// retries (by which point the server list may have been refreshed).
		// Only non-matching queries ever reach this path, so no query for a
		// split-listed (internal) domain is exposed or answered here.
		servfail, ok := buildServfail(q.payload)
		if !ok {
			return
		}
		reply = servfail
	}

	pkt := buildResponse(q, reply)

	// Offset 0 is correct for the Android VpnService tun, which is created without
	// IFF_VNET_HDR, so NativeTun.Write writes each buffer from index 0 with no
	// virtio header headroom.
	d.writeMu.Lock()
	_, err = d.Device.Write([][]byte{pkt}, 0)
	d.writeMu.Unlock()
	if err != nil {
		logError("failed to write DNS reply: %v", err)
	}
}

func (d *Device) resolve(query []byte) ([]byte, error) {
	ctx, cancel := context.WithTimeout(d.ctx, resolveTimeout)
	defer cancel()

	var lastErr error
	for _, server := range d.currentServers() {
		serverCtx, serverCancel := context.WithTimeout(ctx, perServerTimeout)
		reply, err := d.exchangeWith(serverCtx, server, query)
		serverCancel()
		if err == nil {
			return reply, nil
		}
		lastErr = err
		if ctx.Err() != nil {
			break
		}
	}

	if lastErr == nil {
		lastErr = net.ErrClosed
	}
	return nil, lastErr
}

func (d *Device) exchangeWith(ctx context.Context, server string, query []byte) ([]byte, error) {
	conn, err := d.dialer.DialContext(ctx, "udp", server)
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	return exchange(ctx, conn, query)
}

func exchange(ctx context.Context, conn net.Conn, query []byte) ([]byte, error) {
	if deadline, ok := ctx.Deadline(); ok {
		_ = conn.SetDeadline(deadline)
	}
	if _, err := conn.Write(query); err != nil {
		return nil, err
	}
	buf := make([]byte, maxResponseSize)
	n, err := conn.Read(buf)
	if err != nil {
		return nil, err
	}
	return buf[:n], nil
}

// Write forwards to the inner device under the same lock used for synthesized
// DNS replies so concurrent writes do not interleave.
func (d *Device) Write(bufs [][]byte, offset int) (int, error) {
	d.writeMu.Lock()
	defer d.writeMu.Unlock()
	return d.Device.Write(bufs, offset)
}

// Close cancels in-flight resolutions and closes the inner device.
func (d *Device) Close() error {
	d.cancel()
	return d.Device.Close()
}
