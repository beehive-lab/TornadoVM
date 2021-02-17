package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Set of utilities ported from zello_log.h (Level Zero)
 */
public class ZeUtils {

    public static long ZE_DEVICE_MEMORY_PROPERTY_FLAG_TBD = ZeConstants.ZE_BIT(0);

    public static String zeTypeToString(int type) {
        switch (type) {
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES";
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_COMPUTE_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_COMPUTE_PROPERTIES";
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_IMAGE_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_IMAGE_PROPERTIES";
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES";
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_CACHE_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_CACHE_PROPERTIES";
        }
        return null;
    }

    public static String zeFlagsToString(long flags) {
        String str = "";
        if (0 == flags) {
            str += "0 | ";
        }
        if ((ZE_DEVICE_MEMORY_PROPERTY_FLAG_TBD & flags) == ZE_DEVICE_MEMORY_PROPERTY_FLAG_TBD) {
            str += "MEMORY_PROPERTY_FLAG_TBD | ";
        }
        return (str.length() > 3)
                ? "Device::{ " + str.substring(0, str.length() - 3) + " }"
                : "Device::{ ? }";
    }

    public static String zeCommandQueueGroupFlagsToString(long access) {
        String str = "";
        if (0 == access) {
            str += "0 | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE){
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS ) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS | ";
        }

        return ( str.length() > 3 )
                ? "Device::{ " + str.substring(0, str.length() - 3) + " }"
                : "Device::{ ? }";
    }

    public static String zeMemporyAccessCapToString(long access) {
        String str = "";
        if (0 == access) {
            str += "0 | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_RW & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_RW){
            str += "MEMORY_ACCESS_CAP_FLAG_RW | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_ATOMIC & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_ATOMIC) {
            str += "MEMORY_ACCESS_CAP_FLAG_ATOMIC | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT) {
            str += "MEMORY_ACCESS_CAP_FLAG_CONCURRENT | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC ) {
            str += "MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC | ";
        }

        return ( str.length() > 3 )
                ? "Device::{ " + str.substring(0, str.length() - 3) + " }"
                : "Device::{ ? }";
    }

    public static String zeFlagsCacheToString(long flags) {
        String str = "";
        if (0 == flags) {
            str += "0 | ";
        }
        if ((ZeDeviceCachePropertyFlags.ZE_DEVICE_CACHE_PROPERTY_FLAG_USER_CONTROL & flags) == ZeDeviceCachePropertyFlags.ZE_DEVICE_CACHE_PROPERTY_FLAG_USER_CONTROL) {
            str += "CACHE_PROPERTY_FLAG_USER_CONTROL | ";
        }
        return (str.length() > 3)
                ? "Device::{ " + str.substring(0, str.length() - 3) + " }"
                : "Device::{ ? }";
    }

}
