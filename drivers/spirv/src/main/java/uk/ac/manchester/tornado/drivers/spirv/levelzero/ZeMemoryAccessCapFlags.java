package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeMemoryAccessCapFlags {

    public static final int ZE_MEMORY_ACCESS_CAP_FLAG_RW = ZeConstants.ZE_BIT(0);
    public static final int ZE_MEMORY_ACCESS_CAP_FLAG_ATOMIC = ZeConstants.ZE_BIT(1);
    public static final int ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT = ZeConstants.ZE_BIT(2);
    public static final int ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC = ZeConstants.ZE_BIT(3);
    public static final int ZE_MEMORY_ACCESS_CAP_FLAG_FORCE_UINT32 = 0x7fffffff;

    private ZeMemoryAccessCapFlags() {

    }
}
