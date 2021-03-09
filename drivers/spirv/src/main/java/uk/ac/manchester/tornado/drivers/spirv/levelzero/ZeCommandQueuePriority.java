package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueuePriority {
    /**
     * [default] normal priority
     */
    public static final int ZE_COMMAND_QUEUE_PRIORITY_NORMAL = 0;

    /**
     * lower priority than normal
     */
    public static final int ZE_COMMAND_QUEUE_PRIORITY_PRIORITY_LOW = 1;

    /**
     * higher priority than normal
     */
    public static final int ZE_COMMAND_QUEUE_PRIORITY_PRIORITY_HIGH = 2;

    public static final int ZE_COMMAND_QUEUE_PRIORITY_FORCE_UINT32 = 0x7fffffff;
}
