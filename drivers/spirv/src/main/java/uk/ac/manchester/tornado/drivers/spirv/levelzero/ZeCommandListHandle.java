package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandListHandle {

    private long ptrZeCommandListHandle;

    public ZeCommandListHandle() {
        this.ptrZeCommandListHandle = -1;
    }

    public long getPtrZeCommandListHandle() {
        return ptrZeCommandListHandle;
    }

    public void initPtr() {
        this.ptrZeCommandListHandle = -1;
    }
}
