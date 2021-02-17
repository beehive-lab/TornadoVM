package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeDeviceMemAllocDesc {

    private int stype;
    private long pNext;
    private long flags;
    private long ordinal;

    private long ptrZeDeviceMemAllocDesc;

    public ZeDeviceMemAllocDesc() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEM_ALLOC_DESC;
        this.ptrZeDeviceMemAllocDesc = -1;
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

    public long getOrdinal() {
        return ordinal;
    }

    public long getPtrZeDeviceMemAllocDesc() {
        return ptrZeDeviceMemAllocDesc;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }
}
