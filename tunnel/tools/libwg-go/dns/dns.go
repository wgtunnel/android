package dns

/*
#cgo LDFLAGS: -landroid
#include "vpn_jni.h"
*/
import "C"
import (
	"bytes"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"syscall"
	"time"

	"github.com/miekg/dns"
	"github.com/wgtunnel/android/shared"
	"golang.org/x/sys/unix"
)

type Resolved struct {
	V4 []netip.Addr
	V6 []netip.Addr
}

type ResolverOptions struct {
	UpstreamURL string
	Timeout     time.Duration
}

type Transport interface {
	Query(ctx context.Context, msg *dns.Msg) (*dns.Msg, error)
}

//export ResolveBootstrap
func ResolveBootstrap(
	host *C.char,
	protocol *C.char,
	resolvedUpstream *C.char,
	originalUpstream *C.char,
	bypass C.int,
) *C.char {

	h := C.GoString(host)
	p := C.GoString(protocol)
	resolved := C.GoString(resolvedUpstream)
	original := C.GoString(originalUpstream)
	bp := bypass == 1

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	shared.LogDebug("DNS", "ResolveBootstrap called host=%s protocol=%s resolved=%s original=%s bypass=%t",
		h, p, resolved, original, bp)

	v4, v6, err := Resolve(ctx, h, p, resolved, original, bp)
	if err != nil {
		shared.LogError("DNS", "ResolveBootstrap failed for %s: %v", h, err)
		return C.CString("ERR|" + err.Error())
	}

	result := fmt.Sprintf("v4=%s;v6=%s",
		strings.Join(toStringSlice(v4), ","),
		strings.Join(toStringSlice(v6), ","),
	)

	shared.LogDebug("DNS", "ResolveBootstrap success for %s: %s", h, result)
	return C.CString(result)
}

func toStringSlice(addrs []netip.Addr) []string {
	out := make([]string, len(addrs))
	for i, a := range addrs {
		out[i] = a.String()
	}
	return out
}

type DoTTransport struct {
	Client  *dns.Client
	Servers []string
}

type DoHTransport struct {
	Client   *http.Client
	URL      string
	Servers  []string // IPv4 first, IPv6 fallback
	Hostname string   // for SNI and Host header
}

type PlainTransport struct {
	Client  *dns.Client
	Servers []string
}

func resolveHost(
	ctx context.Context,
	t Transport,
	host string,
) (v4, v6 []netip.Addr, err error) {
	a4, e4 := resolveQ(ctx, t, host, dns.TypeA)
	if e4 == nil {
		v4 = a4
	}
	a6, e6 := resolveQ(ctx, t, host, dns.TypeAAAA)
	if e6 == nil {
		v6 = a6
	}

	if len(v4) > 0 || len(v6) > 0 {
		return v4, v6, nil
	}
	return nil, nil, errors.Join(e4, e6)
}

func resolveQ(
	ctx context.Context,
	t Transport,
	host string,
	qtype uint16,
) ([]netip.Addr, error) {
	req := &dns.Msg{}
	req.SetQuestion(dns.Fqdn(host), qtype)
	req.SetEdns0(4096, true)

	res, err := t.Query(ctx, req)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, fmt.Errorf("nil DNS response")
	}
	if res.Rcode != dns.RcodeSuccess {
		return nil, fmt.Errorf("rcode %d", res.Rcode)
	}

	addrs := parseDNSAnswers(res, qtype)
	if len(addrs) == 0 {
		return nil, fmt.Errorf("no answers for qtype %d", qtype)
	}
	return addrs, nil
}

func parseUpstream(upstreamURL string) (network, address string, err error) {
	shared.LogDebug("DNS", "Parsing upstream URL: %s", upstreamURL)
	u := upstreamURL
	if !strings.Contains(u, "://") {
		u = normalizeHostPort(u)
		u = "udp://" + u
	}
	parsed, err := url.Parse(u)
	if err != nil {
		shared.LogError("DNS", "parseUpstream failed for %q: %v", upstreamURL, err)
		return "", "", fmt.Errorf("invalid upstream URL %q: %w", upstreamURL, err)
	}

	switch parsed.Scheme {
	case "udp", "":
		network = "udp"
	case "tcp":
		network = "tcp"
	default:
		err = fmt.Errorf("unsupported upstream scheme %q (only udp:// and tcp:// supported for plain DNS)", parsed.Scheme)
		shared.LogError("DNS", "%v", err)
		return "", "", err
	}

	host := parsed.Hostname()
	port := parsed.Port()
	if port == "" {
		port = "53"
	}
	address = net.JoinHostPort(host, port)
	shared.LogDebug("DNS", "Parsed upstream -> network=%s address=%s", network, address)
	return network, address, nil
}

func newUnderlyingResolver(bypass bool, underlying string) *net.Resolver {
	if !bypass {
		return &net.Resolver{PreferGo: false}
	}

	rawServers := strings.Split(underlying, ",")
	var servers []string

	for _, s := range rawServers {
		s = strings.TrimSpace(s)
		if s == "" {
			continue
		}

		if !strings.Contains(s, ":") {
			s = net.JoinHostPort(s, "53")
		}
		servers = append(servers, s)
	}

	if len(servers) == 0 {
		servers = []string{"1.1.1.1:53"}
	}

	return &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			for _, server := range servers {
				conn, err := GetDialer(true).DialContext(ctx, network, server)
				if err == nil {
					shared.LogDebug("DNS", "Using underlying bootstrap resolver: %s", server)
					return conn, nil
				}
				shared.LogDebug("DNS", "Bootstrap resolver failed for %s: %v", server, err)
			}
			return nil, fmt.Errorf("all underlying DNS servers failed")
		},
	}
}

func resolveServerAddrs(
	ctx context.Context,
	address string,
	bypass bool,
	defaultPort string,
	underlying string,
) ([]string, string, error) {
	address = normalizeHostPort(address)

	host, port, err := net.SplitHostPort(address)
	if err != nil {
		host = address
		port = defaultPort
	}

	if net.ParseIP(host) != nil {
		return []string{net.JoinHostPort(host, port)}, host, nil
	}

	resolver := newUnderlyingResolver(bypass, underlying)
	ips, err := resolver.LookupIP(ctx, "ip", host)
	if err != nil {
		shared.LogError("DNS", "Failed to resolve upstream %s (bypass=%t): %v", host, bypass, err)
		return nil, "", err
	}

	var v4, v6 []string
	for _, ip := range ips {
		addr := net.JoinHostPort(ip.String(), port)
		if ip.To4() != nil {
			v4 = append(v4, addr)
		} else {
			v6 = append(v6, addr)
		}
	}

	return append(v4, v6...), host, nil
}

func (t PlainTransport) Query(ctx context.Context, msg *dns.Msg) (*dns.Msg, error) {
	for _, server := range t.Servers {
		m, _, err := t.Client.ExchangeContext(ctx, msg, server)
		if err == nil && m != nil && m.Rcode == dns.RcodeSuccess {
			return m, nil
		}
		if err != nil {
			shared.LogDebug("DNS", "Plain DNS query to %s failed: %v", server, err)
		}
	}
	return nil, fmt.Errorf("all DNS servers failed")
}

func (t DoTTransport) Query(ctx context.Context, msg *dns.Msg) (*dns.Msg, error) {
	for _, server := range t.Servers {
		m, _, err := t.Client.ExchangeContext(ctx, msg, server)
		if err == nil && m != nil && m.Rcode == dns.RcodeSuccess {
			return m, nil
		}
		if err != nil {
			shared.LogDebug("DNS", "DoT Exchange to %s failed: %v", server, err)
		}
	}
	return nil, fmt.Errorf("all DoT servers failed")
}

func (t DoHTransport) Query(ctx context.Context, msg *dns.Msg) (*dns.Msg, error) {
	wire, err := msg.Pack()
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(
		ctx, "POST", t.URL, bytes.NewReader(wire),
	)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")
	req.Host = t.Hostname // important for virtual hosting and cert validation

	resp, err := t.Client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("doh status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var res dns.Msg
	if err := res.Unpack(body); err != nil {
		return nil, err
	}
	return &res, nil
}

func parseDNSAnswers(msg *dns.Msg, qtype uint16) []netip.Addr {
	var out []netip.Addr
	for _, ans := range msg.Answer {
		switch qtype {
		case dns.TypeA:
			if a, ok := ans.(*dns.A); ok {
				if ip, err := netip.ParseAddr(a.A.String()); err == nil {
					out = append(out, ip)
				}
			}
		case dns.TypeAAAA:
			if aaaa, ok := ans.(*dns.AAAA); ok {
				if ip, err := netip.ParseAddr(aaaa.AAAA.String()); err == nil {
					out = append(out, ip)
				}
			}
		}
	}
	return out
}

func Resolve(
	ctx context.Context,
	host, protocol, resolvedUpstream, originalUpstream string,
	bypass bool,
) ([]netip.Addr, []netip.Addr, error) {

	t, err := buildTransport(protocol, resolvedUpstream, originalUpstream, bypass)
	if err != nil {
		return nil, nil, err
	}
	return resolveHost(ctx, t, host)
}

func buildTransport(
	protocol, resolvedUpstream, originalUpstream string,
	bypass bool,
) (Transport, error) {

	switch protocol {
	case "doh":
		// Parse original for SNI
		origURL, err := url.Parse(originalUpstream)
		if err != nil {
			return nil, fmt.Errorf("invalid original DoH upstream: %w", err)
		}

		originalHost := origURL.Hostname()

		// Parse resolved to get the IP
		resolvedURL, _ := url.Parse(resolvedUpstream)
		dialHost := resolvedURL.Hostname()
		if dialHost == "" {
			dialHost = originalHost // fallback
		}

		port := origURL.Port()
		if port == "" {
			port = "443"
		}

		dialer := GetDialer(bypass)

		transport := &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return dialer.DialContext(ctx, network, net.JoinHostPort(dialHost, port))
			},
			TLSClientConfig: &tls.Config{
				ServerName: originalHost, // Use original hostname for certificate validation
			},
		}

		finalURL := origURL.String()
		if !strings.HasPrefix(finalURL, "https://") {
			finalURL = "https://" + finalURL
		}

		return DoHTransport{
			Client:   &http.Client{Timeout: 5 * time.Second, Transport: transport},
			URL:      finalURL,
			Hostname: originalHost,
		}, nil

	case "dot":
		// Get SNI from original
		origHost, origPort, err := net.SplitHostPort(originalUpstream)
		if err != nil {
			origHost = originalUpstream
			origPort = "853"
		}

		// Get connection target from resolved
		resolvedHost, resolvedPort, _ := net.SplitHostPort(resolvedUpstream)
		if resolvedHost == "" {
			resolvedHost = resolvedUpstream
			resolvedPort = origPort
		}

		client := &dns.Client{
			Net:     "tcp-tls",
			Dialer:  GetDialer(bypass),
			Timeout: 6 * time.Second,
			TLSConfig: &tls.Config{
				ServerName: origHost,
				MinVersion: tls.VersionTLS12,
			},
		}

		return DoTTransport{
			Client:  client,
			Servers: []string{net.JoinHostPort(resolvedHost, resolvedPort)},
		}, nil

	default: // plain
		host, port, _ := net.SplitHostPort(resolvedUpstream)
		if host == "" {
			host = resolvedUpstream
			port = "53"
		}

		client := &dns.Client{
			Net:     "udp",
			Dialer:  GetDialer(bypass),
			Timeout: 5 * time.Second,
		}
		return PlainTransport{
			Client:  client,
			Servers: []string{net.JoinHostPort(host, port)},
		}, nil
	}
}

// normalizeHostPort makes sure raw IPv6 is correctly bracketed.
func normalizeHostPort(s string) string {
	s = strings.TrimSpace(s)
	if s == "" || strings.Contains(s, "://") || strings.Contains(s, "]") {
		return s
	}
	if strings.Count(s, ":") < 2 {
		return s // definitely not IPv6
	}

	lastColon := strings.LastIndexByte(s, ':')
	potentialHost := s[:lastColon]
	potentialPort := s[lastColon+1:]

	if ip := net.ParseIP(potentialHost); ip != nil && ip.To4() == nil {
		if potentialPort != "" {
			return "[" + potentialHost + "]:" + potentialPort
		}
		return "[" + potentialHost + "]"
	}

	// fallback with no port
	if ip := net.ParseIP(s); ip != nil && ip.To4() == nil {
		return "[" + s + "]"
	}

	return s
}

func GetDialer(bypass bool) *net.Dialer {
	if !bypass {
		return &net.Dialer{LocalAddr: nil}
	}
	return &net.Dialer{
		Control: func(network, address string, c syscall.RawConn) error {
			var opErr error
			err := c.Control(func(fd uintptr) {
				if C.bypass_socket(C.int(fd)) == 0 {
					opErr = unix.EACCES
					shared.LogError("DNS", "Failed to bypass socket FD: %d", fd)
				} else {
					shared.LogDebug("DNS", "Bypassed DNS socket FD: %d", fd)
				}
			})
			if err != nil {
				return err
			}
			return opErr
		},
	}
}
