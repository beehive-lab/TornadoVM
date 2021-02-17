package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeKernelHandle {

    private long ptrZeKernelHandle;

    public ZeKernelHandle() {
        this.ptrZeKernelHandle = -1;
    }

    public long getPtrZeKernelHandle() {
        return ptrZeKernelHandle;
    }
}
