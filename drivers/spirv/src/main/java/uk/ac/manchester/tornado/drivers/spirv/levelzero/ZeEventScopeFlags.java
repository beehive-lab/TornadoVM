package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeEventScopeFlags {

    /**
     * Cache hierarchies are flushed or invalidated sufficient for local sub-device access
     */
    public static final int ZE_EVENT_SCOPE_FLAG_SUBDEVICE = ZeConstants.ZE_BIT(0);

    /**
     * Cache hierarchies are flushed or invalidated sufficient for global device access and peer device access
     */
    public static final int  ZE_EVENT_SCOPE_FLAG_DEVICE = ZeConstants.ZE_BIT(1);

    /**
     * Cache hierarchies are flushed or invalidated sufficient for device and host access
     */
    public static final int ZE_EVENT_SCOPE_FLAG_HOST = ZeConstants.ZE_BIT(2);

    public static final int ZE_EVENT_SCOPE_FLAG_FORCE_UINT32 = 0x7fffffff;

}
