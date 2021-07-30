package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Fence descriptor
 */
public class ZeFenceDesc {

    private int stype;
    private long pNext;
    private int flags;

    private long ptrZeFenceDesc;

    public ZeFenceDesc() {
        pNext = -1;
        stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_FENCE_DESC;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public long getPtrZeFenceDesc() {
        return ptrZeFenceDesc;
    }
}
