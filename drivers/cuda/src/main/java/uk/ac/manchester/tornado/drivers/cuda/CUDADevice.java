package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceAttribute;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class CUDADevice extends TornadoLogger implements TornadoTargetDevice {

    private static int INIT_VAL = -1;

    private int index;
    private String name;
    private long constantBufferSize;
    private long totalDeviceMemory;
    private long localMemorySize;
    private int noOfWorkUnits;
    private int maxFrequency;
    private CUDAContext context;
    private long[] maxWorkItemSizes;

    public CUDADevice(int index) {
        this.index = index;
        context = new CUDAContext(this);
        constantBufferSize = INIT_VAL;
        totalDeviceMemory = INIT_VAL;
        localMemorySize = INIT_VAL;
        noOfWorkUnits = INIT_VAL;
        maxFrequency = INIT_VAL;
    }

    native static String cuDeviceGetName(int deviceId);

    native static int cuDeviceGetAttribute(int deviceId, int attribute);

    native static long cuDeviceTotalMem(int deviceId);

    public long getId() {
        return 1;
    }

    @Override public String getDeviceName() {
        if (name == null) name = cuDeviceGetName(index);
        return name;
    }

    @Override public long getDeviceGlobalMemorySize() {
        if (totalDeviceMemory == INIT_VAL) {
            totalDeviceMemory = cuDeviceTotalMem(index);
        }
        return totalDeviceMemory;
    }

    @Override public long getDeviceLocalMemorySize() {
        if (localMemorySize == INIT_VAL) {
            localMemorySize = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_SHARED_MEMORY_PER_BLOCK.value());
        }
        return localMemorySize;
    }

    @Override public int getDeviceMaxComputeUnits() {
        if (noOfWorkUnits == INIT_VAL) {
            noOfWorkUnits = cuDeviceGetAttribute(index, CUDADeviceAttribute.MULTIPROCESSOR_COUNT.value());
        }
        return noOfWorkUnits;
    }

    @Override public long[] getDeviceMaxWorkItemSizes() {
        if (maxWorkItemSizes != null) return maxWorkItemSizes;

        maxWorkItemSizes = new long[3];

        maxWorkItemSizes[0] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_X.value());
        maxWorkItemSizes[1] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_Y.value());
        maxWorkItemSizes[2] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_Z.value());

        return maxWorkItemSizes;
    }

    @Override public int getDeviceMaxClockFrequency() {
        if (maxFrequency == INIT_VAL) {
            maxFrequency = cuDeviceGetAttribute(index, CUDADeviceAttribute.CLOCK_RATE.value());
        }
        return maxFrequency;
    }

    @Override public long getDeviceMaxConstantBufferSize() {
        if (constantBufferSize == INIT_VAL) {
            constantBufferSize = cuDeviceGetAttribute(index, CUDADeviceAttribute.TOTAL_CONSTANT_MEMORY.value());
        }
        return constantBufferSize;
    }

    @Override public long getDeviceMaxAllocationSize() {
        return 0;
    }

    @Override public Object getDeviceInfo() {
        return null;
    }

    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    public int getIndex() {
        return index;
    }

    public CUDAContext getContext() {
        return context;
    }
}
