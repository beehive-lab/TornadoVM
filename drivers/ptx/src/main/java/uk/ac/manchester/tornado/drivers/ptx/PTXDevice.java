package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.ptx.enums.PTXDeviceAttribute;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class PTXDevice extends TornadoLogger implements TornadoTargetDevice {

    private static final int INIT_VAL = -1;

    private final String name;
    private final long[] maxGridSizes;
    private final PTXContext context;
    private final PTXVersion ptxVersion;
    private final long[] maxWorkItemSizes;
    private final TargetArchitecture targetArchitecture;
    private final CUDAComputeCapability computeCapability;

    private final int deviceIndex;
    private final long cuDevice;
    private final int maxFrequency;
    private final int numComputeUnits;
    private final long localMemorySize;
    private final long totalDeviceMemory;
    private final long constantBufferSize;
    private final long maxAllocationSize;

    public PTXDevice(int deviceIndex) {
        this.deviceIndex = deviceIndex;
        cuDevice = cuDeviceGet(deviceIndex);
        name = cuDeviceGetName(cuDevice);
        constantBufferSize = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.TOTAL_CONSTANT_MEMORY.value());
        totalDeviceMemory = cuDeviceTotalMem(cuDevice);
        localMemorySize = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_SHARED_MEMORY_PER_BLOCK.value());
        numComputeUnits = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MULTIPROCESSOR_COUNT.value());
        maxFrequency = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.CLOCK_RATE.value());
        maxWorkItemSizes = initMaxWorkItemSizes();
        maxGridSizes = initMaxGridSizes();
        ptxVersion = CUDAVersion.getMaxPTXVersion(cuDriverGetVersion());
        computeCapability = initComputeCapability();
        targetArchitecture = ptxVersion.getArchitecture(computeCapability);

        context = new PTXContext(this);
        // CUcontext for the CUDevice must be created first before cuMemGetInfo returns a valid value.
        maxAllocationSize = cuMemGetInfo();
    }

    private native static long cuDeviceGet(int deviceId);
    private native static String cuDeviceGetName(long cuDevice);
    private native static int cuDeviceGetAttribute(long cuDevice, int attribute);
    private native static long cuDeviceTotalMem(long cuDevice);
    private native static long cuMemGetInfo();
    private native static int cuDriverGetVersion();

    @Override
    public String getDeviceName() {
        return name;
    }

    private CUDAComputeCapability initComputeCapability() {
        int major = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.COMPUTE_CAPABILITY_MAJOR.value());
        int minor = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.COMPUTE_CAPABILITY_MINOR.value());
        return new CUDAComputeCapability(major, minor);
    }

    public CUDAComputeCapability getComputeCapability() {
        return computeCapability;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return totalDeviceMemory;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return localMemorySize;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        return numComputeUnits;
    }

    private long[] initMaxWorkItemSizes() {
        long[] maxWorkItemSizes = new long[3];
        maxWorkItemSizes[0] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_X.value());
        maxWorkItemSizes[1] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_Y.value());
        maxWorkItemSizes[2] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_Z.value());
        return maxWorkItemSizes;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    private long[] initMaxGridSizes() {
        long[] maxGridSizes = new long[3];
        maxGridSizes[0] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_X.value());
        maxGridSizes[1] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_Y.value());
        maxGridSizes[2] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_Z.value());
        return maxGridSizes;
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return maxGridSizes;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return maxFrequency;
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        return constantBufferSize;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return maxAllocationSize;
    }

    @Override
    public Object getDeviceInfo() {
        return getDeviceName();
    }

    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public PTXContext getPTXContext() {
        return context;
    }

    public TornadoDeviceType getDeviceType() {
        return TornadoDeviceType.GPU;
    }

    public TargetArchitecture getTargetArchitecture() {
        return targetArchitecture;
    }

    public String getTargetPTXVersion() {
        return ptxVersion.toString();
    }

    public long getCuDevice() {
        return cuDevice;
    }
}
