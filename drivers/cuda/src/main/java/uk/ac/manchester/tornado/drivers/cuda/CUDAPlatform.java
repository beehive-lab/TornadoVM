package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CUDAPlatform extends TornadoLogger {
    private final int index;
    private final long id;
    private final List<CUDADevice> devices;
    private final Set<CUDAContext> contexts;

    public CUDAPlatform(int index, long id) {
        this.index = index;
        this.id = id;
        this.devices = new ArrayList<>();
        this.contexts = new HashSet<>();

        final int deviceCount = 1;

        final long[] ids = new long[]{1};
        for (int i = 0; i < ids.length; i++) {
            devices.add(new CUDADevice(i, ids[i]));
        }

    }

    native static String clGetPlatformInfo(long id, int info);

    native static int clGetDeviceCount(long id, long type);

    native static int clGetDeviceIDs(long id, long type, long[] devices);

    native static long clCreateContext(long platform, long[] devices) throws Exception;

    public CUDAContext createContext() {
        CUDAContext contextObject = null;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        for (CUDADevice device : devices) {
            deviceIds.put(device.getId());
        }

        try {
            //long contextId = clCreateContext(id, deviceIds.array());
            contextObject = new CUDAContext(this, 1 /*contextId*/, devices);
            contexts.add(contextObject);
        } catch (Exception e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return contextObject;
    }

    public void cleanup() {
        for (CUDAContext context : contexts) {
            if (context != null) {
                context.cleanup();
            }
        }
    }

    public String getProfile() {
        return ""; //clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    public String getVersion() {

        return "0.0.0"; //clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return "CUDA platform"; //clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {

        return "NVIDIA"; //clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {

        return ""; //clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("name=%s, num. devices=%d, ", getName(), devices.size()));
        sb.append(String.format("version=%s", getVersion()));

        return sb.toString().trim();
    }

    public int getIndex() {
        return index;
    }
}
