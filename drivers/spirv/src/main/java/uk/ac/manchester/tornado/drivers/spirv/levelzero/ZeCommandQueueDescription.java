package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueueDescription {

    private int stype;
    private long pNext;
    private long ordinal;
    private long index;

    private int flags;
    private int mode;
    private int priority;

    private long ptrZeCommandDescription;

    public ZeCommandQueueDescription() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_COMMAND_QUEUE_DESC;
        this.ptrZeCommandDescription = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getOrdinal() {
        return ordinal;
    }

    public long getIndex() {
        return index;
    }

    public int getFlags() {
        return flags;
    }

    public int getMode() {
        return mode;
    }

    public int getPriority() {
        return priority;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }
}
