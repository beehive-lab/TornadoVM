package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandListDescription {

    private int stype;
    private long pNext;
    private long commandQueueGroupOrdinal;
    private int flags;

    private long ptrZeCommandListDescription;

    public ZeCommandListDescription() {
        this.ptrZeCommandListDescription = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getCommandQueueGroupOrdinal() {
        return commandQueueGroupOrdinal;
    }

    public int getFlags() {
        return flags;
    }

    public long getPtrZeCommandListDescription() {
        return ptrZeCommandListDescription;
    }

    public void setCommandQueueGroupOrdinal(long ordinal) {
        this.commandQueueGroupOrdinal = ordinal;
    }
}

