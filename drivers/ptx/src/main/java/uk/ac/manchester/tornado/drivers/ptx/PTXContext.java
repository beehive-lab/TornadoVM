package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXContext extends TornadoLogger {

    private final long contextPtr;
    private final PTXDevice device;
    private final PTXStream stream;
    private final PTXDeviceContext deviceContext;
    private long[] allocatedRegions;
    private int allocatedRegionCount;

    private static final int MAX_ALLOCATED_REGIONS = 64;

    public PTXContext(PTXDevice device) {
        this.device = device;

        contextPtr = cuCtxCreate(device.getIndex());

        stream = new PTXStream();
        deviceContext = new PTXDeviceContext(device, stream);

        allocatedRegionCount = 0;
        allocatedRegions = new long[MAX_ALLOCATED_REGIONS];
    }

    private native static long cuCtxCreate(int deviceIndex);

    private native static long cuCtxDestroy(long cuContext);

    private native static long cuMemAlloc(long cuContext, long numBytes);

    private native static long cuMemFree(long cuContext, long devicePtr);

    private native static long cuCtxSetCurrent(long cuContext);

    public void setContextForCurrentThread() {
        cuCtxSetCurrent(contextPtr);
    }

    public void cleanup() {
        deviceContext.cleanup();

        for (int i = 0; i < allocatedRegionCount; i++) {
            cuMemFree(contextPtr, allocatedRegions[i]);
        }

        cuCtxDestroy(contextPtr);
    }

    public PTXDeviceContext getDeviceContext() {
        return deviceContext;
    }

    public long allocateMemory(long numBytes) {
        long devicePtr = 0;
        try {
            devicePtr = cuMemAlloc(contextPtr, numBytes);
            allocatedRegions[allocatedRegionCount] = devicePtr;
            allocatedRegionCount++;
        } catch (Exception e) {
            error(e.getMessage());
        }
        return devicePtr;
    }
}
