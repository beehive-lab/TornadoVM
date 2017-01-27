package tornado.drivers.opencl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.enums.OCLBuildStatus;
import tornado.drivers.opencl.enums.OCLProgramBuildInfo;
import tornado.drivers.opencl.enums.OCLProgramInfo;
import tornado.drivers.opencl.exceptions.OCLException;

public class OCLProgram extends TornadoLogger {

    private final long id;
    private final OCLDeviceContext deviceContext;
    private final long[] devices;
    private final List<OCLKernel> kernels;
    private final ByteBuffer buffer;

    public OCLProgram(long id, OCLDeviceContext deviceContext) {
        this.id = id;
        this.deviceContext = deviceContext;
        this.devices = new long[]{deviceContext.getDeviceId()};
        this.kernels = new ArrayList<OCLKernel>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(OpenCL.BYTE_ORDER);
    }

//	static {
//		System.loadLibrary(OpenCL.OPENCL_LIBRARY);
//	}
    native static void clReleaseProgram(long programId) throws OCLException;

    native static void clBuildProgram(long programId, long[] devices,
            String options) throws OCLException;

    native static void clGetProgramInfo(long programId, int param, byte[] buffer)
            throws OCLException;

    native static void clGetProgramBuildInfo(long programId, long deviceId,
            int param, byte[] buffer) throws OCLException;

    native static long clCreateKernel(long programId, String name)
            throws OCLException;

    native static void getBinaries(long programId, long numDevices, byte[] buffer) throws OCLException;

    public OCLBuildStatus getStatus(long deviceId) {
        OCLBuildStatus result = OCLBuildStatus.CL_BUILD_UNKNOWN;
        buffer.clear();
        try {
            clGetProgramBuildInfo(id, deviceId,
                    OCLProgramBuildInfo.CL_PROGRAM_BUILD_STATUS.getValue(),
                    buffer.array());
            result = OCLBuildStatus.toEnum(buffer.getInt());
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return result;
    }

    public String getBuildLog(long deviceId) {
        String result = "";
        buffer.clear();
        try {
            clGetProgramBuildInfo(id, deviceId,
                    OCLProgramBuildInfo.CL_PROGRAM_BUILD_STATUS.getValue(),
                    buffer.array());

            result = new String(buffer.array(), "ASCII");
        } catch (OCLException | UnsupportedEncodingException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        result = result.substring(0, result.indexOf('\0'));
        //System.out.println(result);
        return result;
    }

    public void build(String options) {
        buffer.clear();

        try {
            clBuildProgram(id, devices, options);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        for (long device : devices) {
            if (getStatus(device) != OCLBuildStatus.CL_BUILD_SUCCESS) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("build: device=0x%x, status=%s:\n",
                        device, getStatus(device)));
                sb.append(getBuildLog(device));
                error(sb.toString().trim());
            }
        }
    }

    public void cleanup() {
        try {
            for (OCLKernel kernel : kernels) {
                kernel.cleanup();
            }

            clReleaseProgram(id);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(id,
                    OCLProgramInfo.CL_PROGRAM_NUM_DEVICES.getValue(),
                    buffer.array());
            result = buffer.getInt();
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public long[] getBinarySizes() {
        final int numDevices = getNumDevices();
        long result[] = new long[numDevices];
        buffer.clear();
        try {
            clGetProgramInfo(id,
                    OCLProgramInfo.CL_PROGRAM_BINARY_SIZES.getValue(),
                    buffer.array());
            for (int i = 0; i < numDevices; i++) {
                result[i] = buffer.getLong();
            }
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public void dumpBinaries(String filenamePrefix) {
        final int numDevices = getNumDevices();
        TornadoInternalError.guarantee(numDevices == 1, "dumping binaries for multiple devices: %s (%d devices)", filenamePrefix, numDevices);
        final long[] sizes = getBinarySizes();

        int totalSize = 0;
        for (long size : sizes) {
            totalSize += size;
        }

        final ByteBuffer binary = ByteBuffer.allocate(totalSize);
        try {
            getBinaries(id, numDevices, binary.array());
        } catch (OCLException e1) {
            e1.printStackTrace();
        }

        for (int i = 0; i < numDevices; i++) {
            try {
                info("dumping binary %s", filenamePrefix);
                FileOutputStream fis = new FileOutputStream(filenamePrefix);
                FileChannel vChannel = fis.getChannel();

                vChannel.write(binary);
                vChannel.close();
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("program: id=0x%x, num devices=%d\n", id,
                devices.length));
        for (long device : devices) {
            sb.append(String.format("device: id=0x%x, status=%s\n", device,
                    getStatus(device)));
        }

        return sb.toString();
    }

    public OCLKernel getKernel(String entryPoint) {
        OCLKernel kernel = null;
        try {
            kernel = new OCLKernel(clCreateKernel(id, entryPoint),
                    deviceContext);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        // final long[] sizes = getBinarySizes();
        debug("Num devices: %d", numDevices);
        for (int i = 0; i < numDevices; i++) {
            // debug("size[%d]: %s", i,
            // RuntimeUtilities.humanReadableByteCount(sizes[i], true));
            // dumpBinaries("./binaries/simple");
        }
    }

}
