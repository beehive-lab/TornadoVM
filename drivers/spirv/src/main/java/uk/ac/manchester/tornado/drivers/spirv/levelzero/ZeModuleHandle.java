package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeModuleHandle {

    private long ptrZeModuleHandle;

    public ZeModuleHandle() {
        this.ptrZeModuleHandle = -1;
    }

    public long getPtrZeModuleHandle() {
        return ptrZeModuleHandle;
    }
}
