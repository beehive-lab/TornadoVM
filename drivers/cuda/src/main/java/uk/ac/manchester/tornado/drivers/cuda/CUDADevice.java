package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceAttribute;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class CUDADevice extends TornadoLogger implements TornadoTargetDevice {

    private static int INIT_VAL = -1;

    private String name;
    private int[] maxGridSizes;
    private CUDAContext context;
    private long[] maxWorkItemSizes;
    private String computeCapability;
    private String targetArchitecture;

    private int index;
    private int maxFrequency;
    private int noOfWorkUnits;
    private long localMemorySize;
    private long totalDeviceMemory;
    private long constantBufferSize;
    private int computeCapabilityMajor;
    private int computeCapabilityMinor;

    public CUDADevice(int index) {
        this.index = index;
        computeCapabilityMajor = INIT_VAL;
        computeCapabilityMinor = INIT_VAL;
        constantBufferSize = INIT_VAL;
        totalDeviceMemory = INIT_VAL;
        localMemorySize = INIT_VAL;
        noOfWorkUnits = INIT_VAL;
        maxFrequency = INIT_VAL;

        context = new CUDAContext(this);
    }

    native static String cuDeviceGetName(int deviceId);

    native static int cuDeviceGetAttribute(int deviceId, int attribute);

    native static long cuDeviceTotalMem(int deviceId);

    public long getId() {
        return 1;
    }

    @Override
    public String getDeviceName() {
        if (name == null) name = cuDeviceGetName(index);
        return name;
    }

    private void ensureComputeCapabilityAvailable() {
        if (computeCapabilityMajor == INIT_VAL)
            computeCapabilityMajor = cuDeviceGetAttribute(index, CUDADeviceAttribute.COMPUTE_CAPABILITY_MAJOR.value());

        if (computeCapabilityMinor == INIT_VAL)
            computeCapabilityMinor = cuDeviceGetAttribute(index, CUDADeviceAttribute.COMPUTE_CAPABILITY_MINOR.value());
    }

    public String getDeviceComputeCapability() {
        if (computeCapability == null) {
            ensureComputeCapabilityAvailable();
            computeCapability = String.format("%d.%d",computeCapabilityMajor ,computeCapabilityMinor);
        }
        return computeCapability;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        if (totalDeviceMemory == INIT_VAL) {
            totalDeviceMemory = cuDeviceTotalMem(index);
        }
        return totalDeviceMemory;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        if (localMemorySize == INIT_VAL) {
            localMemorySize = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_SHARED_MEMORY_PER_BLOCK.value());
        }
        return localMemorySize;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (noOfWorkUnits == INIT_VAL) {
            noOfWorkUnits = cuDeviceGetAttribute(index, CUDADeviceAttribute.MULTIPROCESSOR_COUNT.value());
        }
        return noOfWorkUnits;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        if (maxWorkItemSizes == null) {
            maxWorkItemSizes = new long[3];
            maxWorkItemSizes[0] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_X.value());
            maxWorkItemSizes[1] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_Y.value());
            maxWorkItemSizes[2] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_BLOCK_DIM_Z.value());
        }

        return maxWorkItemSizes;
    }

    public int[] getDeviceMaxGridSizes() {
        if (maxGridSizes == null) {
            maxGridSizes = new int[3];
            maxGridSizes[0] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_GRID_DIM_X.value());
            maxGridSizes[1] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_GRID_DIM_Y.value());
            maxGridSizes[2] = cuDeviceGetAttribute(index, CUDADeviceAttribute.MAX_GRID_DIM_Z.value());
        }

        return maxGridSizes;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (maxFrequency == INIT_VAL) {
            maxFrequency = cuDeviceGetAttribute(index, CUDADeviceAttribute.CLOCK_RATE.value());
        }
        return maxFrequency;
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        if (constantBufferSize == INIT_VAL) {
            constantBufferSize = cuDeviceGetAttribute(index, CUDADeviceAttribute.TOTAL_CONSTANT_MEMORY.value());
        }
        return constantBufferSize;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return 0;
    }

    @Override
    public Object getDeviceInfo() {
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

    public TornadoDeviceType getDeviceType() {
        return TornadoDeviceType.GPU;
    }

    public String getTargetArchitecture() {
        if (targetArchitecture == null) {
            ensureComputeCapabilityAvailable();
            targetArchitecture = String.format("sm_%d%d", computeCapabilityMajor, computeCapabilityMinor);
        }
        return targetArchitecture;
    }

}
