package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DUMP_EVENTS;

public class PTXContext extends TornadoLogger {

    private final long ptxContext;
    private final PTXDevice device;
    private final PTXStream stream;
    private final PTXDeviceContext deviceContext;
    private long allocatedRegion;

    public PTXContext(PTXDevice device) {
        this.device = device;

        ptxContext = cuCtxCreate(device.getCuDevice());

        stream = new PTXStream();
        deviceContext = new PTXDeviceContext(device, stream);
    }

    private native static long cuCtxCreate(long deviceIndex);

    private native static long cuCtxDestroy(long cuContext);

    private native static long cuMemAlloc(long cuContext, long numBytes);

    private native static long cuMemFree(long cuContext, long devicePtr);

    private native static long cuCtxSetCurrent(long cuContext);

    public void enablePTXContext() {
        cuCtxSetCurrent(ptxContext);
    }

    public void cleanup() {
        if (DUMP_EVENTS) {
            deviceContext.dumpEvents();
        }

        deviceContext.cleanup();
        cuMemFree(ptxContext, allocatedRegion);
        cuCtxDestroy(ptxContext);
    }

    public PTXDeviceContext getDeviceContext() {
        return deviceContext;
    }

    public long allocateMemory(long numBytes) {
        TornadoInternalError.guarantee(allocatedRegion == 0, "Only a single heap allocation is supported");
        try {
            allocatedRegion = cuMemAlloc(ptxContext, numBytes);
        } catch (Exception e) {
            throw new TornadoBailoutRuntimeException("[Error during memory allocation] ", e);
        }
        return allocatedRegion;
    }
}
