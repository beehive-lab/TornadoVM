package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeEventDescription {

    private int stype;
    private long pNext;

    private long index;
    private int signal;
    private int wait;

    private long ptrZeEventDescription;

    public ZeEventDescription() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_DESC;
        this.ptrZeEventDescription = -1;
    }

    public void setStype(int stype) {
        this.stype = stype;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }

    public void setWait(int wait) {
        this.wait = wait;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getIndex() {
        return index;
    }

    public int getSignal() {
        return signal;
    }

    public int getWait() {
        return wait;
    }

    public long getPtrZeEventDescription() {
        return ptrZeEventDescription;
    }
}
