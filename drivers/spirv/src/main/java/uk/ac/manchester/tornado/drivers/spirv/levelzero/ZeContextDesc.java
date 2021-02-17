package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeContextDesc {

    private int type;
    private long pNext;
    private int flags;
    private long nativePointer;

    public ZeContextDesc() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_CONTEXT_DESC;
        this.nativePointer = -1;
    }

    public long getNativePointer() {
        return nativePointer;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getType() {
        return type;
    }

}
