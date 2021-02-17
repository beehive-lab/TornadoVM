package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeEventPoolFlags {

    public static final int ZE_EVENT_POOL_FLAG_HOST_VISIBLE =  ZeConstants.ZE_BIT(0);    //< signals and waits are also visible to host
    public static final int ZE_EVENT_POOL_FLAG_IPC = ZeConstants.ZE_BIT(1);              //< signals and waits may be shared across processes
    public static final int ZE_EVENT_POOL_FLAG_KERNEL_TIMESTAMP = ZeConstants.ZE_BIT(2); //< Indicates all events in pool will contain kernel timestamps; cannot be
                                                                                               //< combined with ::ZE_EVENT_POOL_FLAG_IPC
    public static final int ZE_EVENT_POOL_FLAG_FORCE_UINT32 = 0x7fffffff;

}
