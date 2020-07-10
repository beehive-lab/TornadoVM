package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.ptx.enums.PTXDeviceAttribute;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class PTXDevice extends TornadoLogger implements TornadoTargetDevice {

    private static int INIT_VAL = -1;

    private String name;
    private int[] maxGridSizes;
    private PTXContext context;
    private PTXVersion ptxVersion;
    private long[] maxWorkItemSizes;
    private String targetArchitecture;
    private CUDAComputeCapability computeCapability;

    private int index;
    private int maxFrequency;
    private int noOfWorkUnits;
    private long localMemorySize;
    private long totalDeviceMemory;
    private long constantBufferSize;

    public PTXDevice(int index) {
        this.index = index;
        constantBufferSize = INIT_VAL;
        totalDeviceMemory = INIT_VAL;
        localMemorySize = INIT_VAL;
        noOfWorkUnits = INIT_VAL;
        maxFrequency = INIT_VAL;

        context = new PTXContext(this);
    }

    private native static String cuDeviceGetName(int deviceId);
    private native static int cuDeviceGetAttribute(int deviceId, int attribute);
    private native static long cuDeviceTotalMem(int deviceId);
    private native static long cuMemGetInfo(int deviceId);
    private native static int cuDriverGetVersion();

    @Override
    public String getDeviceName() {
        if (name == null) name = cuDeviceGetName(index);
        return name;
    }

    private void ensurePTXVersionAvailable() {
        if (ptxVersion == null) {
            ptxVersion = CUDAVersion.getMaxPTXVersion(cuDriverGetVersion());
        }
    }

    private void ensureComputeCapabilityAvailable() {
        if (computeCapability == null) {
            int major = cuDeviceGetAttribute(index, PTXDeviceAttribute.COMPUTE_CAPABILITY_MAJOR.value());
            int minor = cuDeviceGetAttribute(index, PTXDeviceAttribute.COMPUTE_CAPABILITY_MINOR.value());
            computeCapability = new CUDAComputeCapability(major, minor);
        }
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
            localMemorySize = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_SHARED_MEMORY_PER_BLOCK.value());
        }
        return localMemorySize;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (noOfWorkUnits == INIT_VAL) {
            noOfWorkUnits = cuDeviceGetAttribute(index, PTXDeviceAttribute.MULTIPROCESSOR_COUNT.value());
        }
        return noOfWorkUnits;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        if (maxWorkItemSizes == null) {
            maxWorkItemSizes = new long[3];
            maxWorkItemSizes[0] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_BLOCK_DIM_X.value());
            maxWorkItemSizes[1] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_BLOCK_DIM_Y.value());
            maxWorkItemSizes[2] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_BLOCK_DIM_Z.value());
        }

        return maxWorkItemSizes;
    }

    public int[] getDeviceMaxGridSizes() {
        if (maxGridSizes == null) {
            maxGridSizes = new int[3];
            maxGridSizes[0] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_GRID_DIM_X.value());
            maxGridSizes[1] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_GRID_DIM_Y.value());
            maxGridSizes[2] = cuDeviceGetAttribute(index, PTXDeviceAttribute.MAX_GRID_DIM_Z.value());
        }

        return maxGridSizes;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (maxFrequency == INIT_VAL) {
            maxFrequency = cuDeviceGetAttribute(index, PTXDeviceAttribute.CLOCK_RATE.value());
        }
        return maxFrequency;
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        if (constantBufferSize == INIT_VAL) {
            constantBufferSize = cuDeviceGetAttribute(index, PTXDeviceAttribute.TOTAL_CONSTANT_MEMORY.value());
        }
        return constantBufferSize;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return cuMemGetInfo(index);
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

    public PTXContext getContext() {
        return context;
    }

    public TornadoDeviceType getDeviceType() {
        return TornadoDeviceType.GPU;
    }

    public String getTargetArchitecture() {
        if (targetArchitecture == null) {
            ensurePTXVersionAvailable();
            ensureComputeCapabilityAvailable();

            // TODO: Maybe this should be in a separate class?
            CUDAComputeCapability archVersion = ptxVersion.getArch(computeCapability);
            targetArchitecture = String.format("sm_%d%d", archVersion.major, archVersion.minor);
        }
        return targetArchitecture;
    }

    public String getTargetPTXVersion() {
        ensurePTXVersionAvailable();
        return ptxVersion.toString();
    }
}
