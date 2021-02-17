package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueueGroupPropertyFlags {

    public static final int ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE = ZE_BIT(0);
    public static final int ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY = ZE_BIT(1);
    public static final int ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS = ZE_BIT(2);
    public static final int ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS = ZE_BIT(3);
    public static final int ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_FORCE_UINT32 = 0x7fffffff;

    public static int ZE_BIT(int value) {
        return 1 << value;
    }
}
