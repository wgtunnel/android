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
	"context"
	"net"
	"net/netip"
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
	"golang.org/x/sys/unix"
)

type TunnelHandle struct {
	device *device.Device
	uapi   net.Listener
	cancel context.CancelFunc
}

var (
	tag              string
	resolvingHandles = sync.Map{}
	tunnelHandles    = make(map[int32]*TunnelHandle)
)

func init() {
	tag = "AwgVPN"
}

//export awgTurnOn
func awgTurnOn(ifName string, tunFd int32, settings string, uapiPath string) int32 {

	handleID, err := shared.GenerateUniqueHandle()
	if err != nil {
		shared.LogError(tag, "Error generating handle: %v", err)
		return -1
	}

	tunnel, name, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))

	if err != nil {
		unix.Close(int(tunFd))
		shared.LogError(tag, "CreateUnmonitoredTUNFromFD: %v", err)
		return -1
	}

	h := &TunnelHandle{}
	var success bool

	defer func() {
		if !success {
			shared.LogDebug(tag, "Startup failed, cleaning up partial resources for handle %d", handleID)
			h.close()
			resolvingHandles.Delete(handleID)
		}
	}()

	conf, err := wireproxyawg.ParseConfigString(settings)
	if err != nil {
		shared.LogError(tag, "Invalid config file", err)
		unix.Close(int(tunFd))
		if tunnel != nil {
			tunnel.Close()
		}
		return -1
	}

	tunnelCtx, tunnelCancel := context.WithCancel(context.Background())
	h.cancel = tunnelCancel

	type peerToResolve struct {
		index int
		host  string
	}
	var resolutionQueue []peerToResolve

	for i := range conf.Device.Peers {
		peer := &conf.Device.Peers[i]
		if peer.NeedsResolution() {
			host, port, err := net.SplitHostPort(*peer.Endpoint)
			if err != nil {
				shared.LogError(tag, "Failed to parse endpoint", err)
				continue
			}
			// set dummy, non-routable address with original port
			dummyEndpoint := shared.DummyAddress + ":" + port
			peer.Endpoint = &dummyEndpoint

			resolutionQueue = append(resolutionQueue, peerToResolve{i, host})
		}
	}

	shared.LogDebug(tag, "Creating device with domain blocking enabled: %v", conf.Device.DomainBlockingEnabled)

	statusCB := func(code device.StatusCode) {
		go C.awgNotifyStatus(C.int32_t(handleID), C.CString(ifName), C.int32_t(code))
	}

	tunDevice := device.NewDevice(tunnel, conn.NewStdNetBind(), shared.NewLogger("Tun/"+ifName), conf.Device.DomainBlockingEnabled, statusCB)

	ipcRequest, err := wireproxyawg.CreateIPCRequest(conf.Device, false)
	if err != nil {
		shared.LogError(tag, "CreateIPCRequest: %v", err)
		unix.Close(int(tunFd))
		return -1
	}

	err = tunDevice.IpcSet(ipcRequest.IpcRequest)
	if err != nil {
		unix.Close(int(tunFd))
		shared.LogError(tag, "IpcSet: %v", err)
		return -1
	}
	tunDevice.DisableSomeRoamingForBrokenMobileSemantics()

	var uapi net.Listener

	uapiFile, err := ipc.UAPIOpen(uapiPath, name)

	if err != nil {
		shared.LogError(tag, "UAPIOpen: %v", err)
	} else {
		uapi, err = ipc.UAPIListen(uapiPath, name, uapiFile) // uapiPath as rootdir, name as interface
		if err != nil {
			uapiFile.Close()
			shared.LogError(tag, "UAPIListen: %v", err)
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
		uapiFile.Close()
		tunDevice.Close()
		return -1
	}
	shared.LogDebug(tag, "Device started")

	for _, p := range resolutionQueue {
		go resolveAndUpdatePeer(tunnelCtx, handleID, conf, p.index, p.host, ifName)
	}

	success = true

	h.device = tunDevice
	h.uapi = uapi

	tunnelHandles[handleID] = h

	return handleID
}

// resolveAndUpdatePeer resolves the host and updates the peer's endpoint if successful.
func resolveAndUpdatePeer(ctx context.Context, tunnelHandle int32, conf *wireproxyawg.Configuration, peerIndex int, host string, ifName string) {

	resolvingHandles.Store(tunnelHandle, true)
	C.awgNotifyStatus(C.int32_t(tunnelHandle), C.CString(ifName), C.int32_t(shared.StatusResolvingDNS))

	select {
	case <-ctx.Done():
		shared.LogDebug(tag, "Tunnel context cancelled, stopping resolver for %s", host)
		resolvingHandles.Delete(tunnelHandle)
		return
	default:
	}

	// TODO make configurable by user
	preferIPv6 := false

	resolved, err := dns.ResolveWithBackoff(ctx, host, preferIPv6)
	if err != nil {
		shared.LogError(tag, "Permanent failure resolving %s: %v", host, err)
		return
	}
	shared.LogDebug(tag, "Successfully resolved the tunnel peer endpoints..")

	var ip netip.Addr
	if preferIPv6 && len(resolved.V6) > 0 {
		ip = resolved.V6[0]
		shared.LogDebug(tag, "Successfully set peer endpoint to preferred resolved ipv6..")
	} else if len(resolved.V4) > 0 {
		ip = resolved.V4[0]
		shared.LogDebug(tag, "Successfully set peer endpoint to resolved ipv4..")
	} else {
		shared.LogError(tag, "No suitable IP resolved for %s", host)
		return
	}

	shared.LogDebug(tag, "Updating config with resolved peer endpoints..")
	// Update the peer config's peer endpoint from dummy
	peer := &conf.Device.Peers[peerIndex]
	if err := peer.UpdateEndpointIP(ip); err != nil {
		shared.LogError(tag, "Failed to update endpoint for peer %s: %v", peer.PublicKey, err)
		return
	}

	// Update peers via UAPI
	ipcRequest, err := wireproxyawg.CreatePeerIPCRequest(conf.Device)
	if err != nil {
		shared.LogError(tag, "CreatePeerIPCRequest: %v", err)
		return
	}

	handle, ok := tunnelHandles[tunnelHandle]
	if !ok || handle.cancel == nil {
		shared.LogDebug(tag, "Tunnel down, skipping update for %s", host)
		return
	}
	if err := handle.device.IpcSet(ipcRequest.IpcRequest); err != nil {
		shared.LogError(tag, "Failed to update peers: %v", err)
		return
	}

	shared.LogDebug(tag, "Successfully updated peer with resolved endpoint for %s", host)
	resolvingHandles.Delete(tunnelHandle)
}

func (h *TunnelHandle) close() {
	if h == nil {
		return
	}

	// stop all goroutines
	if h.cancel != nil {
		h.cancel()
	}

	// close UAPI listener
	if h.uapi != nil {
		_ = h.uapi.Close()
	}

	// close tun device
	if h.device != nil {
		h.device.Close()
	}
}

//export awgUpdateTunnelPeers
func awgUpdateTunnelPeers(tunnelHandle int32, settings string) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
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

	shared.LogDebug(tag, "Configuration updated successfully")
	return 0
}

//export awgTurnOff
func awgTurnOff(tunnelHandle int32) {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		shared.LogError(tag, "Tunnel is not up")
		return
	}
	delete(tunnelHandles, tunnelHandle)
	if handle.uapi != nil {
		handle.uapi.Close()
	}
	handle.device.Close()
	shared.ReleaseHandle(tunnelHandle)
}

//export awgGetSocketV4
func awgGetSocketV4(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	bind, _ := handle.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd4()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export awgGetSocketV6
func awgGetSocketV6(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	bind, _ := handle.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd6()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export awgGetConfig
func awgGetConfig(tunnelHandle int32) *C.char {
	handle, ok := tunnelHandles[tunnelHandle]
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
