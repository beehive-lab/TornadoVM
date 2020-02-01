package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class CUDADevice extends TornadoLogger implements TornadoTargetDevice {

    private int index;

    public CUDADevice(int index, long id) {
        this.index = index;
    }

    public long getId() {
        return 1;
    }

    @Override public String getDeviceName() {
        return "NVIDIA GPU";
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
}
