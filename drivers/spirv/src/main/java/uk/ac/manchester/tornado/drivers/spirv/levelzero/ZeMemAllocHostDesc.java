package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeMemAllocHostDesc {

    private int stype;
    private long pNext;
    private long flags;

    private long ptrZeHostMemAllocDesc;

    public ZeMemAllocHostDesc() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_HOST_MEM_ALLOC_DESC;
        this.ptrZeHostMemAllocDesc = -1;
        this.pNext = -1;
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

    public void setFlags(int flags) {
        this.flags = flags;
    }
}