module github.com/wgtunnel/android

go 1.24.4

require (
	github.com/amnezia-vpn/amneziawg-go v0.2.16
	github.com/artem-russkikh/wireproxy-awg v1.0.12
	github.com/miekg/dns v1.1.69
	golang.org/x/sys v0.38.0
)

require (
	github.com/MakeNowJust/heredoc/v2 v2.0.1 // indirect
	github.com/go-ini/ini v1.67.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/things-go/go-socks5 v0.1.0 // indirect
	golang.org/x/crypto v0.45.0 // indirect
	golang.org/x/mod v0.30.0 // indirect
	golang.org/x/net v0.47.0 // indirect
	golang.org/x/sync v0.18.0 // indirect
	golang.org/x/time v0.14.0 // indirect
	golang.org/x/tools v0.39.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	gvisor.dev/gvisor v0.0.0-20250503011706-39ed1f5ac29c // indirect
)

replace github.com/amnezia-vpn/amneziawg-go => github.com/wgtunnel/amneziawg-go v0.0.0-20260618075902-e1b699b2104b

replace github.com/artem-russkikh/wireproxy-awg => github.com/wgtunnel/wireproxy-awg v0.0.0-20260309043206-ff4200f20ff2

replace github.com/things-go/go-socks5 => github.com/wgtunnel/go-socks5 v0.0.0-20260307052555-86f8d93b9534

// local dev
//replace github.com/amnezia-vpn/amneziawg-go => ../../../../amneziawg-go

//replace github.com/artem-russkikh/wireproxy-awg => ../../../../wireproxy-awg
