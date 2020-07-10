package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXPlatform extends TornadoLogger {
    private final PTXDevice[] devices;

    public PTXPlatform() {
        devices = new PTXDevice[cuDeviceGetCount()];

        for (int i = 0; i < devices.length; i++) {
            devices[i] = new PTXDevice(i);
        }
    }

    public native static int cuDeviceGetCount();

    public void cleanup() {
        for (PTXDevice device : devices) {
            if (device != null) {
                device.getContext().cleanup();
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("name=%s, num. devices=%d" ,getName(), devices.length));

        return sb.toString().trim();
    }

    public int getDeviceCount() {
        return devices.length;
    }

    public PTXDevice getDevice(int deviceIndex) {
        return devices[deviceIndex];
    }

    public String getName() {
        return "CUDA-PTX";
    }
}
