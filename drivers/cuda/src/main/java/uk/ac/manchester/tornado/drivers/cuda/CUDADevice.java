package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class CUDADevice extends TornadoLogger implements TornadoTargetDevice {

    private int index;
    private String name;
    private CUDAContext context;

    public CUDADevice(int index) {
        this.index = index;
        context = new CUDAContext(this);
    }

    native static String cuDeviceGetName(int deviceId);

    public long getId() {
        return 1;
    }

    @Override public String getDeviceName() {
        if (name == null) name = cuDeviceGetName(index);
        return name;
    }

    @Override public long getDeviceGlobalMemorySize() {
        return 0;
    }

    @Override public long getDeviceLocalMemorySize() {
        return 0;
    }

    @Override public int getDeviceMaxComputeUnits() {
        return 0;
    }

    @Override public long[] getDeviceMaxWorkItemSizes() {
        return new long[0];
    }

    @Override public int getDeviceMaxClockFrequency() {
        return 0;
    }

    @Override public long getDeviceMaxConstantBufferSize() {
        return 0;
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
