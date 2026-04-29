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
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/miekg/dns"
	"github.com/wgtunnel/android/shared"
	"golang.org/x/sys/unix"
)

const defaultPlain = "udp://1.1.1.1:53"

var (
	currentConfig DNSConfig = DNSConfig{
		"plain",
		"1.1.1.1:53",
	}
	configMu sync.RWMutex
)

type DNSConfig struct {
	Protocol string `json:"protocol"` // plain, doh, or dot
	Upstream string `json:"upstream"`
}

type Resolved struct {
	V4 []netip.Addr
	V6 []netip.Addr
}

type ResolverOptions struct {
	UpstreamURL string
	Timeout     time.Duration
}

func DefaultOptions() ResolverOptions {
	return ResolverOptions{
		UpstreamURL: defaultPlain,
		Timeout:     5 * time.Second,
	}
}

//export SetDNSConfig
func SetDNSConfig(config string) {
	var cfg DNSConfig
	if err := json.Unmarshal([]byte(config), &cfg); err != nil {
		shared.LogError("DNS", "Failed to parse DNSConfig: %v", err)
		return
	}
	if cfg.Protocol != "plain" && cfg.Protocol != "doh" && cfg.Protocol != "dot" {
		cfg.Protocol = "plain"
	}
	configMu.Lock()
	currentConfig = cfg
	configMu.Unlock()
	shared.LogDebug("DNS", "DNS config updated: %s %s", cfg.Protocol, cfg.Upstream)
}

//export ResolveBootstrap
func ResolveBootstrap(host *C.char, bypass C.int) *C.char {
	h := C.GoString(host)
	bp := bypass == 1
	shared.LogDebug("DNS", "ResolveBootstrap called for host=%s (bypass=%t)", h, bp)

	v4, v6, err := Resolve(h, bp)
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

	result := "v4=" + strings.Join(v4Str, ",") + ";v6=" + strings.Join(v6Str, ",")
	shared.LogDebug("DNS", "ResolveBootstrap success for %s: %s", h, result)
	return C.CString(result)
}

func getConfig() DNSConfig {
	configMu.RLock()
	defer configMu.RUnlock()
	return currentConfig
}

func parseUpstream(upstreamURL string) (network, address string, err error) {
	shared.LogDebug("DNS", "Parsing upstream URL: %s", upstreamURL)
	u := upstreamURL
	if !strings.Contains(u, "://") {
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

func resolveServerAddr(ctx context.Context, address string, bypass bool) (string, error) {
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		shared.LogError("DNS", "resolveServerAddr: invalid address %q: %v", address, err)
		return "", err
	}

	if net.ParseIP(host) != nil {
		return address, nil
	}

	shared.LogDebug("DNS", "resolveServerAddr: bootstrapping upstream hostname %s (bypass=%t)", host, bypass)

	bootstrapDialer := GetDialer(bypass)
	resolver := &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			return bootstrapDialer.DialContext(ctx, network, "1.1.1.1:53")
		},
	}

	ips, err := resolver.LookupIP(ctx, "ip", host)
	if err != nil {
		shared.LogError("DNS", "Failed to resolve upstream hostname %s (bypass=%t): %v", host, bypass, err)
		return "", fmt.Errorf("failed to resolve upstream hostname %s: %w", host, err)
	}
	if len(ips) == 0 {
		err = errors.New("no IPs found for upstream hostname")
		shared.LogError("DNS", "%v for %s", err, host)
		return "", err
	}

	addr := net.JoinHostPort(ips[0].String(), port)
	shared.LogDebug("DNS", "Resolved upstream %s -> %s", host, addr)
	return addr, nil
}
func resolveInner(host string, ipType uint16, network, serverAddr string, bypass bool) ([]netip.Addr, error) {
	req := &dns.Msg{}
	req.Id = dns.Id()
	req.RecursionDesired = true
	req.SetQuestion(dns.Fqdn(host), ipType)
	req.SetEdns0(4096, true)

	client := &dns.Client{
		Net:     network,
		Dialer:  GetDialer(bypass),
		Timeout: 5 * time.Second,
		UDPSize: 4096,
	}

	res, _, err := client.Exchange(req, serverAddr)
	if err != nil {
		shared.LogError("DNS", "resolveInner: DNS exchange failed for %s (type=%d, server=%s, bypass=%t): %v", host, ipType, serverAddr, bypass, err)
		return nil, err
	}
	if res.Rcode != dns.RcodeSuccess {
		shared.LogError("DNS", "resolveInner: DNS query failed with Rcode %d for %s", res.Rcode, host)
		return nil, fmt.Errorf("DNS query failed with Rcode: %d", res.Rcode)
	}

	var addr []netip.Addr
	for _, ans := range res.Answer {
		switch ipType {
		case dns.TypeA:
			if a, ok := ans.(*dns.A); ok {
				if ip, err := netip.ParseAddr(a.A.String()); err == nil {
					addr = append(addr, ip)
				}
			}
		case dns.TypeAAAA:
			if aaaa, ok := ans.(*dns.AAAA); ok {
				if ip, err := netip.ParseAddr(aaaa.AAAA.String()); err == nil {
					addr = append(addr, ip)
				}
			}
		}
	}
	return addr, nil
}

func resolvePlain(host, upstreamURL string, bypass bool) ([]netip.Addr, []netip.Addr, error) {
	shared.LogDebug("DNS", "resolvePlain: %s with upstream=%s (bypass=%t)", host, upstreamURL, bypass)
	network, addr, err := parseUpstream(upstreamURL)
	if err != nil {
		return nil, nil, err
	}

	serverAddr, err := resolveServerAddr(context.Background(), addr, bypass)
	if err != nil {
		return nil, nil, err
	}

	var wg sync.WaitGroup
	var v4, v6 []netip.Addr
	var v4Err, v6Err error
	wg.Add(2)
	go func() { v4, v4Err = resolveInner(host, dns.TypeA, network, serverAddr, bypass); wg.Done() }()
	go func() { v6, v6Err = resolveInner(host, dns.TypeAAAA, network, serverAddr, bypass); wg.Done() }()
	wg.Wait()

	if v4Err != nil && v6Err != nil {
		shared.LogError("DNS", "resolvePlain failed for %s: both A and AAAA failed", host)
		return nil, nil, errors.Join(v4Err, v6Err)
	}
	if len(v4) == 0 && len(v6) == 0 {
		err = errors.New("no IP addresses found")
		shared.LogError("DNS", "%v for %s", err, host)
		return nil, nil, err
	}
	return v4, v6, nil
}

func resolveDoH(host, dohURL string, bypass bool) ([]netip.Addr, []netip.Addr, error) {
	shared.LogDebug("DNS", "Resolving DOH: %s with %s", host, dohURL)
	var v4, v6 []netip.Addr
	var v4Err, v6Err error

	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		v4, v4Err = doDoHQuery(dohURL, host, dns.TypeA, bypass)
	}()
	go func() {
		defer wg.Done()
		v6, v6Err = doDoHQuery(dohURL, host, dns.TypeAAAA, bypass)
	}()
	wg.Wait()

	if v4Err != nil && v6Err != nil {
		shared.LogError("DNS", "resolveDoH failed for %s: both queries failed", host)
		return nil, nil, errors.Join(v4Err, v6Err)
	}
	return v4, v6, nil
}

func doDoHQuery(dohURL, host string, qtype uint16, bypass bool) ([]netip.Addr, error) {
	req := &dns.Msg{}
	req.Id = dns.Id()
	req.RecursionDesired = true
	req.SetEdns0(4096, true)
	req.SetQuestion(dns.Fqdn(host), qtype)

	transport := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			h, port, _ := net.SplitHostPort(addr)
			if net.ParseIP(h) == nil {
				ips, err := CustomResolver(bypass).LookupIP(ctx, "ip", h)
				if err == nil && len(ips) > 0 {
					h = ips[0].String()
				}
			}
			return GetDialer(bypass).DialContext(ctx, network, net.JoinHostPort(h, port))
		},
	}

	client := &http.Client{
		Transport: transport,
		Timeout:   5 * time.Second,
	}

	wire, err := req.Pack()
	if err != nil {
		return nil, err
	}

	httpReq, err := http.NewRequestWithContext(context.Background(), "POST", dohURL, bytes.NewReader(wire))
	if err != nil {
		return nil, err
	}
	httpReq.Header.Set("Content-Type", "application/dns-message")
	httpReq.Header.Set("Accept", "application/dns-message")

	resp, err := client.Do(httpReq)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		shared.LogError("DNS", "doDoHQuery: DoH server returned HTTP %d for %s", resp.StatusCode, host)
		return nil, fmt.Errorf("DoH HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var res dns.Msg
	if err := res.Unpack(body); err != nil {
		return nil, err
	}
	if res.Rcode != dns.RcodeSuccess {
		shared.LogError("DNS", "doDoHQuery: DoH Rcode %d for %s", res.Rcode, host)
		return nil, fmt.Errorf("DoH Rcode %d", res.Rcode)
	}

	var addrs []netip.Addr
	for _, ans := range res.Answer {
		if qtype == dns.TypeA {
			if a, ok := ans.(*dns.A); ok {
				if ip, _ := netip.ParseAddr(a.A.String()); ip.Is4() {
					addrs = append(addrs, ip)
				}
			}
		} else if qtype == dns.TypeAAAA {
			if aaaa, ok := ans.(*dns.AAAA); ok {
				if ip, _ := netip.ParseAddr(aaaa.AAAA.String()); ip.Is6() {
					addrs = append(addrs, ip)
				}
			}
		}
	}
	return addrs, nil
}

func resolveDoT(host, dotUpstream string, bypass bool) ([]netip.Addr, []netip.Addr, error) {
	shared.LogDebug("DNS", "resolveDoT: %s with upstream=%s (bypass=%t)", host, dotUpstream, bypass)

	var v4, v6 []netip.Addr
	var v4Err, v6Err error

	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		v4, v4Err = doDoTQuery(dotUpstream, host, dns.TypeA, bypass)
	}()
	go func() {
		defer wg.Done()
		v6, v6Err = doDoTQuery(dotUpstream, host, dns.TypeAAAA, bypass)
	}()
	wg.Wait()

	if v4Err != nil && v6Err != nil {
		shared.LogError("DNS", "resolveDoT failed for %s: both A and AAAA queries failed (bypass=%t)", host, bypass)
		return nil, nil, errors.Join(v4Err, v6Err)
	}

	shared.LogDebug("DNS", "resolveDoT success for %s: %d v4, %d v6 (bypass=%t)", host, len(v4), len(v6), bypass)
	return v4, v6, nil
}

func doDoTQuery(dotUpstream, host string, qtype uint16, bypass bool) ([]netip.Addr, error) {
	// Normalize upstream to host:port
	sni, port, err := net.SplitHostPort(dotUpstream)
	if err != nil {
		sni = dotUpstream
		port = "853"
		dotUpstream = net.JoinHostPort(sni, port)
	}

	// Resolve hostname using bypass resolver
	serverAddr, err := resolveServerAddr(context.Background(), dotUpstream, bypass)
	if err != nil {
		return nil, err
	}

	req := &dns.Msg{}
	req.Id = dns.Id()
	req.RecursionDesired = true
	req.SetEdns0(4096, true)
	req.SetQuestion(dns.Fqdn(host), qtype)

	client := &dns.Client{
		Net:     "tcp-tls",
		Dialer:  GetDialer(bypass),
		Timeout: 5 * time.Second,
		TLSConfig: &tls.Config{
			ServerName:         sni,
			InsecureSkipVerify: false,
		},
	}

	res, _, err := client.Exchange(req, serverAddr)
	if err != nil {
		return nil, err
	}
	if res.Rcode != dns.RcodeSuccess {
		return nil, fmt.Errorf("DoT query failed with Rcode: %d", res.Rcode)
	}

	var addrs []netip.Addr
	for _, ans := range res.Answer {
		switch qtype {
		case dns.TypeA:
			if a, ok := ans.(*dns.A); ok {
				if ip, _ := netip.ParseAddr(a.A.String()); ip.Is4() {
					addrs = append(addrs, ip)
				}
			}
		case dns.TypeAAAA:
			if aaaa, ok := ans.(*dns.AAAA); ok {
				if ip, _ := netip.ParseAddr(aaaa.AAAA.String()); ip.Is6() {
					addrs = append(addrs, ip)
				}
			}
		}
	}
	return addrs, nil
}

// Resolve runs the correct protocol based on the global config
func Resolve(host string, bypass bool) ([]netip.Addr, []netip.Addr, error) {
	cfg := getConfig()
	shared.LogDebug("DNS", "Resolve(%s, bypass=%t) protocol=%s upstream=%s", host, bypass, cfg.Protocol, cfg.Upstream)

	var v4, v6 []netip.Addr
	var err error
	switch cfg.Protocol {
	case "doh":
		v4, v6, err = resolveDoH(host, cfg.Upstream, bypass)
	case "dot":
		v4, v6, err = resolveDoT(host, cfg.Upstream, bypass)
	default:
		v4, v6, err = resolvePlain(host, cfg.Upstream, bypass)
	}

	if err != nil {
		shared.LogError("DNS", "Final Resolve failed for %s: %v", host, err)
	} else {
		shared.LogDebug("DNS", "Resolve success for %s: %d v4, %d v6", host, len(v4), len(v6))
	}
	return v4, v6, err
}

func CustomResolver(bypass bool) *net.Resolver {
	return &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			d := GetDialer(bypass)
			return d.DialContext(ctx, network, address)
		},
	}
}

func GetDialer(bypass bool) *net.Dialer {
	if !bypass {
		return &net.Dialer{
			LocalAddr: nil,
		}
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
