package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeMemoryAccessProperties {

    private int type;
    private long pNext;

    private long hostAllocCapabilities;
    private long deviceAllocCapabilities;
    private long sharedSingleDeviceAllocCapabilities;
    private long sharedCrossDeviceAllocCapabilities;
    private long sharedSystemAllocCapabilities;

    public ZeMemoryAccessProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEMORY_ACCESS_PROPERTIES;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Memory Access Properties\n");
        builder.append("=========================\n");
        builder.append("Type                : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext               : " + pNext + "\n");
        builder.append("hostAllocCapabilities               : " + ZeUtils.zeMemporyAccessCapToString(hostAllocCapabilities) + "\n");
        builder.append("deviceAllocCapabilities             : " + ZeUtils.zeMemporyAccessCapToString(deviceAllocCapabilities) + "\n");
        builder.append("sharedSingleDeviceAllocCapabilities : " + ZeUtils.zeMemporyAccessCapToString(sharedSingleDeviceAllocCapabilities) + "\n");
        builder.append("sharedCrossDeviceAllocCapabilities  : " + ZeUtils.zeMemporyAccessCapToString(sharedCrossDeviceAllocCapabilities) + "\n");
        builder.append("sharedSystemAllocCapabilities       : " + ZeUtils.zeMemporyAccessCapToString(sharedSystemAllocCapabilities) + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public long getHostAllocCapabilities() {
        return hostAllocCapabilities;
    }

    public long getDeviceAllocCapabilities() {
        return deviceAllocCapabilities;
    }

    public long getSharedSingleDeviceAllocCapabilities() {
        return sharedSingleDeviceAllocCapabilities;
    }

    public long getSharedCrossDeviceAllocCapabilities() {
        return sharedCrossDeviceAllocCapabilities;
    }

    public long getSharedSystemAllocCapabilities() {
        return sharedSystemAllocCapabilities;
    }
}
