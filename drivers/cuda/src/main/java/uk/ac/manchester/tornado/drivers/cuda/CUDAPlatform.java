package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CUDAPlatform extends TornadoLogger {
    private final CUDADevice[] devices;

    public CUDAPlatform() {
        devices = new CUDADevice[cuDeviceGetCount()];

        for (int i = 0; i < devices.length; i++) {
            devices[i] = new CUDADevice(i);
        }
    }

    //native static String clGetPlatformInfo(long id, int info);

    public native static int cuDeviceGetCount();

    //native static int clGetDeviceIDs(long id, long type, long[] devices);

    //native static long clCreateContext(long platform, long[] devices) throws Exception;

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

        sb.append(String.format("name=CUDA-PTX, num. devices=%d" , devices.length));

        return sb.toString().trim();
    }

    public int getDeviceCount() {
        return devices.length;
    }

    public CUDADevice getDevice(int deviceIndex) {
        return devices[deviceIndex];
    }
}
