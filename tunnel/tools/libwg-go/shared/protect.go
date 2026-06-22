package shared

/*
#include "vpn_jni.h"
*/
import "C"
import "syscall"

// ProtectControlFunc control hook to bypass sockets
func ProtectControlFunc(network, address string, c syscall.RawConn) error {
	var opErr error
	err := c.Control(func(fd uintptr) {
		if C.bypass_socket(C.int(fd)) == 0 {
			opErr = syscall.EACCES
			LogError("Protect", "Failed to protect socket FD: %d", fd)
		} else {
			LogDebug("Protect", "Protected socket FD: %d", fd)
		}
	})
	if err != nil {
		return err
	}
	return opErr
}
