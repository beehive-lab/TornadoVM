package tornado.drivers.opencl;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.enums.OCLPlatformInfo;
import tornado.drivers.opencl.exceptions.OCLException;

public class OCLPlatform extends TornadoLogger {

    private final int index;
    private final long id;
    private final List<OCLDevice> devices;
    private final Set<OCLContext> contexts;

    public OCLPlatform(int index, long id) {
        this.index = index;
        this.id = id;
        this.devices = new ArrayList<>();
        this.contexts = new HashSet<>();

        final int deviceCount = clGetDeviceCount(id,
                OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue());

        final long[] ids = new long[deviceCount];
        clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        for (int i = 0; i < ids.length; i++) {
            devices.add(new OCLDevice(i, ids[i]));
        }

    }

    native static String clGetPlatformInfo(long id, int info);

    native static int clGetDeviceCount(long id, long type);

    native static int clGetDeviceIDs(long id, long type, long[] devices);

    native static long clCreateContext(long platform, long[] devices)
            throws OCLException;

    public OCLContext createContext() {
        OCLContext result = null;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        for (OCLDevice device : devices) {
            deviceIds.put(device.getId());
        }

        try {
            long contextId = clCreateContext(id, deviceIds.array());
            result = new OCLContext(this, contextId, devices);
            contexts.add(result);
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public void cleanup() {
        for (OCLContext context : contexts) {
            context.cleanup();
        }
    }

    public String getProfile() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    public String getVersion() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("name=%s, num. devices=%d, ", getName(),
                devices.size()));
        sb.append(String.format("version=%s", getVersion()));

        return sb.toString().trim();
    }

    public int getIndex() {
        return index;
    }

}
