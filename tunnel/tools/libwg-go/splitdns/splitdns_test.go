// SPDX-License-Identifier: Apache-2.0

package splitdns

import (
	"context"
	"encoding/binary"
	"net"
	"net/netip"
	"sync"
	"testing"

	"github.com/miekg/dns"
)

func TestMatcherSuffix(t *testing.T) {
	m := NewMatcher([]string{"example.com", "*.internal.corp", ".foo.net", "EXAMPLE.ORG."})

	cases := map[string]bool{
		"example.com":          true,
		"mail.example.com":     true,
		"a.b.example.com":      true,
		"notexample.com":       false, // not a label-boundary suffix
		"example.com.evil.com": false,
		"x.internal.corp":      true,
		"internal.corp":        true,
		"foo.net":              true,
		"www.foo.net":          true,
		"example.org":          true,
		"sub.example.org":      true,
		"google.com":           false,
	}

	for name, want := range cases {
		if got := m.Matches(name); got != want {
			t.Errorf("Matches(%q) = %v, want %v", name, got, want)
		}
		// FQDN form (trailing dot) must behave identically.
		if got := m.Matches(name + "."); got != want {
			t.Errorf("Matches(%q.) = %v, want %v", name, got, want)
		}
	}
}

func TestEmptyMatcherMatchesNothing(t *testing.T) {
	m := NewMatcher(nil)
	if !m.Empty() {
		t.Fatal("expected empty matcher")
	}
	if m.Matches("example.com") {
		t.Error("empty matcher should not match")
	}
}

// buildQuery assembles an IPv4/IPv6 UDP/53 query packet for a name.
func buildQuery(t *testing.T, isV6 bool, name string) ([]byte, []byte) {
	t.Helper()
	var msg dns.Msg
	msg.SetQuestion(dns.Fqdn(name), dns.TypeA)
	payload, err := msg.Pack()
	if err != nil {
		t.Fatalf("pack: %v", err)
	}

	udpLen := udpHeader + len(payload)
	var src, dst netip.Addr
	var pkt []byte
	if isV6 {
		src = netip.MustParseAddr("fd00::2")
		dst = netip.MustParseAddr("fd00::1")
		pkt = make([]byte, ipv6Header+udpLen)
		pkt[0] = 0x60
		binary.BigEndian.PutUint16(pkt[4:6], uint16(udpLen))
		pkt[6] = protoUDP
		pkt[7] = 64
		copy(pkt[8:24], src.AsSlice())
		copy(pkt[24:40], dst.AsSlice())
		seg := pkt[ipv6Header:]
		binary.BigEndian.PutUint16(seg[0:2], 12345) // src port
		binary.BigEndian.PutUint16(seg[2:4], dnsPort)
		binary.BigEndian.PutUint16(seg[4:6], uint16(udpLen))
		copy(seg[udpHeader:], payload)
		binary.BigEndian.PutUint16(seg[6:8], udpChecksum(src, dst, seg[:udpLen], true))
	} else {
		src = netip.MustParseAddr("10.0.0.2")
		dst = netip.MustParseAddr("10.0.0.1")
		pkt = make([]byte, ipv4MinHeader+udpLen)
		pkt[0] = 0x45
		binary.BigEndian.PutUint16(pkt[2:4], uint16(len(pkt)))
		pkt[8] = 64
		pkt[9] = protoUDP
		copy(pkt[12:16], src.AsSlice())
		copy(pkt[16:20], dst.AsSlice())
		binary.BigEndian.PutUint16(pkt[10:12], checksum(pkt[:ipv4MinHeader]))
		seg := pkt[ipv4MinHeader:]
		binary.BigEndian.PutUint16(seg[0:2], 12345)
		binary.BigEndian.PutUint16(seg[2:4], dnsPort)
		binary.BigEndian.PutUint16(seg[4:6], uint16(udpLen))
		copy(seg[udpHeader:], payload)
		binary.BigEndian.PutUint16(seg[6:8], udpChecksum(src, dst, seg[:udpLen], false))
	}
	return pkt, payload
}

func TestParseAndQuestionName(t *testing.T) {
	for _, isV6 := range []bool{false, true} {
		pkt, payload := buildQuery(t, isV6, "mail.example.com")
		q, err := parseDNSQuery(pkt)
		if err != nil {
			t.Fatalf("isV6=%v parse: %v", isV6, err)
		}
		if q.isV6 != isV6 {
			t.Errorf("isV6=%v: got %v", isV6, q.isV6)
		}
		if q.dstPort != dnsPort {
			t.Errorf("dstPort = %d", q.dstPort)
		}
		if string(q.payload) != string(payload) {
			t.Errorf("payload mismatch")
		}
		name, ok := questionName(q.payload)
		if !ok || name != "mail.example.com." {
			t.Errorf("questionName = %q, ok=%v", name, ok)
		}
	}
}

func TestParseRejectsNonDNS(t *testing.T) {
	// TCP packet (proto 6) must be rejected.
	pkt := make([]byte, ipv4MinHeader+8)
	pkt[0] = 0x45
	pkt[9] = 6
	if _, err := parseDNSQuery(pkt); err == nil {
		t.Error("expected non-DNS packet to be rejected")
	}
	// Non port-53 UDP must be rejected.
	pkt2, _ := buildQuery(t, false, "x.com")
	binary.BigEndian.PutUint16(pkt2[ipv4MinHeader+2:ipv4MinHeader+4], 443)
	if _, err := parseDNSQuery(pkt2); err == nil {
		t.Error("expected non-53 UDP to be rejected")
	}
}

// verifyChecksums recomputes header/UDP checksums of a built packet and ensures
// they validate to zero (the standard correctness check).
func TestBuildResponseChecksums(t *testing.T) {
	for _, isV6 := range []bool{false, true} {
		pkt, _ := buildQuery(t, isV6, "example.com")
		q, err := parseDNSQuery(pkt)
		if err != nil {
			t.Fatal(err)
		}

		var ans dns.Msg
		ans.SetQuestion(dns.Fqdn("example.com"), dns.TypeA)
		ans.Response = true
		ans.Answer = []dns.RR{&dns.A{
			Hdr: dns.RR_Header{Name: dns.Fqdn("example.com"), Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60},
			A:   netip.MustParseAddr("93.184.216.34").AsSlice(),
		}}
		reply, _ := ans.Pack()

		resp := buildResponse(q, reply)
		rq, err := parseDNSQuery(resp)
		// parseDNSQuery requires dst port 53; the response has dst port = original
		// src (12345), so parse it manually for verification instead.
		_ = rq
		_ = err

		if isV6 {
			seg := resp[ipv6Header:]
			src := netip.MustParseAddr("fd00::1") // reply src = original dst
			dst := netip.MustParseAddr("fd00::2")
			if got := udpChecksum(src, dst, seg, true); got != 0 && got != 0xffff {
				t.Errorf("v6 udp checksum validation = %d, want 0/0xffff", got)
			}
			// reply src port must be 53, dst port the original 12345
			if binary.BigEndian.Uint16(seg[0:2]) != dnsPort {
				t.Errorf("v6 reply src port = %d", binary.BigEndian.Uint16(seg[0:2]))
			}
		} else {
			if got := checksum(resp[:ipv4MinHeader]); got != 0 {
				t.Errorf("v4 ip checksum validation = %d, want 0", got)
			}
			seg := resp[ipv4MinHeader:]
			src := netip.MustParseAddr("10.0.0.1")
			dst := netip.MustParseAddr("10.0.0.2")
			if got := udpChecksum(src, dst, seg, false); got != 0 && got != 0xffff {
				t.Errorf("v4 udp checksum validation = %d, want 0/0xffff", got)
			}
			if binary.BigEndian.Uint16(seg[0:2]) != dnsPort {
				t.Errorf("v4 reply src port = %d", binary.BigEndian.Uint16(seg[0:2]))
			}
		}
	}
}

func TestBuildServfail(t *testing.T) {
	var q dns.Msg
	q.SetQuestion("example.com.", dns.TypeA)
	q.Id = 0xbeef
	query, err := q.Pack()
	if err != nil {
		t.Fatalf("pack: %v", err)
	}

	out, ok := buildServfail(query)
	if !ok {
		t.Fatal("buildServfail failed on valid query")
	}

	var res dns.Msg
	if err := res.Unpack(out); err != nil {
		t.Fatalf("unpack response: %v", err)
	}
	if res.Id != q.Id {
		t.Errorf("response id = %#x, want %#x", res.Id, q.Id)
	}
	if res.Rcode != dns.RcodeServerFailure {
		t.Errorf("rcode = %d, want SERVFAIL", res.Rcode)
	}
	if !res.Response {
		t.Error("QR bit not set")
	}
	if len(res.Question) != 1 || res.Question[0].Name != "example.com." {
		t.Errorf("question not echoed: %v", res.Question)
	}

	if _, ok := buildServfail([]byte{0x01, 0x02}); ok {
		t.Error("buildServfail should fail on garbage input")
	}
}

func TestNormalizeServers(t *testing.T) {
	got := normalizeServers([]string{"10.0.0.1", "1.1.1.1:5353", "fd00::9", "[fd00::9]:53"})
	want := []string{"10.0.0.1:53", "1.1.1.1:5353", "[fd00::9]:53", "[fd00::9]:53"}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("normalizeServers[%d] = %q, want %q", i, got[i], want[i])
		}
	}
}

// recordingDialer fails every dial but records the addresses tried.
type recordingDialer struct {
	mu    sync.Mutex
	addrs []string
}

func (r *recordingDialer) DialContext(_ context.Context, _, address string) (net.Conn, error) {
	r.mu.Lock()
	r.addrs = append(r.addrs, address)
	r.mu.Unlock()
	return nil, net.ErrClosed
}

func TestSetServersTakesEffect(t *testing.T) {
	dialer := &recordingDialer{}
	d := NewDevice(nil, NewMatcher([]string{"internal.corp"}), []string{"10.0.0.1"}, dialer)
	defer d.cancel()

	if _, err := d.resolve([]byte{0}); err == nil {
		t.Fatal("expected resolve to fail with failing dialer")
	}
	d.SetServers([]string{"192.168.1.1", "1.1.1.1"})
	if _, err := d.resolve([]byte{0}); err == nil {
		t.Fatal("expected resolve to fail with failing dialer")
	}

	want := []string{"10.0.0.1:53", "192.168.1.1:53", "1.1.1.1:53"}
	dialer.mu.Lock()
	defer dialer.mu.Unlock()
	if len(dialer.addrs) != len(want) {
		t.Fatalf("dialed %v, want %v", dialer.addrs, want)
	}
	for i := range want {
		if dialer.addrs[i] != want[i] {
			t.Errorf("dial[%d] = %q, want %q", i, dialer.addrs[i], want[i])
		}
	}
}
