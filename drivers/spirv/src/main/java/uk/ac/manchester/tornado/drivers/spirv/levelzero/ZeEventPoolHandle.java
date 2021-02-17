package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeEventPoolHandle {

    private long ptrZeEventPoolHandle;

    public ZeEventPoolHandle() {
        this.ptrZeEventPoolHandle = -1;
    }

    public long getPtrZeEventPoolHandle() {
        return this.ptrZeEventPoolHandle;
    }

    public void initPtr() {
        this.ptrZeEventPoolHandle = -1;
    }
}
