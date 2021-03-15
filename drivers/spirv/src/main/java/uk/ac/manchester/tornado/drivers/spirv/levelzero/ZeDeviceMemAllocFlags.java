package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeDeviceMemAllocFlags {
    /**
     * Device should cache allocation
     */
    public static final int ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_CACHED = ZeConstants.ZE_BIT(0);

    /**
     * device should not cache allocation (UC)
     */
    public static final int ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED = ZeConstants.ZE_BIT(1);

    public static final int ZE_DEVICE_MEM_ALLOC_FLAG_FORCE_UINT32 = 0x7fffffff;
}
