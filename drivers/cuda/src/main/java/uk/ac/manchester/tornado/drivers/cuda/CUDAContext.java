package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAContext extends TornadoLogger {

    private final CUDADevice device;
    private final CUDAStream stream;
    private final CUDADeviceContext deviceContext;
    private long[] allocatedRegions;
    private int allocatedRegionCount;

    private static final int MAX_ALLOCATED_REGIONS = 64;

    public CUDAContext(CUDADevice device) {
        this.device = device;

        cuCtxCreate(device.getIndex());

        stream = new CUDAStream(device.getIndex());
        deviceContext = new CUDADeviceContext(device, this, stream);

        allocatedRegionCount = 0;
        allocatedRegions = new long[MAX_ALLOCATED_REGIONS];
    }

    private native static void cuCtxCreate(int deviceIndex);

    private native static void cuCtxDestroy(int deviceIndex);

    private native static long cuMemAlloc(int deviceIndex, long numBytes);

    private native static void cuMemFree(int deviceIndex, long devicePtr);

    public void cleanup() {

        for (int i = 0; i < allocatedRegionCount; i++) {
            cuMemFree(device.getIndex(), allocatedRegions[i]);
        }

        cuCtxDestroy(device.getIndex());
    }

    public CUDADeviceContext getDeviceContext() {
        return deviceContext;
    }

    public long allocateMemory(long numBytes) {
        long devicePtr = 0;
        try {
            devicePtr = cuMemAlloc(device.getIndex(), numBytes);
            allocatedRegions[allocatedRegionCount] = devicePtr;
            allocatedRegionCount++;
        } catch (Exception e) {
            error(e.getMessage());
        }
        return devicePtr;
    }
}
