package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueueGroupProperties {

    private int type;
    private long pNext;
    private int flags;
    private int maxMemoryFillPatternSize;
    private int numQueues;

    public ZeCommandQueueGroupProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_COMMAND_QUEUE_GROUP_PROPERTIES;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append(" Command Group Properties \n");
        builder.append("=========================\n");
        builder.append("Type        : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext       : " + pNext + "\n");
        builder.append("flags       : " + ZeUtils.zeCommandQueueGroupFlagsToString(flags) + "\n");
        builder.append("maxMemoryFillPatternSize: " + maxMemoryFillPatternSize + "\n");
        builder.append("numQueues : " + numQueues + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public int getFlags() {
        return flags;
    }

    public int getMaxMemoryFillPatternSize() {
        return maxMemoryFillPatternSize;
    }

    public int getNumQueues() {
        return numQueues;
    }
}
