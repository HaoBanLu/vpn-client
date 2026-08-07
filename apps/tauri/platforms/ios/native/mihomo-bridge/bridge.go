package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"os"
	"path/filepath"
	"sync"
	"unsafe"

	"github.com/metacubex/mihomo/config"
	Cpath "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/log"
)

var (
	mu      sync.Mutex
	running bool
)

//export ky_mihomo_start
func ky_mihomo_start(configDir *C.char) C.int {
	dir := C.GoString(configDir)
	if dir == "" {
		return 1
	}
	cfgPath := filepath.Join(dir, "config.yaml")
	raw, err := os.ReadFile(cfgPath)
	if err != nil {
		log.Errorln("ky_mihomo_start read config: %s", err.Error())
		return 2
	}

	mu.Lock()
	defer mu.Unlock()
	if running {
		executor.Shutdown()
		running = false
	}

	Cpath.SetHomeDir(dir)
	Cpath.SetConfig(cfgPath)
	if err := config.Init(dir); err != nil {
		log.Errorln("ky_mihomo_start init: %s", err.Error())
		return 3
	}
	if err := hub.Parse(raw); err != nil {
		log.Errorln("ky_mihomo_start parse/apply: %s", err.Error())
		return 4
	}
	running = true
	log.Infoln("ky_mihomo_start ok home=%s", dir)
	return 0
}

//export ky_mihomo_stop
func ky_mihomo_stop() {
	mu.Lock()
	defer mu.Unlock()
	if !running {
		return
	}
	executor.Shutdown()
	running = false
	log.Infoln("ky_mihomo_stop ok")
}

//export ky_mihomo_is_running
func ky_mihomo_is_running() C.int {
	mu.Lock()
	defer mu.Unlock()
	if running {
		return 1
	}
	return 0
}

func main() {}

// keep unsafe referenced for cgo builds on some toolchains
var _ = unsafe.Pointer(nil)
