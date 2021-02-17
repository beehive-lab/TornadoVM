package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * It keeps all device pointers associated with one driver
 */
public class ZeDevicesHandle {

    private long[] devicePtr;
    private int numDevices;

    public ZeDevicesHandle(int numDevices) {
        this.numDevices = numDevices;
        this.devicePtr = new long[numDevices];
    }

    public long[] getDevicePointers() {
        return this.devicePtr;
    }

    public long getDevicePtrAtIndex(int index) {
        return this.devicePtr[index];
    }

    public int getNumDevices() {
        return numDevices;
    }
}
