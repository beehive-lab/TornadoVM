/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Set of utilities ported from zello_log.h (Level Zero)
 */
public class ZeUtils {

    public static long ZE_DEVICE_MEMORY_PROPERTY_FLAG_TBD = ZeConstants.ZE_BIT(0);

    public static String zeTypeToString(int type) {
        switch (type) {
            case Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MODULE_PROPERTIES:
                return "ZE_STRUCTURE_TYPE_DEVICE_MODULE_PROPERTIES";
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
        return (str.length() > 3) ? "Device::{ " + str.substring(0, str.length() - 3) + " }" : "Device::{ ? }";
    }

    public static String zeCommandQueueGroupFlagsToString(long access) {
        String str = "";
        if (0 == access) {
            str += "0 | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COPY | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS
                & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COOPERATIVE_KERNELS | ";
        }

        if ((ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS & access) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS) {
            str += "ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_METRICS | ";
        }

        return (str.length() > 3) ? "Device::{ " + str.substring(0, str.length() - 3) + " }" : "Device::{ ? }";
    }

    public static String zeMemporyAccessCapToString(long access) {
        String str = "";
        if (0 == access) {
            str += "0 | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_RW & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_RW) {
            str += "MEMORY_ACCESS_CAP_FLAG_RW | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_ATOMIC & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_ATOMIC) {
            str += "MEMORY_ACCESS_CAP_FLAG_ATOMIC | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT) {
            str += "MEMORY_ACCESS_CAP_FLAG_CONCURRENT | ";
        }

        if ((ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC & access) == ZeMemoryAccessCapFlags.ZE_MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC) {
            str += "MEMORY_ACCESS_CAP_FLAG_CONCURRENT_ATOMIC | ";
        }

        return (str.length() > 3) ? "Device::{ " + str.substring(0, str.length() - 3) + " }" : "Device::{ ? }";
    }

    public static String zeFlagsCacheToString(long flags) {
        String str = "";
        if (0 == flags) {
            str += "0 | ";
        }
        if ((ZeDeviceCachePropertyFlags.ZE_DEVICE_CACHE_PROPERTY_FLAG_USER_CONTROL & flags) == ZeDeviceCachePropertyFlags.ZE_DEVICE_CACHE_PROPERTY_FLAG_USER_CONTROL) {
            str += "CACHE_PROPERTY_FLAG_USER_CONTROL | ";
        }
        return (str.length() > 3) ? "Device::{ " + str.substring(0, str.length() - 3) + " }" : "Device::{ ? }";
    }

    public static String zeDeviceModuleFlagsToString(long flag) {
        String str = "";
        if (0 == flag) {
            str += "0 | ";
        }

        if ((ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP16 & flag) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP16) {
            str += "ZE_DEVICE_MODULE_FLAG_FP16 | ";
        }

        if ((ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64 & flag) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64) {
            str += "ZE_DEVICE_MODULE_FLAG_FP64 | ";
        }

        if ((ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_INT64_ATOMICS & flag) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_INT64_ATOMICS) {
            str += "ZE_DEVICE_MODULE_FLAG_INT64_ATOMICS | ";
        }

        if ((ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_DP4A & flag) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_DP4A) {
            str += "ZE_DEVICE_MODULE_FLAG_DP4A | ";
        }

        return (str.length() > 3) ? "Flags::{ " + str.substring(0, str.length() - 3) + " }" : "Flags::{ ? }";
    }

    public static String zeDeviceFPXXtoString(long flag) {
        String str = "";
        if (0 == flag) {
            str += "0 | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_DENORM & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_DENORM) {
            str += "ZE_DEVICE_FP_FLAG_DENORM | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_INF_NAN & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_INF_NAN) {
            str += "ZE_DEVICE_FP_FLAG_INF_NAN | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_NEAREST & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_NEAREST) {
            str += "ZE_DEVICE_FP_FLAG_ROUND_TO_NEAREST | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_ZERO & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_ZERO) {
            str += "ZE_DEVICE_FP_FLAG_ROUND_TO_ZERO | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_INF & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUND_TO_INF) {
            str += "ZE_DEVICE_FP_FLAG_ROUND_TO_INF | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_FMA & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_FMA) {
            str += "ZE_DEVICE_FP_FLAG_FMA | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUNDED_DIVIDE_SQRT & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_ROUNDED_DIVIDE_SQRT) {
            str += "ZE_DEVICE_FP_FLAG_ROUNDED_DIVIDE_SQRT | ";
        }

        if ((ZeDeviceFlags.ZE_DEVICE_FP_FLAG_SOFT_FLOAT & flag) == ZeDeviceFlags.ZE_DEVICE_FP_FLAG_SOFT_FLOAT) {
            str += "ZE_DEVICE_FP_FLAG_SOFT_FLOAT | ";
        }

        return (str.length() > 3) ? "Flags::{ " + str.substring(0, str.length() - 3) + " }" : "Flags::{ ? }";
    }

}
