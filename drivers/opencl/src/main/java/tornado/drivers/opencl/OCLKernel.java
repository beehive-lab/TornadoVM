package tornado.drivers.opencl;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLKernelInfo;
import tornado.drivers.opencl.exceptions.OCLException;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

public class OCLKernel extends TornadoLogger {

    private final long id;
    private final OCLDeviceContext deviceContext;
    private final ByteBuffer buffer;
    private String kernelName;

    public OCLKernel(long id, OCLDeviceContext deviceContext) {
        this.id = id;
        this.deviceContext = deviceContext;
        this.buffer = ByteBuffer.allocate(1024);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.kernelName = "unknown";

        queryName();

    }

    native static void clReleaseKernel(long kernelId) throws OCLException;

    native static void clSetKernelArg(long kernelId, int index, long size,
            byte[] buffer) throws OCLException;

    native static void clGetKernelInfo(long kernelId, int info, byte[] buffer) throws OCLException;

    public void setArg(int index, ByteBuffer buffer) {
        try {
            clSetKernelArg(id, index, buffer.position(), buffer.array());
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void setArgUnused(int index) {
        try {
            clSetKernelArg(id, index, 8, null);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void setConstantRegion(int index, ByteBuffer buffer) {
        long maxSize = deviceContext.getDevice().getMaxConstantBufferSize();
        guarantee(buffer.position() <= maxSize, "constant buffer is too large for device");
        setArg(index, buffer);
    }

    public void setLocalRegion(int index, long size) {
        long maxSize = deviceContext.getDevice().getLocalMemorySize();
        guarantee(size <= maxSize, "local allocation is too large for device");
        try {
            clSetKernelArg(id, index, size, null);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            clReleaseKernel(id);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return kernelName;
    }

    private void queryName() {
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.clear();
        try {
            clGetKernelInfo(id, OCLKernelInfo.CL_KERNEL_FUNCTION_NAME.getValue(),
                    buffer.array());
            kernelName = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException | OCLException e) {
            e.printStackTrace();
        }
    }

    public long getId() {
        return id;
    }
}
