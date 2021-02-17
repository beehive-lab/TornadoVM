package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Event Pool Descriptor
 */
public class ZeEventPoolDescription {

    private int stype;
    private long pNext;
    private int flags;

    private int count;

    private long ptrZeEventPoolDescription;

    public ZeEventPoolDescription() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_POOL_DESC;
        this.ptrZeEventPoolDescription = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public long getPtrZeEventPoolDescription() {
        return ptrZeEventPoolDescription;
    }
}
