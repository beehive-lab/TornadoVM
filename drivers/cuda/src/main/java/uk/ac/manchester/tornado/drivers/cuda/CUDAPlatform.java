package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAPlatform extends TornadoLogger {
    private final CUDADevice[] devices;

    public CUDAPlatform() {
        devices = new CUDADevice[cuDeviceGetCount()];

        for (int i = 0; i < devices.length; i++) {
            devices[i] = new CUDADevice(i);
        }
    }

    public native static int cuDeviceGetCount();

    public void cleanup() {
        for (CUDADevice device : devices) {
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

    public CUDADevice getDevice(int deviceIndex) {
        return devices[deviceIndex];
    }

    public String getName() {
        return "CUDA-PTX";
    }
}
