package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeMemoryProperties {

    private int type;
    private long pNext;

    private long flags;
    private long maxClockRate;
    private long maxBusWidth;
    private long totalSize;
    private String name;

    private long ptrZeMemoryProperty;

    public ZeMemoryProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Memory Properties\n");
        builder.append("=========================\n");
        builder.append("Type        : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext       : " + pNext + "\n");
        builder.append("flags       : " + ZeUtils.zeFlagsToString(flags) + "\n");
        builder.append("maxClockRate: " + maxClockRate + "\n");
        builder.append("maxBusWidth : " + maxBusWidth + "\n");
        builder.append("totalSize   : " + totalSize + "\n");
        builder.append("name        : " + name + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public long getFlags() {
        return flags;
    }

    public long getMaxClockRate() {
        return maxClockRate;
    }

    public long getMaxBusWidth() {
        return maxBusWidth;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public String getName() {
        return name;
    }

    public long getPtrZeMemoryProperty() {
        return ptrZeMemoryProperty;
    }
}
