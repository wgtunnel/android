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

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/ipc"
	"github.com/amnezia-vpn/amneziawg-go/tun"
	wireproxyawg "github.com/artem-russkikh/wireproxy-awg"
	"github.com/wgtunnel/android/shared"
	"golang.org/x/sys/unix"
)

type TunnelHandle struct {
	device *device.Device
	uapi   net.Listener
}

var (
	tag           string
	tunnelHandles = make(map[int32]TunnelHandle)
)

func init() {
	tag = "AwgVPN"
}

//export awgTurnOn
func awgTurnOn(interfaceName string, tunFd int32, settings string, uapiPath string) int32 {
	tunnel, name, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))

	if err != nil {
		unix.Close(int(tunFd))
		shared.LogError(tag, "CreateUnmonitoredTUNFromFD: %v", err)
		return -1
	}

	conf, err := wireproxyawg.ParseConfigString(settings)
	if err != nil {
		shared.LogError(tag, "Invalid config file", err)
		unix.Close(int(tunFd))
		if tunnel != nil {
			tunnel.Close()
		}
		return -1
	}

	shared.LogDebug(tag, "Creating device with domain blocking enabled: %v", conf.Device.DomainBlockingEnabled)

	handle, err2 := shared.GenerateUniqueHandle()

	statusCB := func(code device.StatusCode) {
		go C.awgNotifyStatus(C.int32_t(handle), C.int32_t(code))
	}

	tunDevice := device.NewDevice(tunnel, conn.NewStdNetBind(), shared.NewLogger("Tun/"+interfaceName), conf.Device.DomainBlockingEnabled, statusCB)

	ipcRequest, err := wireproxyawg.CreateIPCRequest(conf.Device, false)
	if err != nil {
		shared.LogError(tag, "CreateIPCRequest: %v", err)
		unix.Close(int(tunFd))
		shared.ReleaseHandle(handle)
		return -1
	}

	err = tunDevice.IpcSet(ipcRequest.IpcRequest)
	if err != nil {
		unix.Close(int(tunFd))
		shared.ReleaseHandle(handle)
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
		shared.ReleaseHandle(handle)
		tunDevice.Close()
		return -1
	}
	shared.LogDebug(tag, "Device started")

	if err2 != nil {
		shared.LogError(tag, "Unable to find empty handle", err2)
		uapiFile.Close()
		shared.ReleaseHandle(handle)
		tunDevice.Close()
		return -1
	}

	tunnelHandles[handle] = TunnelHandle{device: tunDevice, uapi: uapi}

	return handle
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

	go C.awgNotifyStatus(
		C.int32_t(tunnelHandle),
		C.int32_t(shared.StatusStop),
	)

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
