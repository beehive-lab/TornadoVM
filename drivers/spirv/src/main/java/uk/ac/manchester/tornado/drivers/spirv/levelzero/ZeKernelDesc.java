package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeKernelDesc {

    private int stype;
    private long pNext;
    private long flags;
    private String kernelName;

    private long ptrZeKernelDesc;

    public ZeKernelDesc() {
        pNext = -1;
        stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_KERNEL_DESC;
    }

    public void setType(int stype) {
        this.stype = stype;
    }

    public void setNext(long pNext) {
        this.pNext = pNext;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public void setKernelName(String kernelName) {
        this.kernelName = kernelName;
    }

    public int getType() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getFlags() {
        return flags;
    }

    public String getKernelName() {
        return kernelName;
    }
}
