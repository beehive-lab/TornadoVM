package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeHostMemAllocFlags {

    /**
     * Host should cache allocation
     */
    public static final int ZE_HOST_MEM_ALLOC_FLAG_BIAS_CACHED = ZeConstants.ZE_BIT(0);

    /**
     * host should not cache allocation (UC)
     */
    public static final int ZE_HOST_MEM_ALLOC_FLAG_BIAS_UNCACHED = ZeConstants.ZE_BIT(1);

    /**
     * Host memory should be allocated write-combined (WC)
     */
    public static final int ZE_HOST_MEM_ALLOC_FLAG_BIAS_WRITE_COMBINED = ZeConstants.ZE_BIT(2);

    public static final int ZE_HOST_MEM_ALLOC_FLAG_FORCE_UINT32 = 0x7fffffff;
}
