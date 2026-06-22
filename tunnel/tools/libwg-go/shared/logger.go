package shared

// #cgo LDFLAGS: -llog
// #include <android/log.h>
import "C"
import (
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"unsafe"

	"github.com/amnezia-vpn/amneziawg-go/device"
	"golang.org/x/sys/unix"
)

func cstring(s string) *C.char {
	b, err := unix.BytePtrFromString(s)
	if err != nil {
		b := [1]C.char{}
		return &b[0]
	}
	return (*C.char)(unsafe.Pointer(b))
}

func init() {
	signals := make(chan os.Signal)
	signal.Notify(signals, unix.SIGUSR2)
	go func() {
		buf := make([]byte, os.Getpagesize())
		for {
			select {
			case <-signals:
				n := runtime.Stack(buf, true)
				if n == len(buf) {
					n--
				}
				buf[n] = 0
				C.__android_log_write(C.ANDROID_LOG_ERROR, cstring("AmneziaWG/Stacktrace"), (*C.char)(unsafe.Pointer(&buf[0])))
			}
		}
	}()
}

func LogDebug(tag string, format string, args ...interface{}) {
	C.__android_log_write(C.ANDROID_LOG_DEBUG, cstring(tag), cstring(fmt.Sprintf(format, args...)))
}

func LogError(tag string, format string, args ...interface{}) {
	C.__android_log_write(C.ANDROID_LOG_ERROR, cstring(tag), cstring(fmt.Sprintf(format, args...)))
}

func NewLogger(tag string) *device.Logger {
	return &device.Logger{
		Verbosef: func(format string, args ...any) {
			LogDebug(tag, format, args...)
		},
		Errorf: func(format string, args ...any) {
			LogError(tag, format, args...)
		},
	}
}
