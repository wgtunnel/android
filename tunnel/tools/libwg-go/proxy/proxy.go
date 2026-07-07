package proxy

/*
#include "vpn_jni.h"
*/
import "C"
import (
	"context"
	"net"
	"sync"

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/ipc"
	"github.com/amnezia-vpn/amneziawg-go/tun/netstack"
	wireproxyawg "github.com/artem-russkikh/wireproxy-awg"
	"github.com/wgtunnel/android/shared"
)

var (
	cancelFuncs          map[int32]context.CancelFunc
	tag                  string
	virtualTunnelHandles map[int32]*wireproxyawg.VirtualTun
	lastTunnelStatus     sync.Map
	tunnelMu             sync.RWMutex
)

func init() {
	tag = "AwgProxy"
	virtualTunnelHandles = make(map[int32]*wireproxyawg.VirtualTun)
	cancelFuncs = make(map[int32]context.CancelFunc)
}

//export awgStartProxy
func awgStartProxy(interfaceName string, config string, uapiPath string, bypass int32) int32 {
	conf, err := wireproxyawg.ParseConfigString(config)
	if err != nil {
		shared.LogError(tag, "Invalid config file", err)
		return -1
	}

	handle, err := shared.GenerateUniqueHandle()
	if err != nil {
		shared.LogError(tag, "Error generating handle: %v", err)
		return -1
	}

	setting, err := wireproxyawg.CreateIPCRequest(conf.Device, false)
	if err != nil {
		shared.LogError(tag, "Create IPC request failed")
		shared.ReleaseHandle(handle)
		return -1
	}

	tun, tnet, err := netstack.CreateNetTUN(
		setting.DeviceAddr,
		setting.DNS,
		setting.MTU,
	)
	if err != nil {
		shared.LogError(tag, "Create TUN failed")
		shared.ReleaseHandle(handle)
		return -1
	}

	name, err := tun.Name()
	if err != nil {
		shared.LogError(tag, "Failed to get TUN name: %v", err)
		shared.ReleaseHandle(handle)
		tun.Close()
		return -1
	}

	bind := conn.NewStdNetBindWithControl(shared.ProtectControlFunc)

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

	dev := device.NewDevice(
		tun,
		bind,
		shared.NewLogger("Tun/"+interfaceName),
		statusCB,
	)

	dev.DisableSomeRoamingForBrokenMobileSemantics()

	if err = dev.IpcSet(setting.IpcRequest); err != nil {
		shared.LogError(tag, "Ipc setting failed")
		shared.ReleaseHandle(handle)
		dev.Close()
		return -1
	}

	var uapi net.Listener
	uapiFile, uapiErr := ipc.UAPIOpen(uapiPath, name)

	if uapiErr != nil {
		shared.LogError(tag, "UAPIOpen: %v", uapiErr)
	} else {
		uapi, err = ipc.UAPIListen(uapiPath, name, uapiFile)

		if err != nil {
			shared.LogError(tag, "UAPIListen: %v", err)
			uapiFile.Close()
			uapi = nil
		} else {
			go func() {
				for {
					connection, err := uapi.Accept()
					if err != nil {
						return
					}
					go dev.IpcHandle(connection)
				}
			}()
		}
	}

	if err = dev.Up(); err != nil {
		shared.LogError(tag, "Failed to bring up device")

		if uapiFile != nil {
			uapiFile.Close()
		}

		if uapi != nil {
			uapi.Close()
		}

		shared.ReleaseHandle(handle)
		dev.Close()
		return -1
	}

	virtualTun := &wireproxyawg.VirtualTun{
		Tnet:           tnet,
		Dev:            dev,
		Logger:         shared.NewLogger("Proxy"),
		Uapi:           uapi,
		Conf:           conf.Device,
		PingRecord:     make(map[string]uint64),
		PingRecordLock: new(sync.Mutex),
	}

	tunnelCtx, tunnelCancel := context.WithCancel(context.Background())

	tunnelMu.Lock()
	virtualTunnelHandles[handle] = virtualTun
	cancelFuncs[handle] = tunnelCancel
	tunnelMu.Unlock()

	for _, spawner := range conf.Routines {
		go func(s wireproxyawg.RoutineSpawner) {
			if err := s.SpawnRoutine(tunnelCtx, virtualTun); err != nil {
				shared.LogError(tag, "Routine failed: %v", err)
			}
		}(spawner)
	}

	shared.LogDebug(tag, "Done starting proxy and tunnel for handle %d", handle)

	return handle
}

//export awgUpdateProxyTunnelPeers
func awgUpdateProxyTunnelPeers(tunnelHandle int32, settings string) int32 {
	tunnelMu.RLock()
	handle, ok := virtualTunnelHandles[tunnelHandle]
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

	err = handle.Dev.IpcSet(ipcRequest.IpcRequest)
	if err != nil {
		shared.LogError(tag, "IpcSet: %v", err)
		return -1
	}

	shared.LogDebug(tag, "Configuration updated successfully")
	return 0
}

//export awgGetProxyConfig
func awgGetProxyConfig(tunnelHandle int32) *C.char {
	tunnelMu.RLock()
	handle, ok := virtualTunnelHandles[tunnelHandle]
	tunnelMu.RUnlock()
	if !ok {
		shared.LogError(tag, "Tunnel is not up")
		return nil
	}
	settings, err := handle.Dev.IpcGet()
	if err != nil {
		shared.LogError(tag, "Failed to get device config: %v", err)
		return nil
	}
	return C.CString(settings)
}

//export awgTriggerProxyBindUpdate
func awgTriggerProxyBindUpdate(handle int32) {
	tunnelMu.RLock()
	vt, ok := virtualTunnelHandles[handle]
	tunnelMu.RUnlock()
	if !ok {
		shared.LogDebug(tag, "awgTriggerProxyBindUpdate: handle %d not found", handle)
		return
	}
	if vt.Dev != nil {
		shared.LogDebug(tag, "Calling BindUpdate on Proxy handle %d", handle)
		vt.Dev.BindUpdate()
	}
}

//export awgTurnProxyTunnelOff
func awgTurnProxyTunnelOff(virtualTunnelHandle int32) {

	tunnelMu.Lock()

	virtualTun, ok := virtualTunnelHandles[virtualTunnelHandle]
	if !ok {
		tunnelMu.Unlock()

		shared.LogError(
			tag,
			"Tunnel handle %d not found",
			virtualTunnelHandle,
		)
		return
	}

	cancel := cancelFuncs[virtualTunnelHandle]

	delete(virtualTunnelHandles, virtualTunnelHandle)
	delete(cancelFuncs, virtualTunnelHandle)

	tunnelMu.Unlock()

	shared.LogDebug(
		tag,
		"Tearing down tunnel %d",
		virtualTunnelHandle,
	)

	if cancel != nil {
		cancel()
	}

	if virtualTun.Uapi != nil {
		virtualTun.Uapi.Close()
	}

	if virtualTun.Dev != nil {
		virtualTun.Dev.Close()
	}

	lastTunnelStatus.Delete(virtualTunnelHandle)
	shared.ReleaseHandle(virtualTunnelHandle)

	C.awgNotifyStatus(
		C.int32_t(virtualTunnelHandle),
		C.int32_t(shared.StatusStop),
	)

	shared.LogDebug(
		tag,
		"Tunnel handle %d fully closed",
		virtualTunnelHandle,
	)
}
