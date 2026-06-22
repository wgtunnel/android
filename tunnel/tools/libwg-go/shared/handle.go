package shared

/*
#include <stdint.h>
*/
import "C"
import (
	"fmt"
	"math"
	"sync"
)

var (
	handleMu    sync.Mutex
	usedHandles       = make(map[int32]bool)
	nextHandle  int32 = 0
)

// GenerateUniqueHandle returns a globally unique handle across all backends.
func GenerateUniqueHandle() (int32, error) {
	handleMu.Lock()
	defer handleMu.Unlock()

	for i := 0; i < math.MaxInt32; i++ {
		h := nextHandle
		nextHandle++
		if nextHandle < 0 {
			nextHandle = 0
		}
		if !usedHandles[h] {
			usedHandles[h] = true
			return h, nil
		}
	}
	return -1, fmt.Errorf("no free handles available")
}

// ReleaseHandle marks a handle as free again.
func ReleaseHandle(handle int32) {
	if handle < 0 {
		return
	}
	handleMu.Lock()
	delete(usedHandles, handle)
	handleMu.Unlock()
}
