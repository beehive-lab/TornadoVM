/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.enums;

public enum OCLDeviceInfo {

    // Defined in standard OpenCL CL/cl.h

    // @formatter:off
    CL_DEVICE_TYPE(0x1000),
    CL_DEVICE_VENDOR_ID(0x1001),
    CL_DEVICE_MAX_COMPUTE_UNITS(0x1002),
    CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS(0x1003),
    CL_DEVICE_MAX_WORK_GROUP_SIZE(0x1004),
    CL_DEVICE_MAX_WORK_ITEM_SIZES(0x1005),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR(0x1006),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT(0x1007),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT(0x1008),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG(0x1009),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT(0x100A),
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE(0x100B),
    CL_DEVICE_MAX_CLOCK_FREQUENCY(0x100C),
    CL_DEVICE_ADDRESS_BITS(0x100D),
    CL_DEVICE_MAX_READ_IMAGE_ARGS(0x100E),
    CL_DEVICE_MAX_WRITE_IMAGE_ARGS(0x100F),
    CL_DEVICE_MAX_MEM_ALLOC_SIZE(0x1010),
    CL_DEVICE_IMAGE2D_MAX_WIDTH(0x1011),
    CL_DEVICE_IMAGE2D_MAX_HEIGHT(0x1012),
    CL_DEVICE_IMAGE3D_MAX_WIDTH(0x1013),
    CL_DEVICE_IMAGE3D_MAX_HEIGHT(0x1014),
    CL_DEVICE_IMAGE3D_MAX_DEPTH(0x1015),
    CL_DEVICE_IMAGE_SUPPORT(0x1016),
    CL_DEVICE_MAX_PARAMETER_SIZE(0x1017),
    CL_DEVICE_MAX_SAMPLERS(0x1018),
    CL_DEVICE_MEM_BASE_ADDR_ALIGN(0x1019),
    CL_DEVICE_MIN_DATA_TYPE_ALIGN_SIZE(0x101A),
    CL_DEVICE_SINGLE_FP_CONFIG(0x101B),
    CL_DEVICE_GLOBAL_MEM_CACHE_TYPE(0x101C),
    CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE(0x101D),
    CL_DEVICE_GLOBAL_MEM_CACHE_SIZE(0x101E),
    CL_DEVICE_GLOBAL_MEM_SIZE(0x101F),
    CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE(0x1020),
    CL_DEVICE_MAX_CONSTANT_ARGS(0x1021),
    CL_DEVICE_LOCAL_MEM_TYPE(0x1022),
    CL_DEVICE_LOCAL_MEM_SIZE(0x1023),
    CL_DEVICE_ERROR_CORRECTION_SUPPORT(0x1024),
    CL_DEVICE_PROFILING_TIMER_RESOLUTION(0x1025),
    CL_DEVICE_ENDIAN_LITTLE(0x1026),
    CL_DEVICE_AVAILABLE(0x1027),
    CL_DEVICE_COMPILER_AVAILABLE(0x1028),
    CL_DEVICE_EXECUTION_CAPABILITIES(0x1029),
    CL_DEVICE_QUEUE_PROPERTIES(0x102A),
    CL_DEVICE_NAME(0x102B),
    CL_DEVICE_VENDOR(0x102C),
    CL_DRIVER_VERSION(0x102D),
    CL_DEVICE_PROFILE(0x102E),
    CL_DEVICE_VERSION(0x102F),
    CL_DEVICE_EXTENSIONS(0x1030),
    CL_DEVICE_PLATFORM(0x1031),
    CL_DEVICE_DOUBLE_FP_CONFIG(0x1032),

    /* (0x1033), reserved for CL_DEVICE_HALF_FP_CONFIG */
    CL_DEVICE_PREFERRED_VECTOR_WIDTH_HALF(0x1034),
    CL_DEVICE_HOST_UNIFIED_MEMORY(0x1035),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_CHAR(0x1036),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_SHORT(0x1037),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_INT(0x1038),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_LONG(0x1039),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_FLOAT(0x103A),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_DOUBLE(0x103B),
    CL_DEVICE_NATIVE_VECTOR_WIDTH_HALF(0x103C),
    CL_DEVICE_OPENCL_C_VERSION(0x103D),
    CL_DEVICE_LINKER_AVAILABLE(0x103E),
    CL_DEVICE_BUILT_IN_KERNELS(0x103F),
    CL_DEVICE_IMAGE_MAX_BUFFER_SIZE(0x1040),
    CL_DEVICE_IMAGE_MAX_ARRAY_SIZE(0x1041),
    CL_DEVICE_PARENT_DEVICE(0x1042),
    CL_DEVICE_PARTITION_MAX_SUB_DEVICES(0x1043),
    CL_DEVICE_PARTITION_PROPERTIES(0x1044),
    CL_DEVICE_PARTITION_AFFINITY_DOMAIN(0x1045),
    CL_DEVICE_PARTITION_TYPE(0x1046),
    CL_DEVICE_REFERENCE_COUNT(0x1047),
    CL_DEVICE_PREFERRED_INTEROP_USER_SYNC(0x1048),
    CL_DEVICE_PRINTF_BUFFER_SIZE(0x1049),
    CL_DEVICE_IMAGE_PITCH_ALIGNMENT(0x104A),
    CL_DEVICE_IMAGE_BASE_ADDRESS_ALIGNMENT(0x104B),

    // OpenCL 2.1
    CL_DEVICE_IL_VERSION(0x105B),
    CL_DEVICE_MAX_NUM_SUB_GROUPS(0x105C),
    CL_DEVICE_SUB_GROUP_INDEPENDENT_FORWARD_PROGRESS(0x105D),

    // OpenCL 3.0
    CL_DEVICE_NUMERIC_VERSION(0x105E),
    CL_DEVICE_EXTENSIONS_WITH_VERSION(0x1060),
    CL_DEVICE_ILS_WITH_VERSION(0x1061),
    CL_DEVICE_BUILT_IN_KERNELS_WITH_VERSION(0x1062),
    CL_DEVICE_ATOMIC_MEMORY_CAPABILITIES(0x1063),
    CL_DEVICE_ATOMIC_FENCE_CAPABILITIES(0x1064),
    CL_DEVICE_NON_UNIFORM_WORK_GROUP_SUPPORT(0x1065),
    CL_DEVICE_OPENCL_C_ALL_VERSIONS(0x1066),
    CL_DEVICE_PREFERRED_WORK_GROUP_SIZE_MULTIPLE(0x1067),
    CL_DEVICE_WORK_GROUP_COLLECTIVE_FUNCTIONS_SUPPORT(0x1068),
    CL_DEVICE_GENERIC_ADDRESS_SPACE_SUPPORT(0x1069),
    CL_DEVICE_OPENCL_C_FEATURES(0x106F),
    CL_DEVICE_DEVICE_ENQUEUE_CAPABILITIES(0x1070),
    CL_DEVICE_PIPE_SUPPORT(0x1071),
    CL_DEVICE_LATEST_CONFORMANCE_VERSION_PASSED(0x1072);
    // @formatter:on

    private final int value;

    OCLDeviceInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }

    public static final OCLDeviceInfo toDeviceInfo(final int v) {
        OCLDeviceInfo result = null;
        switch (v) {
            case 0x1000:
                result = OCLDeviceInfo.CL_DEVICE_TYPE;
                break;
            case 0x1001:
                result = OCLDeviceInfo.CL_DEVICE_VENDOR_ID;
                break;
            case 0x1002:
                result = OCLDeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS;
                break;
            case 0x1003:
                result = OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS;
                break;
            case 0x1004:
                result = OCLDeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE;
                break;
            case 0x1005:
                result = OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES;
                break;
            case 0x1006:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR;
                break;
            case 0x1007:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT;
                break;
            case 0x1008:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT;
                break;
            case 0x1009:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG;
                break;
            case 0x100A:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT;
                break;
            case 0x100B:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE;
                break;
            case 0x100C:
                result = OCLDeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY;
                break;
            case 0x100D:
                result = OCLDeviceInfo.CL_DEVICE_ADDRESS_BITS;
                break;
            case 0x100E:
                result = OCLDeviceInfo.CL_DEVICE_MAX_READ_IMAGE_ARGS;
                break;
            case 0x100F:
                result = OCLDeviceInfo.CL_DEVICE_MAX_WRITE_IMAGE_ARGS;
                break;
            case 0x1010:
                result = OCLDeviceInfo.CL_DEVICE_MAX_MEM_ALLOC_SIZE;
                break;
            case 0x1011:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE2D_MAX_WIDTH;
                break;
            case 0x1012:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE2D_MAX_HEIGHT;
                break;
            case 0x1013:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE3D_MAX_WIDTH;
                break;
            case 0x1014:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE3D_MAX_HEIGHT;
                break;
            case 0x1015:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE3D_MAX_DEPTH;
                break;
            case 0x1016:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE_SUPPORT;
                break;
            case 0x1017:
                result = OCLDeviceInfo.CL_DEVICE_MAX_PARAMETER_SIZE;
                break;
            case 0x1018:
                result = OCLDeviceInfo.CL_DEVICE_MAX_SAMPLERS;
                break;
            case 0x1019:
                result = OCLDeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN;
                break;
            case 0x101A:
                result = OCLDeviceInfo.CL_DEVICE_MIN_DATA_TYPE_ALIGN_SIZE;
                break;
            case 0x101B:
                result = OCLDeviceInfo.CL_DEVICE_SINGLE_FP_CONFIG;
                break;
            case 0x101C:
                result = OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_CACHE_TYPE;
                break;
            case 0x101D:
                result = OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE;
                break;
            case 0x101E:
                result = OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_CACHE_SIZE;
                break;
            case 0x101F:
                result = OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE;
                break;
            case 0x1020:
                result = OCLDeviceInfo.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE;
                break;
            case 0x1021:
                result = OCLDeviceInfo.CL_DEVICE_MAX_CONSTANT_ARGS;
                break;
            case 0x1022:
                result = OCLDeviceInfo.CL_DEVICE_LOCAL_MEM_TYPE;
                break;
            case 0x1023:
                result = OCLDeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE;
                break;
            case 0x1024:
                result = OCLDeviceInfo.CL_DEVICE_ERROR_CORRECTION_SUPPORT;
                break;
            case 0x1025:
                result = OCLDeviceInfo.CL_DEVICE_PROFILING_TIMER_RESOLUTION;
                break;
            case 0x1026:
                result = OCLDeviceInfo.CL_DEVICE_ENDIAN_LITTLE;
                break;
            case 0x1027:
                result = OCLDeviceInfo.CL_DEVICE_AVAILABLE;
                break;
            case 0x1028:
                result = OCLDeviceInfo.CL_DEVICE_COMPILER_AVAILABLE;
                break;
            case 0x1029:
                result = OCLDeviceInfo.CL_DEVICE_EXECUTION_CAPABILITIES;
                break;
            case 0x102A:
                result = OCLDeviceInfo.CL_DEVICE_QUEUE_PROPERTIES;
                break;
            case 0x102B:
                result = OCLDeviceInfo.CL_DEVICE_NAME;
                break;
            case 0x102C:
                result = OCLDeviceInfo.CL_DEVICE_VENDOR;
                break;
            case 0x102D:
                result = OCLDeviceInfo.CL_DRIVER_VERSION;
                break;
            case 0x102E:
                result = OCLDeviceInfo.CL_DEVICE_PROFILE;
                break;
            case 0x102F:
                result = OCLDeviceInfo.CL_DEVICE_VERSION;
                break;
            case 0x1030:
                result = OCLDeviceInfo.CL_DEVICE_EXTENSIONS;
                break;
            case 0x1031:
                result = OCLDeviceInfo.CL_DEVICE_PLATFORM;
                break;
            case 0x1032:
                result = OCLDeviceInfo.CL_DEVICE_DOUBLE_FP_CONFIG;
                break;
            case 0x1034:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_VECTOR_WIDTH_HALF;
                break;
            case 0x1035:
                result = OCLDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY;
                break;
            case 0x1036:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_CHAR;
                break;
            case 0x1037:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_SHORT;
                break;
            case 0x1038:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_INT;
                break;
            case 0x1039:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_LONG;
                break;
            case 0x103A:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_FLOAT;
                break;
            case 0x103B:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_DOUBLE;
                break;
            case 0x103C:
                result = OCLDeviceInfo.CL_DEVICE_NATIVE_VECTOR_WIDTH_HALF;
                break;
            case 0x103D:
                result = OCLDeviceInfo.CL_DEVICE_OPENCL_C_VERSION;
                break;
            case 0x103E:
                result = OCLDeviceInfo.CL_DEVICE_LINKER_AVAILABLE;
                break;
            case 0x103F:
                result = OCLDeviceInfo.CL_DEVICE_BUILT_IN_KERNELS;
                break;
            case 0x1040:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE_MAX_BUFFER_SIZE;
                break;
            case 0x1041:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE_MAX_ARRAY_SIZE;
                break;
            case 0x1042:
                result = OCLDeviceInfo.CL_DEVICE_PARENT_DEVICE;
                break;
            case 0x1043:
                result = OCLDeviceInfo.CL_DEVICE_PARTITION_MAX_SUB_DEVICES;
                break;
            case 0x1044:
                result = OCLDeviceInfo.CL_DEVICE_PARTITION_PROPERTIES;
                break;
            case 0x1045:
                result = OCLDeviceInfo.CL_DEVICE_PARTITION_AFFINITY_DOMAIN;
                break;
            case 0x1046:
                result = OCLDeviceInfo.CL_DEVICE_PARTITION_TYPE;
                break;
            case 0x1047:
                result = OCLDeviceInfo.CL_DEVICE_REFERENCE_COUNT;
                break;
            case 0x1048:
                result = OCLDeviceInfo.CL_DEVICE_PREFERRED_INTEROP_USER_SYNC;
                break;
            case 0x1049:
                result = OCLDeviceInfo.CL_DEVICE_PRINTF_BUFFER_SIZE;
                break;
            case 0x104A:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE_PITCH_ALIGNMENT;
                break;
            case 0x104B:
                result = OCLDeviceInfo.CL_DEVICE_IMAGE_BASE_ADDRESS_ALIGNMENT;
                break;
        }
        return result;
    }
}
