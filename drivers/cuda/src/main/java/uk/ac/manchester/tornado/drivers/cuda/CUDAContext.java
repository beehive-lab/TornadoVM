package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.ArrayList;
import java.util.List;

public class CUDAContext extends TornadoLogger {

    private final CUDADevice device;
    private final CUDADeviceContext deviceContext;

    public CUDAContext(CUDADevice device) {
        this.device = device;

        cuCtxCreate(device.getIndex());

        deviceContext = new CUDADeviceContext(device, this);
    }

    public native static void cuCtxCreate(int deviceIndex);

    public native static void cuCtxDestroy(int deviceIndex);


    public void cleanup() {
        cuCtxDestroy(device.getIndex());
    }

    public CUDADeviceContext getDeviceContext() {
        return deviceContext;
    }
}
