package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeHostMemAllocDesc {

    private int stype;
    private long pNext;
    private long flags;

    private long ptrZeHostMemAllocDesc;

    public ZeHostMemAllocDesc() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_HOST_MEM_ALLOC_DESC;
        this.ptrZeHostMemAllocDesc = -1;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getFlags() {
        return flags;
    }

    public long getPtrZeHostMemAllocDesc() {
        return ptrZeHostMemAllocDesc;
    }
}
