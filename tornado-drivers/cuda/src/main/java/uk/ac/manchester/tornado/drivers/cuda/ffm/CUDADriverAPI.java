/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.cuda.ffm;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
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

/**
 * Panama bindings for the subset of the CUDA Driver API the backend uses. One static method per
 * entry point, named and shaped exactly like the C function so that the call sites read like the
 * driver documentation.
 *
 * <p>
 * Handle types that the Java layer already carries as {@code long} ({@code CUcontext},
 * {@code CUstream}, {@code CUevent}, {@code CUmodule}, {@code CUfunction}, {@code CUdeviceptr},
 * {@code CUgraph}, {@code CUgraphExec}) are bound as {@code JAVA_LONG}: on every ABI Panama
 * supports, a pointer and a 64-bit integer are passed in the same register class, and keeping them
 * as longs is what lets the existing Java code stay untouched. Genuine memory arguments -- out
 * parameters, host buffers, argument vectors -- are bound as {@code ADDRESS} so that Panama keeps
 * the owning arena alive across the call.
 *
 * <p>
 * Every binding names the versioned symbol the CUDA headers would have resolved
 * ({@code cuCtxCreate} is a macro for {@code cuCtxCreate_v2}) before the plain one: libcuda still
 * exports the unversioned symbols with the older, incompatible ABI.
 */
public final class CUDADriverAPI {

    /** CUresult success code. */
    public static final int CUDA_SUCCESS = 0;
    /** CUresult returned by cuEventQuery for an event that has not completed yet. */
    public static final int CUDA_ERROR_NOT_READY = 600;
    /** CUresult reported for an entry point this driver does not export. */
    public static final int CUDA_ERROR_NOT_SUPPORTED = 801;

    /** CUctx_flags: yield the CPU while waiting for the GPU. */
    public static final int CU_CTX_SCHED_YIELD = 0x02;

    /** CUdevice_attribute values the backend queries. */
    public static final int CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK = 1;
    public static final int CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X = 2;
    public static final int CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Y = 3;
    public static final int CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Z = 4;
    public static final int CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK = 8;
    public static final int CU_DEVICE_ATTRIBUTE_TOTAL_CONSTANT_MEMORY = 9;
    public static final int CU_DEVICE_ATTRIBUTE_WARP_SIZE = 10;
    public static final int CU_DEVICE_ATTRIBUTE_CLOCK_RATE = 13;
    public static final int CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT = 16;
    public static final int CU_DEVICE_ATTRIBUTE_CONCURRENT_KERNELS = 31;
    public static final int CU_DEVICE_ATTRIBUTE_ASYNC_ENGINE_COUNT = 40;
    public static final int CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR = 75;
    public static final int CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR = 76;

    /** CUjit_option values used to capture the driver's JIT diagnostics on a module load. */
    public static final int CU_JIT_ERROR_LOG_BUFFER = 5;
    public static final int CU_JIT_ERROR_LOG_BUFFER_SIZE_BYTES = 6;

    /** CUstreamCaptureStatus / CUstreamCaptureMode values used by the CUDA-graph path. */
    public static final int CU_STREAM_CAPTURE_STATUS_NONE = 0;
    public static final int CU_STREAM_CAPTURE_STATUS_ACTIVE = 1;
    public static final int CU_STREAM_CAPTURE_MODE_GLOBAL = 0;

    private static final SymbolLookup LIBCUDA = FFMSupport.loadLibrary("libcuda.so.1", "libcuda.so", "nvcuda.dll", "libcuda.dylib");

    private static final MethodHandle CU_INIT;
    private static final MethodHandle CU_DRIVER_GET_VERSION;
    private static final MethodHandle CU_GET_ERROR_NAME;
    private static final MethodHandle CU_GET_ERROR_STRING;

    private static final MethodHandle CU_DEVICE_GET;
    private static final MethodHandle CU_DEVICE_GET_COUNT;
    private static final MethodHandle CU_DEVICE_GET_NAME;
    private static final MethodHandle CU_DEVICE_TOTAL_MEM;
    private static final MethodHandle CU_DEVICE_GET_ATTRIBUTE;

    private static final MethodHandle CU_CTX_CREATE;
    private static final MethodHandle CU_CTX_DESTROY;
    private static final MethodHandle CU_CTX_SET_CURRENT;
    private static final MethodHandle CU_CTX_SYNCHRONIZE;

    private static final MethodHandle CU_STREAM_CREATE;
    private static final MethodHandle CU_STREAM_DESTROY;
    private static final MethodHandle CU_STREAM_SYNCHRONIZE;
    private static final MethodHandle CU_STREAM_WAIT_EVENT;

    private static final MethodHandle CU_EVENT_CREATE;
    private static final MethodHandle CU_EVENT_DESTROY;
    private static final MethodHandle CU_EVENT_RECORD;
    private static final MethodHandle CU_EVENT_QUERY;
    private static final MethodHandle CU_EVENT_SYNCHRONIZE;
    private static final MethodHandle CU_EVENT_ELAPSED_TIME;

    private static final MethodHandle CU_MEM_ALLOC;
    private static final MethodHandle CU_MEM_FREE;
    private static final MethodHandle CU_MEM_ALLOC_HOST;
    private static final MethodHandle CU_MEM_FREE_HOST;
    private static final MethodHandle CU_MEM_HOST_REGISTER;
    private static final MethodHandle CU_MEM_HOST_UNREGISTER;
    private static final MethodHandle CU_MEMCPY_HTOD_ASYNC;
    private static final MethodHandle CU_MEMCPY_DTOH_ASYNC;
    private static final MethodHandle CU_MEMCPY_DTOD;
    private static final MethodHandle CU_MEMSET_D8;

    private static final MethodHandle CU_MODULE_LOAD_DATA_EX;
    private static final MethodHandle CU_MODULE_GET_FUNCTION;
    private static final MethodHandle CU_MODULE_UNLOAD;
    private static final MethodHandle CU_LAUNCH_KERNEL;
    private static final MethodHandle CU_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE;

    private static final MethodHandle CU_STREAM_BEGIN_CAPTURE;
    private static final MethodHandle CU_STREAM_END_CAPTURE;
    private static final MethodHandle CU_STREAM_IS_CAPTURING;
    private static final MethodHandle CU_GRAPH_INSTANTIATE;
    private static final MethodHandle CU_GRAPH_EXEC_UPDATE;
    private static final MethodHandle CU_GRAPH_LAUNCH;
    private static final MethodHandle CU_GRAPH_EXEC_DESTROY;
    private static final MethodHandle CU_GRAPH_DESTROY;

    static {
        if (LIBCUDA == null) {
            CU_INIT = null;
            CU_DRIVER_GET_VERSION = null;
            CU_GET_ERROR_NAME = null;
            CU_GET_ERROR_STRING = null;
            CU_DEVICE_GET = null;
            CU_DEVICE_GET_COUNT = null;
            CU_DEVICE_GET_NAME = null;
            CU_DEVICE_TOTAL_MEM = null;
            CU_DEVICE_GET_ATTRIBUTE = null;
            CU_CTX_CREATE = null;
            CU_CTX_DESTROY = null;
            CU_CTX_SET_CURRENT = null;
            CU_CTX_SYNCHRONIZE = null;
            CU_STREAM_CREATE = null;
            CU_STREAM_DESTROY = null;
            CU_STREAM_SYNCHRONIZE = null;
            CU_STREAM_WAIT_EVENT = null;
            CU_EVENT_CREATE = null;
            CU_EVENT_DESTROY = null;
            CU_EVENT_RECORD = null;
            CU_EVENT_QUERY = null;
            CU_EVENT_SYNCHRONIZE = null;
            CU_EVENT_ELAPSED_TIME = null;
            CU_MEM_ALLOC = null;
            CU_MEM_FREE = null;
            CU_MEM_ALLOC_HOST = null;
            CU_MEM_FREE_HOST = null;
            CU_MEM_HOST_REGISTER = null;
            CU_MEM_HOST_UNREGISTER = null;
            CU_MEMCPY_HTOD_ASYNC = null;
            CU_MEMCPY_DTOH_ASYNC = null;
            CU_MEMCPY_DTOD = null;
            CU_MEMSET_D8 = null;
            CU_MODULE_LOAD_DATA_EX = null;
            CU_MODULE_GET_FUNCTION = null;
            CU_MODULE_UNLOAD = null;
            CU_LAUNCH_KERNEL = null;
            CU_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE = null;
            CU_STREAM_BEGIN_CAPTURE = null;
            CU_STREAM_END_CAPTURE = null;
            CU_STREAM_IS_CAPTURING = null;
            CU_GRAPH_INSTANTIATE = null;
            CU_GRAPH_EXEC_UPDATE = null;
            CU_GRAPH_LAUNCH = null;
            CU_GRAPH_EXEC_DESTROY = null;
            CU_GRAPH_DESTROY = null;
        } else {
            CU_INIT = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_INT), "cuInit");
            CU_DRIVER_GET_VERSION = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER), "cuDriverGetVersion");
            CU_GET_ERROR_NAME = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_INT, C_POINTER), "cuGetErrorName");
            CU_GET_ERROR_STRING = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_INT, C_POINTER), "cuGetErrorString");

            CU_DEVICE_GET = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "cuDeviceGet");
            CU_DEVICE_GET_COUNT = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER), "cuDeviceGetCount");
            CU_DEVICE_GET_NAME = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "cuDeviceGetName");
            CU_DEVICE_TOTAL_MEM = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "cuDeviceTotalMem_v2", "cuDeviceTotalMem");
            CU_DEVICE_GET_ATTRIBUTE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "cuDeviceGetAttribute");

            // cuCtxCreate_v4 takes an extra CUctxCreateParams* in slot 2; the backend never uses
            // execution affinity or CIG, so the _v2 shape is the one bound here.
            CU_CTX_CREATE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "cuCtxCreate_v2", "cuCtxCreate");
            CU_CTX_DESTROY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuCtxDestroy_v2", "cuCtxDestroy");
            CU_CTX_SET_CURRENT = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuCtxSetCurrent");
            CU_CTX_SYNCHRONIZE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT), "cuCtxSynchronize");

            CU_STREAM_CREATE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "cuStreamCreate");
            CU_STREAM_DESTROY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuStreamDestroy_v2", "cuStreamDestroy");
            CU_STREAM_SYNCHRONIZE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuStreamSynchronize");
            CU_STREAM_WAIT_EVENT = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT), "cuStreamWaitEvent");

            CU_EVENT_CREATE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "cuEventCreate");
            CU_EVENT_DESTROY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuEventDestroy_v2", "cuEventDestroy");
            CU_EVENT_RECORD = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cuEventRecord");
            CU_EVENT_QUERY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuEventQuery");
            CU_EVENT_SYNCHRONIZE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuEventSynchronize");
            CU_EVENT_ELAPSED_TIME = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG), "cuEventElapsedTime_v2", "cuEventElapsedTime");

            CU_MEM_ALLOC = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "cuMemAlloc_v2", "cuMemAlloc");
            CU_MEM_FREE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuMemFree_v2", "cuMemFree");
            CU_MEM_ALLOC_HOST = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "cuMemAllocHost_v2", "cuMemAllocHost");
            CU_MEM_FREE_HOST = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuMemFreeHost");
            CU_MEM_HOST_REGISTER = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT), "cuMemHostRegister_v2", "cuMemHostRegister");
            CU_MEM_HOST_UNREGISTER = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuMemHostUnregister");
            CU_MEMCPY_HTOD_ASYNC = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG), "cuMemcpyHtoDAsync_v2", "cuMemcpyHtoDAsync");
            CU_MEMCPY_DTOH_ASYNC = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG), "cuMemcpyDtoHAsync_v2", "cuMemcpyDtoHAsync");
            CU_MEMCPY_DTOD = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "cuMemcpyDtoD_v2", "cuMemcpyDtoD");
            CU_MEMSET_D8 = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_CHAR, C_LONG), "cuMemsetD8_v2", "cuMemsetD8");

            CU_MODULE_LOAD_DATA_EX = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER), "cuModuleLoadDataEx");
            CU_MODULE_GET_FUNCTION = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_POINTER), "cuModuleGetFunction");
            CU_MODULE_UNLOAD = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuModuleUnload");
            CU_LAUNCH_KERNEL = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_LONG, C_POINTER, C_POINTER), "cuLaunchKernel");
            CU_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG, C_LONG, C_LONG, C_INT), "cuOccupancyMaxPotentialBlockSize");

            CU_STREAM_BEGIN_CAPTURE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_INT), "cuStreamBeginCapture_v2", "cuStreamBeginCapture");
            CU_STREAM_END_CAPTURE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "cuStreamEndCapture");
            CU_STREAM_IS_CAPTURING = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "cuStreamIsCapturing");
            CU_GRAPH_INSTANTIATE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG), "cuGraphInstantiateWithFlags");
            CU_GRAPH_EXEC_UPDATE = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER), "cuGraphExecUpdate_v2");
            CU_GRAPH_LAUNCH = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cuGraphLaunch");
            CU_GRAPH_EXEC_DESTROY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuGraphExecDestroy");
            CU_GRAPH_DESTROY = downcall(LIBCUDA, FunctionDescriptor.of(C_INT, C_LONG), "cuGraphDestroy");
        }
    }

    private CUDADriverAPI() {
    }

    /** Whether libcuda could be opened at all; false on a machine with no NVIDIA driver. */
    public static boolean isAvailable() {
        return LIBCUDA != null && CU_INIT != null;
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

    public static int cuInit(int flags) {
        try {
            return (int) CU_INIT.invokeExact(flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDriverGetVersion(MemorySegment driverVersion) {
        try {
            return (int) CU_DRIVER_GET_VERSION.invokeExact(driverVersion);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGetErrorName(int error, MemorySegment str) {
        try {
            return (int) CU_GET_ERROR_NAME.invokeExact(error, str);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGetErrorString(int error, MemorySegment str) {
        try {
            return (int) CU_GET_ERROR_STRING.invokeExact(error, str);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDeviceGet(MemorySegment device, int ordinal) {
        try {
            return (int) CU_DEVICE_GET.invokeExact(device, ordinal);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDeviceGetCount(MemorySegment count) {
        try {
            return (int) CU_DEVICE_GET_COUNT.invokeExact(count);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDeviceGetName(MemorySegment name, int len, int device) {
        try {
            return (int) CU_DEVICE_GET_NAME.invokeExact(name, len, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDeviceTotalMem(MemorySegment bytes, int device) {
        try {
            return (int) CU_DEVICE_TOTAL_MEM.invokeExact(bytes, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuDeviceGetAttribute(MemorySegment value, int attribute, int device) {
        try {
            return (int) CU_DEVICE_GET_ATTRIBUTE.invokeExact(value, attribute, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuCtxCreate(MemorySegment context, int flags, int device) {
        try {
            return (int) CU_CTX_CREATE.invokeExact(context, flags, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuCtxDestroy(long context) {
        try {
            return (int) CU_CTX_DESTROY.invokeExact(context);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuCtxSetCurrent(long context) {
        try {
            return (int) CU_CTX_SET_CURRENT.invokeExact(context);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuCtxSynchronize() {
        try {
            return (int) CU_CTX_SYNCHRONIZE.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamCreate(MemorySegment stream, int flags) {
        try {
            return (int) CU_STREAM_CREATE.invokeExact(stream, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamDestroy(long stream) {
        try {
            return (int) CU_STREAM_DESTROY.invokeExact(stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamSynchronize(long stream) {
        try {
            return (int) CU_STREAM_SYNCHRONIZE.invokeExact(stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamWaitEvent(long stream, long event, int flags) {
        try {
            return (int) CU_STREAM_WAIT_EVENT.invokeExact(stream, event, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventCreate(MemorySegment event, int flags) {
        try {
            return (int) CU_EVENT_CREATE.invokeExact(event, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventDestroy(long event) {
        try {
            return (int) CU_EVENT_DESTROY.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventRecord(long event, long stream) {
        try {
            return (int) CU_EVENT_RECORD.invokeExact(event, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventQuery(long event) {
        try {
            return (int) CU_EVENT_QUERY.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventSynchronize(long event) {
        try {
            return (int) CU_EVENT_SYNCHRONIZE.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuEventElapsedTime(MemorySegment milliseconds, long start, long end) {
        try {
            return (int) CU_EVENT_ELAPSED_TIME.invokeExact(milliseconds, start, end);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemAlloc(MemorySegment devicePointer, long byteSize) {
        try {
            return (int) CU_MEM_ALLOC.invokeExact(devicePointer, byteSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemFree(long devicePointer) {
        try {
            return (int) CU_MEM_FREE.invokeExact(devicePointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemAllocHost(MemorySegment hostPointer, long byteSize) {
        try {
            return (int) CU_MEM_ALLOC_HOST.invokeExact(hostPointer, byteSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemFreeHost(long hostPointer) {
        try {
            return (int) CU_MEM_FREE_HOST.invokeExact(hostPointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemHostRegister(long hostPointer, long byteSize, int flags) {
        try {
            return (int) CU_MEM_HOST_REGISTER.invokeExact(hostPointer, byteSize, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemHostUnregister(long hostPointer) {
        try {
            return (int) CU_MEM_HOST_UNREGISTER.invokeExact(hostPointer);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemcpyHtoDAsync(long destinationDevice, long sourceHost, long byteCount, long stream) {
        try {
            return (int) CU_MEMCPY_HTOD_ASYNC.invokeExact(destinationDevice, sourceHost, byteCount, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemcpyDtoHAsync(long destinationHost, long sourceDevice, long byteCount, long stream) {
        try {
            return (int) CU_MEMCPY_DTOH_ASYNC.invokeExact(destinationHost, sourceDevice, byteCount, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemcpyDtoD(long destinationDevice, long sourceDevice, long byteCount) {
        try {
            return (int) CU_MEMCPY_DTOD.invokeExact(destinationDevice, sourceDevice, byteCount);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuMemsetD8(long destinationDevice, byte value, long count) {
        try {
            return (int) CU_MEMSET_D8.invokeExact(destinationDevice, value, count);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuModuleLoadDataEx(MemorySegment module, MemorySegment image, int numOptions, MemorySegment options, MemorySegment optionValues) {
        try {
            return (int) CU_MODULE_LOAD_DATA_EX.invokeExact(module, image, numOptions, options, optionValues);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuModuleGetFunction(MemorySegment function, long module, MemorySegment name) {
        try {
            return (int) CU_MODULE_GET_FUNCTION.invokeExact(function, module, name);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuModuleUnload(long module) {
        try {
            return (int) CU_MODULE_UNLOAD.invokeExact(module);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuLaunchKernel(long function, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, int sharedMemBytes, long stream, MemorySegment kernelParams,
            MemorySegment extra) {
        try {
            return (int) CU_LAUNCH_KERNEL.invokeExact(function, gridDimX, gridDimY, gridDimZ, blockDimX, blockDimY, blockDimZ, sharedMemBytes, stream, kernelParams, extra);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuOccupancyMaxPotentialBlockSize(MemorySegment minGridSize, MemorySegment blockSize, long function, long blockSizeToDynamicSMemSize, long dynamicSMemSize, int blockSizeLimit) {
        try {
            return (int) CU_OCCUPANCY_MAX_POTENTIAL_BLOCK_SIZE.invokeExact(minGridSize, blockSize, function, blockSizeToDynamicSMemSize, dynamicSMemSize, blockSizeLimit);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamBeginCapture(long stream, int mode) {
        if (CU_STREAM_BEGIN_CAPTURE == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_STREAM_BEGIN_CAPTURE.invokeExact(stream, mode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamEndCapture(long stream, MemorySegment graph) {
        if (CU_STREAM_END_CAPTURE == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_STREAM_END_CAPTURE.invokeExact(stream, graph);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuStreamIsCapturing(long stream, MemorySegment captureStatus) {
        if (CU_STREAM_IS_CAPTURING == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_STREAM_IS_CAPTURING.invokeExact(stream, captureStatus);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGraphInstantiateWithFlags(MemorySegment graphExec, long graph, long flags) {
        if (CU_GRAPH_INSTANTIATE == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_GRAPH_INSTANTIATE.invokeExact(graphExec, graph, flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGraphExecUpdate(long graphExec, long graph, MemorySegment resultInfo) {
        if (CU_GRAPH_EXEC_UPDATE == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_GRAPH_EXEC_UPDATE.invokeExact(graphExec, graph, resultInfo);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGraphLaunch(long graphExec, long stream) {
        if (CU_GRAPH_LAUNCH == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_GRAPH_LAUNCH.invokeExact(graphExec, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGraphExecDestroy(long graphExec) {
        if (CU_GRAPH_EXEC_DESTROY == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_GRAPH_EXEC_DESTROY.invokeExact(graphExec);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int cuGraphDestroy(long graph) {
        if (CU_GRAPH_DESTROY == null) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
        try {
            return (int) CU_GRAPH_DESTROY.invokeExact(graph);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** The {@code CUDA_ERROR_*} name for a CUresult, or the bare number when the driver has none. */
    public static String errorName(int result) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = FFMSupport.allocatePointer(arena);
            if (cuGetErrorName(result, out) != CUDA_SUCCESS) {
                return Integer.toString(result);
            }
            String name = FFMSupport.readCString(out.get(C_POINTER, 0));
            return name == null ? Integer.toString(result) : name;
        }
    }

    /**
     * The message a failed driver call is reported with: the call that failed, the
     * {@code CUDA_ERROR_*} name and numeric CUresult, and the driver's own description of it.
     */
    public static String describe(String call, int result) {
        StringBuilder message = new StringBuilder(call).append(" failed: ").append(errorName(result)).append(" (").append(result).append(")");
        String text = errorString(result);
        if (text != null) {
            message.append(" - ").append(text);
        }
        return message.toString();
    }

    /** The human-readable description for a CUresult, or {@code null} when the driver has none. */
    public static String errorString(int result) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = FFMSupport.allocatePointer(arena);
            if (cuGetErrorString(result, out) != CUDA_SUCCESS) {
                return null;
            }
            return FFMSupport.readCString(out.get(C_POINTER, 0));
        }
    }
}
