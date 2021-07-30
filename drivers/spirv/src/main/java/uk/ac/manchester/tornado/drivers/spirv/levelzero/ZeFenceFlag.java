package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeFenceFlag {

    /**
     * Fence is created in the signaled state, otherwise not signaled.
     */
    public static final int ZE_FENCE_FLAG_SIGNALED = ZeConstants.ZE_BIT(0);      ///< only initialize GPU drivers

    public static final int ZE_INIT_FLAG_FORCE_UINT32 = 0x7fffffff;
}
