package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueueListHandle {

    private long ptrZeCommandListHandle;

    public ZeCommandQueueListHandle() {
        this.ptrZeCommandListHandle = -1;
    }

    public long getPtrZeCommandListHandle() {
        return ptrZeCommandListHandle;
    }

    public void initPtr() {
        this.ptrZeCommandListHandle = -1;
    }
}
