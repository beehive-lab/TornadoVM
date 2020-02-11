package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAContext context;
    private final CUDAMemoryManager memoryManager;

    public CUDADeviceContext(CUDADevice device, CUDAContext context) {
        this.device = device;
        this.context = context;
        this.memoryManager = new CUDAMemoryManager();
    }

    @Override public CUDAMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override public boolean needsBump() {
        return false;
    }

    @Override public boolean wasReset() {
        return false;
    }

    @Override public void setResetToFalse() {

    }

    @Override public boolean isInitialised() {
        return false;
    }

    public CUDATornadoDevice asMapping() {
        return new CUDATornadoDevice(device.getIndex());
    }

    public TornadoInstalledCode installCode(PTXCompilationResult result) {
        return new PTXInstalledCode("foo");
    }
}
