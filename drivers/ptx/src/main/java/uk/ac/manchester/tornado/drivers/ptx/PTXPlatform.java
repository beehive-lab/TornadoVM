package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXPlatform extends TornadoLogger {
    private final PTXDevice[] devices;

    public PTXPlatform() {
        devices = new PTXDevice[cuDeviceGetCount()];

        if (devices.length == 0) {
            throw new TornadoBailoutRuntimeException("[WARNING] No CUDA devices found. Deoptimizing to sequential execution.");
        }

        for (int i = 0; i < devices.length; i++) {
            devices[i] = new PTXDevice(i);
        }
    }

    public native static int cuDeviceGetCount();

    public void cleanup() {
        for (PTXDevice device : devices) {
            if (device != null) {
                device.getPTXContext().cleanup();
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
        if (deviceIndex >= devices.length) {
            throw new TornadoBailoutRuntimeException("[ERROR] Device index is invalid " + deviceIndex);
        }
        return devices[deviceIndex];
    }

    public String getName() {
        return "PTX";
    }
}
