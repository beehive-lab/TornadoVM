package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeEventHandle {

    private long ptrZeEventHandle;

    public ZeEventHandle() {
        this.ptrZeEventHandle = -1;
    }

    public long getPtrZeEventHandle() {
        return this.ptrZeEventHandle;
    }

    public void initPtr() {
        this.ptrZeEventHandle = -1;
    }
}
