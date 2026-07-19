// SPDX-License-Identifier: Apache-2.0
//
// Split-tunnel DNS domain matching.

package splitdns

import "strings"

// Matcher decides whether a queried name should be resolved through the tunnel
// (matched) or via the underlying system resolver (not matched).
//
// Matching is suffix based and case insensitive: an entry "example.com" matches
// "example.com" as well as any subdomain such as "mail.example.com" or
// "a.b.example.com". A leading dot or "*." prefix on an entry is tolerated and
// stripped so "*.example.com" and ".example.com" behave the same as
// "example.com".
type Matcher struct {
	domains []string
}

// NewMatcher normalizes the provided domains into a Matcher. Blank entries are
// ignored.
func NewMatcher(domains []string) *Matcher {
	normalized := make([]string, 0, len(domains))
	for _, d := range domains {
		n := normalizeDomain(d)
		if n != "" {
			normalized = append(normalized, n)
		}
	}
	return &Matcher{domains: normalized}
}

// Empty reports whether there are no domains to match against.
func (m *Matcher) Empty() bool {
	return m == nil || len(m.domains) == 0
}

// Matches reports whether name should be routed through the tunnel.
func (m *Matcher) Matches(name string) bool {
	if m == nil {
		return false
	}
	name = normalizeDomain(name)
	if name == "" {
		return false
	}
	for _, d := range m.domains {
		if name == d || strings.HasSuffix(name, "."+d) {
			return true
		}
	}
	return false
}

func normalizeDomain(d string) string {
	d = strings.TrimSpace(d)
	d = strings.ToLower(d)
	d = strings.TrimSuffix(d, ".") // drop FQDN trailing dot
	d = strings.TrimPrefix(d, "*.")
	d = strings.TrimPrefix(d, ".")
	return d
}
