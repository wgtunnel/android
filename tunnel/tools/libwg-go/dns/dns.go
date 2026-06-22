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
	upstream *C.char,
	underlyingDnsServers *C.char,
	bypass C.int,
) *C.char {
	h := C.GoString(host)
	p := C.GoString(protocol)
	u := C.GoString(upstream)
	underlying := C.GoString(underlyingDnsServers)
	bp := bypass == 1

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	shared.LogDebug(
		"DNS",
		"ResolveBootstrap called host=%s protocol=%s upstream=%s bypass=%t",
		h, p, u, bp,
	)

	v4, v6, err := Resolve(ctx, h, p, u, bp, underlying)
	if err != nil {
		shared.LogError("DNS", "ResolveBootstrap failed for %s: %v", h, err)
		return C.CString("ERR|" + err.Error())
	}

	v4Str := make([]string, len(v4))
	for i, ip := range v4 {
		v4Str[i] = ip.String()
	}
	v6Str := make([]string, len(v6))
	for i, ip := range v6 {
		v6Str[i] = ip.String()
	}

	result := "v4=" + strings.Join(v4Str, ",") +
		";v6=" + strings.Join(v6Str, ",")

	shared.LogDebug("DNS", "ResolveBootstrap success for %s: %s", h, result)
	return C.CString(result)
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
		m, _, err := t.Client.Exchange(msg, server)
		if err == nil && m != nil && m.Rcode == dns.RcodeSuccess {
			return m, nil
		}
	}
	return nil, fmt.Errorf("all DNS servers failed")
}

func (t DoTTransport) Query(ctx context.Context, msg *dns.Msg) (*dns.Msg, error) {
	for _, server := range t.Servers {
		m, _, err := t.Client.Exchange(msg, server)
		if err == nil && m != nil && m.Rcode == dns.RcodeSuccess {
			return m, nil
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
	host, protocol, upstream string,
	bypass bool,
	underlying string,
) ([]netip.Addr, []netip.Addr, error) {
	t, err := buildTransport(ctx, protocol, upstream, bypass, underlying)
	if err != nil {
		return nil, nil, err
	}
	return resolveHost(ctx, t, host)
}

func buildTransport(
	ctx context.Context,
	protocol, upstream string,
	bypass bool,
	underlying string,
) (Transport, error) {
	switch protocol {
	case "doh":
		u, err := url.Parse(upstream)
		if err != nil {
			return nil, err
		}
		hostname := u.Hostname()
		port := u.Port()
		if port == "" {
			port = "443"
		}
		u.Host = net.JoinHostPort(hostname, port)

		// Pre-resolve with IPv4-first ordering + bypass
		servers, _, err := resolveServerAddrs(ctx, u.Host, bypass, "443", underlying)
		if err != nil {
			return nil, err
		}
		if len(servers) == 0 {
			return nil, fmt.Errorf("no addresses resolved for DoH server")
		}

		// Custom dialer that tries servers in order
		// tries ipv4 first and then ipv6
		dialer := GetDialer(bypass)
		transport := &http.Transport{
			DialContext: func(ctx context.Context, network, _ string) (net.Conn, error) {
				for _, addr := range servers {
					conn, err := dialer.DialContext(ctx, network, addr)
					if err == nil {
						return conn, nil
					}
				}
				return nil, fmt.Errorf("all DoH addresses failed")
			},
			TLSClientConfig: &tls.Config{
				ServerName: hostname,
			},
		}

		return DoHTransport{
			Client:   &http.Client{Timeout: 5 * time.Second, Transport: transport},
			URL:      u.String(),
			Servers:  servers,
			Hostname: hostname,
		}, nil

	case "dot":
		servers, sni, err := resolveServerAddrs(ctx, upstream, bypass, "853", underlying)
		if err != nil {
			return nil, err
		}
		if len(servers) == 0 {
			return nil, fmt.Errorf("no addresses resolved for DoT server")
		}

		client := &dns.Client{
			Net:     "tcp-tls",
			Dialer:  GetDialer(bypass),
			Timeout: 5 * time.Second,
			TLSConfig: &tls.Config{
				ServerName: sni,
			},
		}
		return DoTTransport{
			Client:  client,
			Servers: servers,
		}, nil

	default: // plain DNS
		_, addr, err := parseUpstream(upstream)
		if err != nil {
			return nil, err
		}
		servers, _, err := resolveServerAddrs(ctx, addr, bypass, "53", underlying)
		if err != nil {
			return nil, err
		}

		client := &dns.Client{
			Net:     "udp",
			Dialer:  GetDialer(bypass),
			Timeout: 5 * time.Second,
		}
		return PlainTransport{
			Client:  client,
			Servers: servers,
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
