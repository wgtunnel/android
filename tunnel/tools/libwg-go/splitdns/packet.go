// SPDX-License-Identifier: Apache-2.0
//
// Minimal IPv4/IPv6 + UDP parsing and DNS response synthesis used by the
// split-tunnel DNS interceptor. We hand-roll this rather than pulling in a full
// netstack because we only ever deal with single, unfragmented UDP/53 datagrams.

package splitdns

import (
	"encoding/binary"
	"errors"
	"net/netip"
)

const (
	protoUDP = 17

	ipv4MinHeader = 20
	ipv6Header    = 40
	udpHeader     = 8

	dnsPort = 53
)

var errNotDNSQuery = errors.New("not a udp/53 datagram")

// udpDatagram describes a parsed UDP/53 packet.
type udpDatagram struct {
	isV6    bool
	srcAddr netip.Addr
	dstAddr netip.Addr
	srcPort uint16
	dstPort uint16
	payload []byte // DNS message bytes (slice into the original buffer)
}

// parseDNSQuery parses an IP packet and returns the contained UDP/53 datagram.
// It returns errNotDNSQuery for anything that is not an unfragmented UDP packet
// destined for port 53.
func parseDNSQuery(pkt []byte) (*udpDatagram, error) {
	if len(pkt) < 1 {
		return nil, errNotDNSQuery
	}
	switch pkt[0] >> 4 {
	case 4:
		return parseV4(pkt)
	case 6:
		return parseV6(pkt)
	default:
		return nil, errNotDNSQuery
	}
}

func parseV4(pkt []byte) (*udpDatagram, error) {
	if len(pkt) < ipv4MinHeader {
		return nil, errNotDNSQuery
	}
	ihl := int(pkt[0]&0x0f) * 4
	if ihl < ipv4MinHeader || len(pkt) < ihl {
		return nil, errNotDNSQuery
	}
	if pkt[9] != protoUDP {
		return nil, errNotDNSQuery
	}
	// Fragmented packets (offset != 0 or MF set) are ignored.
	fragField := binary.BigEndian.Uint16(pkt[6:8])
	if fragField&0x1fff != 0 || fragField&0x2000 != 0 {
		return nil, errNotDNSQuery
	}
	src, _ := netip.AddrFromSlice(pkt[12:16])
	dst, _ := netip.AddrFromSlice(pkt[16:20])
	return parseUDP(pkt[ihl:], false, src, dst)
}

func parseV6(pkt []byte) (*udpDatagram, error) {
	if len(pkt) < ipv6Header {
		return nil, errNotDNSQuery
	}
	// Only handle packets where UDP is the first (and only) header.
	if pkt[6] != protoUDP {
		return nil, errNotDNSQuery
	}
	src, _ := netip.AddrFromSlice(pkt[8:24])
	dst, _ := netip.AddrFromSlice(pkt[24:40])
	return parseUDP(pkt[ipv6Header:], true, src, dst)
}

func parseUDP(seg []byte, isV6 bool, src, dst netip.Addr) (*udpDatagram, error) {
	if len(seg) < udpHeader {
		return nil, errNotDNSQuery
	}
	dstPort := binary.BigEndian.Uint16(seg[2:4])
	if dstPort != dnsPort {
		return nil, errNotDNSQuery
	}
	length := int(binary.BigEndian.Uint16(seg[4:6]))
	if length < udpHeader || length > len(seg) {
		length = len(seg)
	}
	return &udpDatagram{
		isV6:    isV6,
		srcAddr: src,
		dstAddr: dst,
		srcPort: binary.BigEndian.Uint16(seg[0:2]),
		dstPort: dstPort,
		payload: seg[udpHeader:length],
	}, nil
}

// buildResponse synthesizes an IP+UDP packet carrying dnsPayload, addressed back
// to the original querier. Source/destination are swapped relative to the
// incoming query so the app sees a reply from the DNS server it queried.
func buildResponse(q *udpDatagram, dnsPayload []byte) []byte {
	udpLen := udpHeader + len(dnsPayload)
	if q.isV6 {
		return buildV6(q, dnsPayload, udpLen)
	}
	return buildV4(q, dnsPayload, udpLen)
}

func buildV4(q *udpDatagram, dnsPayload []byte, udpLen int) []byte {
	total := ipv4MinHeader + udpLen
	pkt := make([]byte, total)

	pkt[0] = 0x45 // version 4, IHL 5
	pkt[1] = 0x00 // DSCP/ECN
	binary.BigEndian.PutUint16(pkt[2:4], uint16(total))
	// identification, flags, fragment offset all left zero
	pkt[8] = 64 // TTL
	pkt[9] = protoUDP
	// reply src = original dst, reply dst = original src
	copy(pkt[12:16], q.dstAddr.AsSlice())
	copy(pkt[16:20], q.srcAddr.AsSlice())
	binary.BigEndian.PutUint16(pkt[10:12], checksum(pkt[:ipv4MinHeader]))

	writeUDP(pkt[ipv4MinHeader:], q, dnsPayload, udpLen, false)
	return pkt
}

func buildV6(q *udpDatagram, dnsPayload []byte, udpLen int) []byte {
	total := ipv6Header + udpLen
	pkt := make([]byte, total)

	pkt[0] = 0x60 // version 6
	binary.BigEndian.PutUint16(pkt[4:6], uint16(udpLen))
	pkt[6] = protoUDP // next header
	pkt[7] = 64       // hop limit
	copy(pkt[8:24], q.dstAddr.AsSlice())
	copy(pkt[24:40], q.srcAddr.AsSlice())

	writeUDP(pkt[ipv6Header:], q, dnsPayload, udpLen, true)
	return pkt
}

func writeUDP(seg []byte, q *udpDatagram, dnsPayload []byte, udpLen int, isV6 bool) {
	// reply src port = original dst port (53), reply dst port = original src port
	binary.BigEndian.PutUint16(seg[0:2], q.dstPort)
	binary.BigEndian.PutUint16(seg[2:4], q.srcPort)
	binary.BigEndian.PutUint16(seg[4:6], uint16(udpLen))
	copy(seg[udpHeader:], dnsPayload)

	// reply src addr = original dst addr, reply dst addr = original src addr
	sum := udpChecksum(q.dstAddr, q.srcAddr, seg[:udpLen], isV6)
	binary.BigEndian.PutUint16(seg[6:8], sum)
}

// checksum computes the 16-bit one's complement checksum used by IPv4 headers.
func checksum(b []byte) uint16 {
	var sum uint32
	for i := 0; i+1 < len(b); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(b[i : i+2]))
	}
	if len(b)%2 == 1 {
		sum += uint32(b[len(b)-1]) << 8
	}
	for sum>>16 != 0 {
		sum = (sum & 0xffff) + (sum >> 16)
	}
	return ^uint16(sum)
}

// udpChecksum computes the UDP checksum including the IPv4/IPv6 pseudo-header.
func udpChecksum(src, dst netip.Addr, udpSeg []byte, isV6 bool) uint16 {
	var sum uint32

	srcB := src.AsSlice()
	dstB := dst.AsSlice()
	for i := 0; i+1 < len(srcB); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(srcB[i : i+2]))
	}
	for i := 0; i+1 < len(dstB); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(dstB[i : i+2]))
	}

	if isV6 {
		sum += uint32(len(udpSeg)) // upper-layer length (fits in 16 bits here)
		sum += uint32(protoUDP)
	} else {
		sum += uint32(protoUDP)
		sum += uint32(len(udpSeg))
	}

	for i := 0; i+1 < len(udpSeg); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(udpSeg[i : i+2]))
	}
	if len(udpSeg)%2 == 1 {
		sum += uint32(udpSeg[len(udpSeg)-1]) << 8
	}

	for sum>>16 != 0 {
		sum = (sum & 0xffff) + (sum >> 16)
	}
	csum := ^uint16(sum)
	if csum == 0 {
		csum = 0xffff // a zero checksum is transmitted as all ones
	}
	return csum
}
