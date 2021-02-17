package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeDeviceCacheProperties {

    private int type;
    private long pNext;
    private long flags;
    private int cacheSize;


    public ZeDeviceCacheProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_CACHE_PROPERTIES;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=============================\n");
        builder.append("Device Cache Memory Properties\n");
        builder.append("=============================\n");
        builder.append("Type                : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext               : " + pNext + "\n");
        builder.append("flags               : " + ZeUtils.zeMemporyAccessCapToString(flags) + "\n");
        builder.append("cacheSize           : " + cacheSize + "\n");
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

    public int getCacheSize() {
        return cacheSize;
    }
}
