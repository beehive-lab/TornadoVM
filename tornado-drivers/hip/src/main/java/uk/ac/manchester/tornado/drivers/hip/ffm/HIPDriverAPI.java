/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.hip.ffm;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_CHAR;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.downcall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings for the subset of the HIP runtime ({@code amdhip64}) the backend uses. One static
 * method per entry point, named and shaped like the C function so that the call sites read like the
 * HIP documentation.
 *
 * <p>
 * Handle types the Java layer carries as {@code long} ({@code hipStream_t}, {@code hipEvent_t},
 * {@code hipModule_t}, {@code hipFunction_t}, {@code hipDeviceptr_t}, {@code hipGraph_t},
 * {@code hipGraphExec_t}) are bound as {@code JAVA_LONG}; out parameters, host buffers and argument
 * vectors are bound as {@code ADDRESS}.
 *
 * <p>
 * HIP deprecates the CUDA-style context API, so the backend's per-device context is the device itself:
 * {@link #hipDevicePrimaryCtxRetain} hands out {@code ordinal + 1} as the context handle and
 * {@link #hipCtxSetCurrent} makes that device current with {@code hipSetDevice}, which is per-thread
 * state just as the current context is.
 */
public final class HIPDriverAPI {

    /** hipError_t success code. */
    public static final int HIP_SUCCESS = 0;
    /** hipErrorInvalidValue. */
    public static final int HIP_ERROR_INVALID_VALUE = 1;
    /** hipErrorOutOfMemory. */
    public static final int HIP_ERROR_OUT_OF_MEMORY = 2;
    /** hipErrorNoBinaryForGpu: the code object has no code for this device's architecture. */
    public static final int HIP_ERROR_NO_BINARY_FOR_GPU = 209;
    /** hipErrorInvalidContext. */
    public static final int HIP_ERROR_INVALID_CONTEXT = 201;
    /** hipErrorInvalidImage (also what an unloadable code object reports). */
    public static final int HIP_ERROR_INVALID_IMAGE = 200;
    /** hipErrorNotReady, returned by hipEventQuery for an event that has not completed yet. */
    public static final int HIP_ERROR_NOT_READY = 600;
    /** hipErrorIllegalAddress. */
    public static final int HIP_ERROR_ILLEGAL_ADDRESS = 700;
    /** hipErrorLaunchOutOfResources. */
    public static final int HIP_ERROR_LAUNCH_OUT_OF_RESOURCES = 701;
    /** hipErrorHostMemoryAlreadyRegistered. */
    public static final int HIP_ERROR_HOST_MEMORY_ALREADY_REGISTERED = 712;
    /** hipErrorNotSupported, also reported for an entry point this runtime does not export. */
    public static final int HIP_ERROR_NOT_SUPPORTED = 801;
    /** hipErrorStreamCaptureUnsupported. */
    public static final int HIP_ERROR_STREAM_CAPTURE_UNSUPPORTED = 900;

    /** hipDeviceSchedule* flags (hipSetDeviceFlags). */
    public static final int HIP_DEVICE_SCHEDULE_AUTO = 0x00;
    public static final int HIP_DEVICE_SCHEDULE_SPIN = 0x01;
    public static final int HIP_DEVICE_SCHEDULE_YIELD = 0x02;
    public static final int HIP_DEVICE_SCHEDULE_BLOCKING_SYNC = 0x04;

    /** hipDeviceAttribute_t values the backend queries (they differ from the CUDA enum). */
    public static final int HIP_DEVICE_ATTRIBUTE_ASYNC_ENGINE_COUNT = 2;
    public static final int HIP_DEVICE_ATTRIBUTE_CLOCK_RATE = 5;
    public static final int HIP_DEVICE_ATTRIBUTE_CONCURRENT_KERNELS = 8;
    public static final int HIP_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR = 23;
    public static final int HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X = 26;
    public static final int HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Y = 27;
    public static final int HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Z = 28;
    public static final int HIP_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK = 56;
    public static final int HIP_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR = 61;
    public static final int HIP_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT = 63;
    public static final int HIP_DEVICE_ATTRIBUTE_PCI_BUS_ID = 67;
    public static final int HIP_DEVICE_ATTRIBUTE_PCI_DEVICE_ID = 68;
    public static final int HIP_DEVICE_ATTRIBUTE_PCI_DOMAIN_ID = 69;
    public static final int HIP_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK = 74;
    public static final int HIP_DEVICE_ATTRIBUTE_TOTAL_CONSTANT_MEMORY = 83;
    public static final int HIP_DEVICE_ATTRIBUTE_WARP_SIZE = 87;

    /** hipFunction_attribute: the largest block a function can launch with. */
    public static final int HIP_FUNC_ATTRIBUTE_MAX_THREADS_PER_BLOCK = 0;

    /** hipStreamCaptureStatus / hipStreamCaptureMode values used by the graph path. */
    public static final int HIP_STREAM_CAPTURE_STATUS_NONE = 0;
    public static final int HIP_STREAM_CAPTURE_STATUS_ACTIVE = 1;
    public static final int HIP_STREAM_CAPTURE_MODE_GLOBAL = 0;

    /** hipStreamNonBlocking: no implicit synchronisation with the null stream. */
    public static final int HIP_STREAM_NON_BLOCKING = 0x01;

    /** Bytes of {@code hipDeviceProp_t} as {@code hipGetDevicePropertiesR0600} fills it, and where its {@code gcnArchName} lies. */
    private static final long DEVICE_PROP_R0600_BYTES = 1472;
    private static final long DEVICE_PROP_R0600_GCN_ARCH_NAME = 1160;
    private static final int GCN_ARCH_NAME_BYTES = 256;

    private static final SymbolLookup LIBHIP = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"),
            List.of("amdhip64_7.dll", "amdhip64_6.dll", "amdhip64.dll"));

    private static final MethodHandle HIP_GET_ERROR_NAME;
    private static final MethodHandle HIP_GET_ERROR_STRING;
    private static final MethodHandle HIP_INIT;
    private static final MethodHandle HIP_DRIVER_GET_VERSION;
    private static final MethodHandle HIP_RUNTIME_GET_VERSION;
    private static final MethodHandle HIP_DEVICE_GET;
    private static final MethodHandle HIP_GET_DEVICE_COUNT;
    private static final MethodHandle HIP_DEVICE_GET_NAME;
    private static final MethodHandle HIP_DEVICE_TOTAL_MEM;
    private static final MethodHandle HIP_DEVICE_GET_ATTRIBUTE;
    private static final MethodHandle HIP_GET_DEVICE_PROPERTIES;
    private static final MethodHandle HIP_SET_DEVICE;
    private static final MethodHandle HIP_SET_DEVICE_FLAGS;
    private static final MethodHandle HIP_DEVICE_SYNCHRONIZE;
    private static final MethodHandle HIP_STREAM_CREATE_WITH_FLAGS;
    private static final MethodHandle HIP_STREAM_DESTROY;
    private static final MethodHandle HIP_STREAM_SYNCHRONIZE;
    private static final MethodHandle HIP_STREAM_WAIT_EVENT;
    private static final MethodHandle HIP_EVENT_CREATE_WITH_FLAGS;
    private static final MethodHandle HIP_EVENT_DESTROY;
    private static final MethodHandle HIP_EVENT_RECORD;
    private static final MethodHandle HIP_EVENT_QUERY;
    private static final MethodHandle HIP_EVENT_SYNCHRONIZE;
    private static final MethodHandle HIP_EVENT_ELAPSED_TIME;
    private static final MethodHandle HIP_MALLOC;
    private static final MethodHandle HIP_FREE;
    private static final MethodHandle HIP_HOST_MALLOC;
    private static final MethodHandle HIP_HOST_FREE;
    private static final MethodHandle HIP_HOST_REGISTER;
    private static final MethodHandle HIP_HOST_UNREGISTER;
    private static final MethodHandle HIP_MEMCPY_HTOD_ASYNC;
    private static final MethodHandle HIP_MEMCPY_DTOH_ASYNC;
    private static final MethodHandle HIP_MEMCPY_DTOD;
    private static final MethodHandle HIP_MEMSET_D8;
    private static final MethodHandle HIP_MODULE_LOAD_DATA_EX;
    private static final MethodHandle HIP_MODULE_GET_FUNCTION;
    private static final MethodHandle HIP_MODULE_UNLOAD;
    private static final MethodHandle HIP_MODULE_LAUNCH_KERNEL;
    private static final MethodHandle HIP_MODULE_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE;
    private static final MethodHandle HIP_FUNC_GET_ATTRIBUTE;
    private static final MethodHandle HIP_STREAM_BEGIN_CAPTURE;
    private static final MethodHandle HIP_STREAM_END_CAPTURE;
    private static final MethodHandle HIP_STREAM_IS_CAPTURING;
    private static final MethodHandle HIP_GRAPH_INSTANTIATE_WITH_FLAGS;
    private static final MethodHandle HIP_GRAPH_EXEC_UPDATE;
    private static final MethodHandle HIP_GRAPH_LAUNCH;
    private static final MethodHandle HIP_GRAPH_EXEC_DESTROY;
    private static final MethodHandle HIP_GRAPH_DESTROY;

    static {
        if (LIBHIP == null) {
            HIP_GET_ERROR_NAME = null;
            HIP_GET_ERROR_STRING = null;
            HIP_INIT = null;
            HIP_DRIVER_GET_VERSION = null;
            HIP_RUNTIME_GET_VERSION = null;
            HIP_DEVICE_GET = null;
            HIP_GET_DEVICE_COUNT = null;
            HIP_DEVICE_GET_NAME = null;
            HIP_DEVICE_TOTAL_MEM = null;
            HIP_DEVICE_GET_ATTRIBUTE = null;
            HIP_GET_DEVICE_PROPERTIES = null;
            HIP_SET_DEVICE = null;
            HIP_SET_DEVICE_FLAGS = null;
            HIP_DEVICE_SYNCHRONIZE = null;
            HIP_STREAM_CREATE_WITH_FLAGS = null;
            HIP_STREAM_DESTROY = null;
            HIP_STREAM_SYNCHRONIZE = null;
            HIP_STREAM_WAIT_EVENT = null;
            HIP_EVENT_CREATE_WITH_FLAGS = null;
            HIP_EVENT_DESTROY = null;
            HIP_EVENT_RECORD = null;
            HIP_EVENT_QUERY = null;
            HIP_EVENT_SYNCHRONIZE = null;
            HIP_EVENT_ELAPSED_TIME = null;
            HIP_MALLOC = null;
            HIP_FREE = null;
            HIP_HOST_MALLOC = null;
            HIP_HOST_FREE = null;
            HIP_HOST_REGISTER = null;
            HIP_HOST_UNREGISTER = null;
            HIP_MEMCPY_HTOD_ASYNC = null;
            HIP_MEMCPY_DTOH_ASYNC = null;
            HIP_MEMCPY_DTOD = null;
            HIP_MEMSET_D8 = null;
            HIP_MODULE_LOAD_DATA_EX = null;
            HIP_MODULE_GET_FUNCTION = null;
            HIP_MODULE_UNLOAD = null;
            HIP_MODULE_LAUNCH_KERNEL = null;
            HIP_MODULE_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE = null;
            HIP_FUNC_GET_ATTRIBUTE = null;
            HIP_STREAM_BEGIN_CAPTURE = null;
            HIP_STREAM_END_CAPTURE = null;
            HIP_STREAM_IS_CAPTURING = null;
            HIP_GRAPH_INSTANTIATE_WITH_FLAGS = null;
            HIP_GRAPH_EXEC_UPDATE = null;
            HIP_GRAPH_LAUNCH = null;
            HIP_GRAPH_EXEC_DESTROY = null;
            HIP_GRAPH_DESTROY = null;
        } else {
            HIP_GET_ERROR_NAME = downcall(LIBHIP, FunctionDescriptor.of(C_POINTER, C_INT), "hipGetErrorName");
            HIP_GET_ERROR_STRING = downcall(LIBHIP, FunctionDescriptor.of(C_POINTER, C_INT), "hipGetErrorString");
            HIP_INIT = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_INT), "hipInit");
            HIP_DRIVER_GET_VERSION = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER), "hipDriverGetVersion");
            HIP_RUNTIME_GET_VERSION = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER), "hipRuntimeGetVersion");
            HIP_DEVICE_GET = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hipDeviceGet");
            HIP_GET_DEVICE_COUNT = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER), "hipGetDeviceCount");
            HIP_DEVICE_GET_NAME = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "hipDeviceGetName");
            HIP_DEVICE_TOTAL_MEM = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hipDeviceTotalMem");
            HIP_DEVICE_GET_ATTRIBUTE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "hipDeviceGetAttribute");
            HIP_GET_DEVICE_PROPERTIES = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hipGetDevicePropertiesR0600");
            HIP_SET_DEVICE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_INT), "hipSetDevice");
            HIP_SET_DEVICE_FLAGS = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_INT), "hipSetDeviceFlags");
            HIP_DEVICE_SYNCHRONIZE = downcall(LIBHIP, FunctionDescriptor.of(C_INT), "hipDeviceSynchronize");
            HIP_STREAM_CREATE_WITH_FLAGS = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hipStreamCreateWithFlags");
            HIP_STREAM_DESTROY = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipStreamDestroy");
            HIP_STREAM_SYNCHRONIZE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipStreamSynchronize");
            HIP_STREAM_WAIT_EVENT = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT), "hipStreamWaitEvent");
            HIP_EVENT_CREATE_WITH_FLAGS = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hipEventCreateWithFlags");
            HIP_EVENT_DESTROY = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipEventDestroy");
            HIP_EVENT_RECORD = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hipEventRecord");
            HIP_EVENT_QUERY = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipEventQuery");
            HIP_EVENT_SYNCHRONIZE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipEventSynchronize");
            HIP_EVENT_ELAPSED_TIME = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG), "hipEventElapsedTime");
            HIP_MALLOC = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "hipMalloc");
            HIP_FREE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipFree");
            HIP_HOST_MALLOC = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_INT), "hipHostMalloc");
            HIP_HOST_FREE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipHostFree");
            HIP_HOST_REGISTER = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT), "hipHostRegister");
            HIP_HOST_UNREGISTER = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipHostUnregister");
            HIP_MEMCPY_HTOD_ASYNC = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG), "hipMemcpyHtoDAsync");
            HIP_MEMCPY_DTOH_ASYNC = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG), "hipMemcpyDtoHAsync");
            HIP_MEMCPY_DTOD = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hipMemcpyDtoD");
            HIP_MEMSET_D8 = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_CHAR, C_LONG), "hipMemsetD8");
            HIP_MODULE_LOAD_DATA_EX = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER), "hipModuleLoadDataEx");
            HIP_MODULE_GET_FUNCTION = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_POINTER), "hipModuleGetFunction");
            HIP_MODULE_UNLOAD = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipModuleUnload");
            HIP_MODULE_LAUNCH_KERNEL = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_LONG, C_POINTER, C_POINTER), "hipModuleLaunchKernel");
            HIP_MODULE_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG, C_LONG, C_INT), "hipModuleOccupancyMaxPotentialBlockSize");
            HIP_FUNC_GET_ATTRIBUTE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_LONG), "hipFuncGetAttribute");
            HIP_STREAM_BEGIN_CAPTURE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_INT), "hipStreamBeginCapture");
            HIP_STREAM_END_CAPTURE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hipStreamEndCapture");
            HIP_STREAM_IS_CAPTURING = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hipStreamIsCapturing");
            HIP_GRAPH_INSTANTIATE_WITH_FLAGS = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG), "hipGraphInstantiateWithFlags");
            HIP_GRAPH_EXEC_UPDATE = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_POINTER), "hipGraphExecUpdate");
            HIP_GRAPH_LAUNCH = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hipGraphLaunch");
            HIP_GRAPH_EXEC_DESTROY = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipGraphExecDestroy");
            HIP_GRAPH_DESTROY = downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipGraphDestroy");
        }
    }

    private HIPDriverAPI() {
    }

    /** Whether amdhip64 could be opened at all; false on a machine without the HIP runtime. */
    public static boolean isAvailable() {
        return LIBHIP != null && HIP_INIT != null;
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    public static int hipInit(int flags) {
        try {
            return (int) HIP_INIT.invokeExact(flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDriverGetVersion(MemorySegment driverVersion) {
        try {
            return (int) HIP_DRIVER_GET_VERSION.invokeExact(driverVersion);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipRuntimeGetVersion(MemorySegment runtimeVersion) {
        try {
            return (int) HIP_RUNTIME_GET_VERSION.invokeExact(runtimeVersion);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDeviceGet(MemorySegment device, int ordinal) {
        try {
            return (int) HIP_DEVICE_GET.invokeExact(device, ordinal);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGetDeviceCount(MemorySegment count) {
        try {
            return (int) HIP_GET_DEVICE_COUNT.invokeExact(count);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDeviceGetName(MemorySegment name, int len, int device) {
        try {
            return (int) HIP_DEVICE_GET_NAME.invokeExact(name, len, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDeviceTotalMem(MemorySegment bytes, int device) {
        try {
            return (int) HIP_DEVICE_TOTAL_MEM.invokeExact(bytes, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDeviceGetAttribute(MemorySegment value, int attribute, int device) {
        try {
            return (int) HIP_DEVICE_GET_ATTRIBUTE.invokeExact(value, attribute, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGetDeviceProperties(MemorySegment properties, int device) {
        if (HIP_GET_DEVICE_PROPERTIES == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GET_DEVICE_PROPERTIES.invokeExact(properties, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipSetDevice(int device) {
        try {
            return (int) HIP_SET_DEVICE.invokeExact(device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipSetDeviceFlags(int flags) {
        try {
            return (int) HIP_SET_DEVICE_FLAGS.invokeExact(flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipDeviceSynchronize() {
        try {
            return (int) HIP_DEVICE_SYNCHRONIZE.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamCreateWithFlags(MemorySegment stream, int flags) {
        try {
            return (int) HIP_STREAM_CREATE_WITH_FLAGS.invokeExact(stream, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamDestroy(long stream) {
        try {
            return (int) HIP_STREAM_DESTROY.invokeExact(stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamSynchronize(long stream) {
        try {
            return (int) HIP_STREAM_SYNCHRONIZE.invokeExact(stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamWaitEvent(long stream, long event, int flags) {
        try {
            return (int) HIP_STREAM_WAIT_EVENT.invokeExact(stream, event, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventCreateWithFlags(MemorySegment event, int flags) {
        try {
            return (int) HIP_EVENT_CREATE_WITH_FLAGS.invokeExact(event, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventDestroy(long event) {
        try {
            return (int) HIP_EVENT_DESTROY.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventRecord(long event, long stream) {
        try {
            return (int) HIP_EVENT_RECORD.invokeExact(event, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventQuery(long event) {
        try {
            return (int) HIP_EVENT_QUERY.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventSynchronize(long event) {
        try {
            return (int) HIP_EVENT_SYNCHRONIZE.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipEventElapsedTime(MemorySegment milliseconds, long start, long end) {
        try {
            return (int) HIP_EVENT_ELAPSED_TIME.invokeExact(milliseconds, start, end);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipMalloc(MemorySegment devicePointer, long byteSize) {
        try {
            return (int) HIP_MALLOC.invokeExact(devicePointer, byteSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipFree(long devicePointer) {
        try {
            return (int) HIP_FREE.invokeExact(devicePointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipHostMalloc(MemorySegment hostPointer, long byteSize, int flags) {
        try {
            return (int) HIP_HOST_MALLOC.invokeExact(hostPointer, byteSize, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipHostFree(long hostPointer) {
        try {
            return (int) HIP_HOST_FREE.invokeExact(hostPointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipHostRegister(long hostPointer, long byteSize, int flags) {
        try {
            return (int) HIP_HOST_REGISTER.invokeExact(hostPointer, byteSize, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipHostUnregister(long hostPointer) {
        try {
            return (int) HIP_HOST_UNREGISTER.invokeExact(hostPointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipMemcpyHtoDAsync(long destinationDevice, long sourceHost, long byteCount, long stream) {
        try {
            return (int) HIP_MEMCPY_HTOD_ASYNC.invokeExact(destinationDevice, sourceHost, byteCount, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipMemcpyDtoHAsync(long destinationHost, long sourceDevice, long byteCount, long stream) {
        try {
            return (int) HIP_MEMCPY_DTOH_ASYNC.invokeExact(destinationHost, sourceDevice, byteCount, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipMemcpyDtoD(long destinationDevice, long sourceDevice, long byteCount) {
        try {
            return (int) HIP_MEMCPY_DTOD.invokeExact(destinationDevice, sourceDevice, byteCount);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipMemsetD8(long destinationDevice, byte value, long count) {
        try {
            return (int) HIP_MEMSET_D8.invokeExact(destinationDevice, value, count);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipModuleLoadDataEx(MemorySegment module, MemorySegment image, int numOptions, MemorySegment options, MemorySegment optionValues) {
        try {
            return (int) HIP_MODULE_LOAD_DATA_EX.invokeExact(module, image, numOptions, options, optionValues);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipModuleGetFunction(MemorySegment function, long module, MemorySegment name) {
        try {
            return (int) HIP_MODULE_GET_FUNCTION.invokeExact(function, module, name);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipModuleUnload(long module) {
        try {
            return (int) HIP_MODULE_UNLOAD.invokeExact(module);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipModuleLaunchKernel(long function, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, int sharedMemBytes, long stream, MemorySegment kernelParams, MemorySegment extra) {
        try {
            return (int) HIP_MODULE_LAUNCH_KERNEL.invokeExact(function, gridDimX, gridDimY, gridDimZ, blockDimX, blockDimY, blockDimZ, sharedMemBytes, stream, kernelParams, extra);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipModuleOccupancyMaxPotentialBlockSize(MemorySegment gridSize, MemorySegment blockSize, long function, long dynamicSharedMemory, int blockSizeLimit) {
        try {
            return (int) HIP_MODULE_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE.invokeExact(gridSize, blockSize, function, dynamicSharedMemory, blockSizeLimit);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipFuncGetAttribute(MemorySegment value, int attribute, long function) {
        try {
            return (int) HIP_FUNC_GET_ATTRIBUTE.invokeExact(value, attribute, function);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamBeginCapture(long stream, int mode) {
        if (HIP_STREAM_BEGIN_CAPTURE == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_STREAM_BEGIN_CAPTURE.invokeExact(stream, mode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamEndCapture(long stream, MemorySegment graph) {
        if (HIP_STREAM_END_CAPTURE == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_STREAM_END_CAPTURE.invokeExact(stream, graph);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipStreamIsCapturing(long stream, MemorySegment captureStatus) {
        if (HIP_STREAM_IS_CAPTURING == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_STREAM_IS_CAPTURING.invokeExact(stream, captureStatus);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGraphInstantiateWithFlags(MemorySegment graphExec, long graph, long flags) {
        if (HIP_GRAPH_INSTANTIATE_WITH_FLAGS == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GRAPH_INSTANTIATE_WITH_FLAGS.invokeExact(graphExec, graph, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGraphExecUpdate(long graphExec, long graph, MemorySegment errorNode, MemorySegment updateResult) {
        if (HIP_GRAPH_EXEC_UPDATE == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GRAPH_EXEC_UPDATE.invokeExact(graphExec, graph, errorNode, updateResult);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGraphLaunch(long graphExec, long stream) {
        if (HIP_GRAPH_LAUNCH == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GRAPH_LAUNCH.invokeExact(graphExec, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGraphExecDestroy(long graphExec) {
        if (HIP_GRAPH_EXEC_DESTROY == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GRAPH_EXEC_DESTROY.invokeExact(graphExec);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hipGraphDestroy(long graph) {
        if (HIP_GRAPH_DESTROY == null) {
            return HIP_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) HIP_GRAPH_DESTROY.invokeExact(graph);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** The context handle of a device: its ordinal plus one, so that 0 still means "no context". */
    public static int hipDevicePrimaryCtxRetain(MemorySegment context, int device) {
        int result = hipSetDevice(device);
        if (result == HIP_SUCCESS) {
            context.set(C_POINTER, 0, MemorySegment.ofAddress(device + 1L));
        }
        return result;
    }

    /** Nothing to release: the device stays initialised for the process, as the primary context does. */
    public static int hipDevicePrimaryCtxRelease(int device) {
        return HIP_SUCCESS;
    }

    /** The scheduling flags for the device; HIP rejects them once the device is active, which is not an error here. */
    public static int hipDevicePrimaryCtxSetFlags(int device, int flags) {
        int result = hipSetDevice(device);
        if (result != HIP_SUCCESS) {
            return result;
        }
        hipSetDeviceFlags(flags);
        return HIP_SUCCESS;
    }

    /** Makes the device behind a context handle current for this thread. */
    public static int hipCtxSetCurrent(long context) {
        if (context <= 0) {
            return HIP_ERROR_INVALID_CONTEXT;
        }
        return hipSetDevice((int) (context - 1));
    }

    /** Waits for all work on the current device. */
    public static int hipCtxSynchronize() {
        return hipDeviceSynchronize();
    }

    /**
     * The device's architecture as hipRTC names it ({@code gfx942}), without the feature suffixes
     * ({@code :sramecc+:xnack-}); empty when the query fails. Read from {@code hipDeviceProp_t} through
     * the versioned {@code hipGetDevicePropertiesR0600}, whose layout is fixed by that version.
     */
    public static String gcnArchName(int device) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment properties = arena.allocate(DEVICE_PROP_R0600_BYTES, 8);
            if (hipGetDeviceProperties(properties, device) != HIP_SUCCESS) {
                return "";
            }
            String name = FFMSupport.readCString(properties.asSlice(DEVICE_PROP_R0600_GCN_ARCH_NAME, GCN_ARCH_NAME_BYTES), GCN_ARCH_NAME_BYTES);
            int colon = name.indexOf(':');
            return colon < 0 ? name.trim() : name.substring(0, colon).trim();
        }
    }

    /** The {@code hipError*} name for a hipError_t, or the bare number when the runtime has none. */
    public static String errorName(int result) {
        String name = readStatic(HIP_GET_ERROR_NAME, result);
        return name == null ? Integer.toString(result) : name;
    }

    /** The human-readable description for a hipError_t, or {@code null} when the runtime has none. */
    public static String errorString(int result) {
        return readStatic(HIP_GET_ERROR_STRING, result);
    }

    private static String readStatic(MethodHandle handle, int result) {
        if (handle == null) {
            return null;
        }
        try {
            MemorySegment text = (MemorySegment) handle.invokeExact(result);
            return FFMSupport.readCString(text);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * The message a failed runtime call is reported with: the call that failed, the {@code hipError*}
     * name and numeric code, and the runtime's own description of it.
     */
    public static String describe(String call, int result) {
        StringBuilder message = new StringBuilder(call).append(" failed: ").append(errorName(result)).append(" (").append(result).append(")");
        String text = errorString(result);
        if (text != null) {
            message.append(" - ").append(text);
        }
        return message.toString();
    }
}
