/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2022 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

package vpn

/*
#include "vpn_jni.h"
*/
import "C"
import (
	"net"
	"runtime/debug"
	"strings"
	"sync"

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/ipc"
	"github.com/amnezia-vpn/amneziawg-go/tun"
	wireproxyawg "github.com/artem-russkikh/wireproxy-awg"
	"github.com/wgtunnel/android/dns"
	"github.com/wgtunnel/android/shared"
	"github.com/wgtunnel/android/splitdns"
	"golang.org/x/sys/unix"
)

func init() {
	// Route splitdns logging through the shared platform logger.
	splitdns.SetLoggers(
		func(format string, args ...any) { shared.LogDebug("SplitDNS", format, args...) },
		func(format string, args ...any) { shared.LogError("SplitDNS", format, args...) },
	)
}

// splitCSV splits a comma-separated list, trimming whitespace and dropping
// empty entries.
//
// Each entry is cloned. The caller passes strings that originate from a cgo
// //export parameter, which aliases the JNI GetStringUTFChars buffer rather than
// copying it. That buffer is released as soon as awgTurnOn returns, but the split
// DNS matcher retains these entries for the tunnel's lifetime, so they must own
// their backing memory. strings.Split/TrimSpace alone return sub-slices of the
// borrowed buffer, hence the explicit Clone.
func splitCSV(s string) []string {
	out := make([]string, 0)
	for _, part := range strings.Split(s, ",") {
		part = strings.TrimSpace(part)
		if part != "" {
			out = append(out, strings.Clone(part))
		}
	}
	return out
}

type TunnelHandle struct {
	device   *device.Device
	uapi     net.Listener
	splitDns *splitdns.Device
}

var (
	tag              string
	tunnelHandles    = make(map[int32]TunnelHandle)
	lastTunnelStatus sync.Map
	tunnelMu         sync.RWMutex
)

func init() {
	tag = "AwgVPN"
}

//export awgTurnOn
func awgTurnOn(interfaceName string, tunFd int32, settings string, uapiPath string, splitDnsDomains string, splitDnsSystemServers string) int32 {
	tunDev, name, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))

	if err != nil {
		unix.Close(int(tunFd))
		shared.LogError(tag, "CreateUnmonitoredTUNFromFD: %v", err)
		return -1
	}

	var tunnel tun.Device = tunDev
	var splitDnsDev *splitdns.Device

	if domains := splitCSV(splitDnsDomains); len(domains) > 0 {
		servers := splitCSV(splitDnsSystemServers)
		shared.LogDebug(tag, "Enabling split DNS for %d domain(s), system servers=%v", len(domains), servers)
		splitDnsDev = splitdns.NewDevice(
			tunDev,
			splitdns.NewMatcher(domains),
			servers,
			dns.GetDialer(true),
		)
		tunnel = splitDnsDev
	}

	conf, err := wireproxyawg.ParseConfigString(settings)
	if err != nil {
		shared.LogError(tag, "Invalid config file", err)
		if tunnel != nil {
			tunnel.Close()
		}
		return -1
	}

	handle, err := shared.GenerateUniqueHandle()
	if err != nil {
		shared.LogError(tag, "Unable to generate handle: %v", err)
		if tunnel != nil {
			tunnel.Close()
		}
		return -1
	}

	statusCB := func(code device.StatusCode) {
		key := handle
		if prev, loaded := lastTunnelStatus.LoadOrStore(key, code); loaded {
			if prev == code {
				return // duplicate, skip
			}
			lastTunnelStatus.Store(key, code)
		}
		go C.awgNotifyStatus(C.int32_t(handle), C.int32_t(code))
	}

	tunDevice := device.NewDevice(tunnel, conn.NewStdNetBindWithControl(shared.ProtectControlFunc), shared.NewLogger("Tun/"+interfaceName), statusCB)
	tunDevice.DisableSomeRoamingForBrokenMobileSemantics()

	ipcRequest, err := wireproxyawg.CreateIPCRequest(conf.Device, false)
	if err != nil {
		shared.LogError(tag, "CreateIPCRequest: %v", err)
		shared.ReleaseHandle(handle)
		tunDevice.Close()
		return -1
	}

	err = tunDevice.IpcSet(ipcRequest.IpcRequest)
	if err != nil {
		shared.LogError(tag, "IpcSet: %v", err)
		shared.ReleaseHandle(handle)
		tunDevice.Close()
		return -1
	}

	var uapi net.Listener
	uapiFile, uapiErr := ipc.UAPIOpen(uapiPath, name)
	if uapiErr != nil {
		shared.LogError(tag, "UAPIOpen: %v", uapiErr)
		uapiFile = nil
	} else {
		uapi, err = ipc.UAPIListen(uapiPath, name, uapiFile)
		if err != nil {
			shared.LogError(tag, "UAPIListen: %v", err)
			uapiFile.Close()
			uapiFile = nil
			uapi = nil
		} else {
			go func() {
				for {
					connection, err := uapi.Accept()
					if err != nil {
						return
					}
					go tunDevice.IpcHandle(connection)
				}
			}()
		}
	}

	err = tunDevice.Up()
	if err != nil {
		shared.LogError(tag, "Unable to bring up device: %v", err)
		if uapiFile != nil {
			uapiFile.Close()
		}
		if uapi != nil {
			uapi.Close()
		}
		shared.ReleaseHandle(handle)
		tunDevice.Close()
		return -1
	}

	shared.LogDebug(tag, "Tunnel started successfully for handle %d", handle)

	tunnelMu.Lock()
	tunnelHandles[handle] = TunnelHandle{
		device:   tunDevice,
		uapi:     uapi,
		splitDns: splitDnsDev,
	}
	tunnelMu.Unlock()
	return handle
}

//export awgSetSplitDnsServers
func awgSetSplitDnsServers(tunnelHandle int32, servers string) int32 {
	tunnelMu.RLock()
	handle, ok := tunnelHandles[tunnelHandle]
	tunnelMu.RUnlock()
	if !ok {
		shared.LogError(tag, "awgSetSplitDnsServers: tunnel is not up")
		return -1
	}
	if handle.splitDns == nil {
		return -1
	}
	// splitCSV clones each entry out of the borrowed JNI string buffer.
	handle.splitDns.SetServers(splitCSV(servers))
	return 0
}

//export awgUpdateTunnelPeers
func awgUpdateTunnelPeers(tunnelHandle int32, settings string) int32 {
	tunnelMu.RLock()
	handle, ok := tunnelHandles[tunnelHandle]
	tunnelMu.RUnlock()
	if !ok {
		shared.LogError(tag, "Tunnel is not up")
		return -1
	}

	conf, err := wireproxyawg.ParseConfigString(settings)
	if err != nil {
		shared.LogError(tag, "Invalid config file", err)
		return -1
	}

	ipcRequest, err := wireproxyawg.CreatePeerIPCRequest(conf.Device)
	if err != nil {
		shared.LogError(tag, "CreateIPCRequest: %v", err)
		return -1
	}

	err = handle.device.IpcSet(ipcRequest.IpcRequest)
	if err != nil {
		shared.LogError(tag, "IpcSet: %v", err)
		return -1
	}

	shared.LogDebug(tag, "Configuration updated successfully with handle %d", handle)
	return 0
}

//export awgTurnOff
func awgTurnOff(tunnelHandle int32) {

	tunnelMu.Lock()

	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		tunnelMu.Unlock()

		shared.LogError(tag, "Tunnel is not up")
		return
	}

	delete(tunnelHandles, tunnelHandle)

	tunnelMu.Unlock()

	if handle.uapi != nil {
		handle.uapi.Close()
	}

	if handle.device != nil {
		handle.device.Close()
	}

	lastTunnelStatus.Delete(tunnelHandle)
	shared.ReleaseHandle(tunnelHandle)

	C.awgNotifyStatus(
		C.int32_t(tunnelHandle),
		C.int32_t(shared.StatusStop),
	)
}

//export awgGetConfig
func awgGetConfig(tunnelHandle int32) *C.char {

	tunnelMu.RLock()
	handle, ok := tunnelHandles[tunnelHandle]
	tunnelMu.RUnlock()

	if !ok {
		return nil
	}

	settings, err := handle.device.IpcGet()
	if err != nil {
		return nil
	}

	return C.CString(settings)
}

//export awgVersion
func awgVersion() *C.char {
	info, ok := debug.ReadBuildInfo()
	if !ok {
		return C.CString("unknown")
	}
	for _, dep := range info.Deps {
		if dep.Path == "github.com/amnezia-vpn/amneziawg-go" {
			parts := strings.Split(dep.Version, "-")
			if len(parts) == 3 && len(parts[2]) == 12 {
				return C.CString(parts[2][:7])
			}
			return C.CString(dep.Version)
		}
	}
	return C.CString("unknown")
}
