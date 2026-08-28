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
package uk.ac.manchester.tornado.drivers.opencl.ffm;

import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.downcall;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;

/**
 * Panama bindings for the OpenCL entry points the backend uses, replacing the hand-written
 * {@code libtornado-opencl} JNI library.
 *
 * <p>
 * Unlike the CUDA backend, whose JNI layer had to emulate OpenCL semantics on top of the CUDA
 * driver API, this is a straight binding: the Java layer already speaks OpenCL, so each method here
 * is the C function with the same name and shape. Handles ({@code cl_platform_id},
 * {@code cl_device_id}, {@code cl_context}, {@code cl_command_queue}, {@code cl_mem},
 * {@code cl_program}, {@code cl_kernel}, {@code cl_event}) are already carried as {@code long} by
 * the Java layer and are bound as {@code JAVA_LONG}; on every ABI Panama supports a pointer and a
 * 64-bit integer are passed in the same register class. Genuine memory arguments -- out parameters,
 * host buffers, work-size and event vectors -- are bound as {@code ADDRESS}, so Panama keeps the
 * owning arena alive across the call.
 *
 * <p>
 * The ICD loader exports one stable, unversioned symbol per entry point, so unlike the CUDA driver
 * API there are no versioned symbol aliases to pick between here.
 */
public final class OpenCLAPI {

    /** {@code CL_SUCCESS}. */
    public static final int CL_SUCCESS = 0;
    /** {@code CL_TRUE} / {@code CL_FALSE}, the {@code cl_bool} values. */
    public static final int CL_TRUE = 1;
    public static final int CL_FALSE = 0;

    private static final SymbolLookup LIBOPENCL = FFMSupport.loadLibrary("libOpenCL.so.1", "libOpenCL.so", "OpenCL.dll", "/System/Library/Frameworks/OpenCL.framework/OpenCL", "libOpenCL.dylib");

    private static final MethodHandle CL_GET_PLATFORM_IDS;
    private static final MethodHandle CL_GET_PLATFORM_INFO;
    private static final MethodHandle CL_GET_DEVICE_IDS;
    private static final MethodHandle CL_GET_DEVICE_INFO;

    private static final MethodHandle CL_CREATE_CONTEXT;
    private static final MethodHandle CL_RELEASE_CONTEXT;
    private static final MethodHandle CL_GET_CONTEXT_INFO;

    private static final MethodHandle CL_CREATE_COMMAND_QUEUE;
    private static final MethodHandle CL_RELEASE_COMMAND_QUEUE;
    private static final MethodHandle CL_GET_COMMAND_QUEUE_INFO;
    private static final MethodHandle CL_FLUSH;
    private static final MethodHandle CL_FINISH;

    private static final MethodHandle CL_CREATE_BUFFER;
    private static final MethodHandle CL_CREATE_SUB_BUFFER;
    private static final MethodHandle CL_RELEASE_MEM_OBJECT;

    private static final MethodHandle CL_CREATE_PROGRAM_WITH_SOURCE;
    private static final MethodHandle CL_CREATE_PROGRAM_WITH_BINARY;
    private static final MethodHandle CL_CREATE_PROGRAM_WITH_IL;
    private static final MethodHandle CL_BUILD_PROGRAM;
    private static final MethodHandle CL_RELEASE_PROGRAM;
    private static final MethodHandle CL_GET_PROGRAM_INFO;
    private static final MethodHandle CL_GET_PROGRAM_BUILD_INFO;

    private static final MethodHandle CL_CREATE_KERNEL;
    private static final MethodHandle CL_RELEASE_KERNEL;
    private static final MethodHandle CL_SET_KERNEL_ARG;
    private static final MethodHandle CL_GET_KERNEL_INFO;

    private static final MethodHandle CL_ENQUEUE_ND_RANGE_KERNEL;
    private static final MethodHandle CL_ENQUEUE_READ_BUFFER;
    private static final MethodHandle CL_ENQUEUE_WRITE_BUFFER;
    private static final MethodHandle CL_ENQUEUE_MAP_BUFFER;
    private static final MethodHandle CL_ENQUEUE_UNMAP_MEM_OBJECT;
    private static final MethodHandle CL_ENQUEUE_MARKER_WITH_WAIT_LIST;
    private static final MethodHandle CL_ENQUEUE_BARRIER_WITH_WAIT_LIST;

    private static final MethodHandle CL_WAIT_FOR_EVENTS;
    private static final MethodHandle CL_GET_EVENT_INFO;
    private static final MethodHandle CL_GET_EVENT_PROFILING_INFO;
    private static final MethodHandle CL_RELEASE_EVENT;

    static {
        if (LIBOPENCL == null) {
            CL_GET_PLATFORM_IDS = null;
            CL_GET_PLATFORM_INFO = null;
            CL_GET_DEVICE_IDS = null;
            CL_GET_DEVICE_INFO = null;
            CL_CREATE_CONTEXT = null;
            CL_RELEASE_CONTEXT = null;
            CL_GET_CONTEXT_INFO = null;
            CL_CREATE_COMMAND_QUEUE = null;
            CL_RELEASE_COMMAND_QUEUE = null;
            CL_GET_COMMAND_QUEUE_INFO = null;
            CL_FLUSH = null;
            CL_FINISH = null;
            CL_CREATE_BUFFER = null;
            CL_CREATE_SUB_BUFFER = null;
            CL_RELEASE_MEM_OBJECT = null;
            CL_CREATE_PROGRAM_WITH_SOURCE = null;
            CL_CREATE_PROGRAM_WITH_BINARY = null;
            CL_CREATE_PROGRAM_WITH_IL = null;
            CL_BUILD_PROGRAM = null;
            CL_RELEASE_PROGRAM = null;
            CL_GET_PROGRAM_INFO = null;
            CL_GET_PROGRAM_BUILD_INFO = null;
            CL_CREATE_KERNEL = null;
            CL_RELEASE_KERNEL = null;
            CL_SET_KERNEL_ARG = null;
            CL_GET_KERNEL_INFO = null;
            CL_ENQUEUE_ND_RANGE_KERNEL = null;
            CL_ENQUEUE_READ_BUFFER = null;
            CL_ENQUEUE_WRITE_BUFFER = null;
            CL_ENQUEUE_MAP_BUFFER = null;
            CL_ENQUEUE_UNMAP_MEM_OBJECT = null;
            CL_ENQUEUE_MARKER_WITH_WAIT_LIST = null;
            CL_ENQUEUE_BARRIER_WITH_WAIT_LIST = null;
            CL_WAIT_FOR_EVENTS = null;
            CL_GET_EVENT_INFO = null;
            CL_GET_EVENT_PROFILING_INFO = null;
            CL_RELEASE_EVENT = null;
        } else {
            CL_GET_PLATFORM_IDS = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_INT, C_POINTER, C_POINTER), "clGetPlatformIDs");
            CL_GET_PLATFORM_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetPlatformInfo");
            CL_GET_DEVICE_IDS = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "clGetDeviceIDs");
            CL_GET_DEVICE_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetDeviceInfo");

            CL_CREATE_CONTEXT = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER), "clCreateContext");
            CL_RELEASE_CONTEXT = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseContext");
            CL_GET_CONTEXT_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetContextInfo");

            // Deprecated in OpenCL 2.0 in favour of clCreateCommandQueueWithProperties, but every
            // ICD still exports it and it is the one that takes the property bitfield the Java
            // layer already carries.
            CL_CREATE_COMMAND_QUEUE = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_LONG, C_LONG, C_POINTER), "clCreateCommandQueue");
            CL_RELEASE_COMMAND_QUEUE = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseCommandQueue");
            CL_GET_COMMAND_QUEUE_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetCommandQueueInfo");
            CL_FLUSH = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clFlush");
            CL_FINISH = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clFinish");

            CL_CREATE_BUFFER = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER), "clCreateBuffer");
            CL_CREATE_SUB_BUFFER = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "clCreateSubBuffer");
            CL_RELEASE_MEM_OBJECT = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseMemObject");

            CL_CREATE_PROGRAM_WITH_SOURCE = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_INT, C_POINTER, C_POINTER, C_POINTER), "clCreateProgramWithSource");
            CL_CREATE_PROGRAM_WITH_BINARY = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER), "clCreateProgramWithBinary");
            // OpenCL 2.1; absent on 1.2 ICDs, where SPIR-V ingestion is simply unsupported.
            CL_CREATE_PROGRAM_WITH_IL = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_POINTER, C_LONG, C_POINTER), "clCreateProgramWithIL");
            CL_BUILD_PROGRAM = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER), "clBuildProgram");
            CL_RELEASE_PROGRAM = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseProgram");
            CL_GET_PROGRAM_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetProgramInfo");
            CL_GET_PROGRAM_BUILD_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetProgramBuildInfo");

            CL_CREATE_KERNEL = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_POINTER, C_POINTER), "clCreateKernel");
            CL_RELEASE_KERNEL = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseKernel");
            CL_SET_KERNEL_ARG = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER), "clSetKernelArg");
            CL_GET_KERNEL_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetKernelInfo");

            CL_ENQUEUE_ND_RANGE_KERNEL = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER),
                    "clEnqueueNDRangeKernel");
            CL_ENQUEUE_READ_BUFFER = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "clEnqueueReadBuffer");
            CL_ENQUEUE_WRITE_BUFFER = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "clEnqueueWriteBuffer");
            CL_ENQUEUE_MAP_BUFFER = downcall(LIBOPENCL, FunctionDescriptor.of(C_POINTER, C_LONG, C_LONG, C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER, C_POINTER),
                    "clEnqueueMapBuffer");
            CL_ENQUEUE_UNMAP_MEM_OBJECT = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "clEnqueueUnmapMemObject");
            CL_ENQUEUE_MARKER_WITH_WAIT_LIST = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_POINTER), "clEnqueueMarkerWithWaitList");
            CL_ENQUEUE_BARRIER_WITH_WAIT_LIST = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_POINTER), "clEnqueueBarrierWithWaitList");

            CL_WAIT_FOR_EVENTS = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_INT, C_POINTER), "clWaitForEvents");
            CL_GET_EVENT_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetEventInfo");
            CL_GET_EVENT_PROFILING_INFO = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_POINTER, C_POINTER), "clGetEventProfilingInfo");
            CL_RELEASE_EVENT = downcall(LIBOPENCL, FunctionDescriptor.of(C_INT, C_LONG), "clReleaseEvent");
        }
    }

    private OpenCLAPI() {
    }

    /** Whether an OpenCL ICD loader could be opened at all. */
    public static boolean isAvailable() {
        return LIBOPENCL != null && CL_GET_PLATFORM_IDS != null;
    }

    /** SPIR-V ingestion needs OpenCL 2.1; 1.2 ICDs do not export it. */
    public static boolean hasCreateProgramWithIL() {
        return CL_CREATE_PROGRAM_WITH_IL != null;
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

    public static int clGetPlatformIDs(int numEntries, MemorySegment platforms, MemorySegment numPlatforms) {
        try {
            return (int) CL_GET_PLATFORM_IDS.invokeExact(numEntries, platforms, numPlatforms);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetPlatformInfo(long platform, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_PLATFORM_INFO.invokeExact(platform, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetDeviceIDs(long platform, long deviceType, int numEntries, MemorySegment devices, MemorySegment numDevices) {
        try {
            return (int) CL_GET_DEVICE_IDS.invokeExact(platform, deviceType, numEntries, devices, numDevices);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetDeviceInfo(long device, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_DEVICE_INFO.invokeExact(device, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateContext(MemorySegment properties, int numDevices, MemorySegment devices, MemorySegment callback, MemorySegment userData, MemorySegment errcodeRet) {
        try {
            MemorySegment context = (MemorySegment) CL_CREATE_CONTEXT.invokeExact(properties, numDevices, devices, callback, userData, errcodeRet);
            return context.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseContext(long context) {
        try {
            return (int) CL_RELEASE_CONTEXT.invokeExact(context);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetContextInfo(long context, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_CONTEXT_INFO.invokeExact(context, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateCommandQueue(long context, long device, long properties, MemorySegment errcodeRet) {
        try {
            MemorySegment queue = (MemorySegment) CL_CREATE_COMMAND_QUEUE.invokeExact(context, device, properties, errcodeRet);
            return queue.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseCommandQueue(long queue) {
        try {
            return (int) CL_RELEASE_COMMAND_QUEUE.invokeExact(queue);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetCommandQueueInfo(long queue, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_COMMAND_QUEUE_INFO.invokeExact(queue, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clFlush(long queue) {
        try {
            return (int) CL_FLUSH.invokeExact(queue);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clFinish(long queue) {
        try {
            return (int) CL_FINISH.invokeExact(queue);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateBuffer(long context, long flags, long size, long hostPointer, MemorySegment errcodeRet) {
        try {
            MemorySegment buffer = (MemorySegment) CL_CREATE_BUFFER.invokeExact(context, flags, size, hostPointer, errcodeRet);
            return buffer.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateSubBuffer(long buffer, long flags, int createType, MemorySegment createInfo, MemorySegment errcodeRet) {
        try {
            MemorySegment subBuffer = (MemorySegment) CL_CREATE_SUB_BUFFER.invokeExact(buffer, flags, createType, createInfo, errcodeRet);
            return subBuffer.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseMemObject(long memory) {
        try {
            return (int) CL_RELEASE_MEM_OBJECT.invokeExact(memory);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateProgramWithSource(long context, int count, MemorySegment strings, MemorySegment lengths, MemorySegment errcodeRet) {
        try {
            MemorySegment program = (MemorySegment) CL_CREATE_PROGRAM_WITH_SOURCE.invokeExact(context, count, strings, lengths, errcodeRet);
            return program.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateProgramWithBinary(long context, int numDevices, MemorySegment devices, MemorySegment lengths, MemorySegment binaries, MemorySegment binaryStatus,
            MemorySegment errcodeRet) {
        try {
            MemorySegment program = (MemorySegment) CL_CREATE_PROGRAM_WITH_BINARY.invokeExact(context, numDevices, devices, lengths, binaries, binaryStatus, errcodeRet);
            return program.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateProgramWithIL(long context, MemorySegment il, long length, MemorySegment errcodeRet) {
        if (CL_CREATE_PROGRAM_WITH_IL == null) {
            return 0;
        }
        try {
            MemorySegment program = (MemorySegment) CL_CREATE_PROGRAM_WITH_IL.invokeExact(context, il, length, errcodeRet);
            return program.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clBuildProgram(long program, int numDevices, MemorySegment devices, MemorySegment options, MemorySegment callback, MemorySegment userData) {
        try {
            return (int) CL_BUILD_PROGRAM.invokeExact(program, numDevices, devices, options, callback, userData);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseProgram(long program) {
        try {
            return (int) CL_RELEASE_PROGRAM.invokeExact(program);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetProgramInfo(long program, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_PROGRAM_INFO.invokeExact(program, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetProgramBuildInfo(long program, long device, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_PROGRAM_BUILD_INFO.invokeExact(program, device, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clCreateKernel(long program, MemorySegment kernelName, MemorySegment errcodeRet) {
        try {
            MemorySegment kernel = (MemorySegment) CL_CREATE_KERNEL.invokeExact(program, kernelName, errcodeRet);
            return kernel.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseKernel(long kernel) {
        try {
            return (int) CL_RELEASE_KERNEL.invokeExact(kernel);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clSetKernelArg(long kernel, int index, long size, MemorySegment value) {
        try {
            return (int) CL_SET_KERNEL_ARG.invokeExact(kernel, index, size, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetKernelInfo(long kernel, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_KERNEL_INFO.invokeExact(kernel, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueNDRangeKernel(long queue, long kernel, int workDim, MemorySegment globalWorkOffset, MemorySegment globalWorkSize, MemorySegment localWorkSize, int numEventsInWaitList,
            MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_ND_RANGE_KERNEL.invokeExact(queue, kernel, workDim, globalWorkOffset, globalWorkSize, localWorkSize, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueReadBuffer(long queue, long buffer, int blocking, long offset, long size, long pointer, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_READ_BUFFER.invokeExact(queue, buffer, blocking, offset, size, pointer, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueWriteBuffer(long queue, long buffer, int blocking, long offset, long size, long pointer, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_WRITE_BUFFER.invokeExact(queue, buffer, blocking, offset, size, pointer, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long clEnqueueMapBuffer(long queue, long buffer, int blocking, long mapFlags, long offset, long size, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event,
            MemorySegment errcodeRet) {
        try {
            MemorySegment mapped = (MemorySegment) CL_ENQUEUE_MAP_BUFFER.invokeExact(queue, buffer, blocking, mapFlags, offset, size, numEventsInWaitList, eventWaitList, event, errcodeRet);
            return mapped.address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueUnmapMemObject(long queue, long memory, long mappedPointer, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_UNMAP_MEM_OBJECT.invokeExact(queue, memory, mappedPointer, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueMarkerWithWaitList(long queue, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_MARKER_WITH_WAIT_LIST.invokeExact(queue, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clEnqueueBarrierWithWaitList(long queue, int numEventsInWaitList, MemorySegment eventWaitList, MemorySegment event) {
        try {
            return (int) CL_ENQUEUE_BARRIER_WITH_WAIT_LIST.invokeExact(queue, numEventsInWaitList, eventWaitList, event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clWaitForEvents(int numEvents, MemorySegment events) {
        try {
            return (int) CL_WAIT_FOR_EVENTS.invokeExact(numEvents, events);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetEventInfo(long event, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_EVENT_INFO.invokeExact(event, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clGetEventProfilingInfo(long event, int paramName, long paramValueSize, MemorySegment paramValue, MemorySegment paramValueSizeRet) {
        try {
            return (int) CL_GET_EVENT_PROFILING_INFO.invokeExact(event, paramName, paramValueSize, paramValue, paramValueSizeRet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int clReleaseEvent(long event) {
        try {
            return (int) CL_RELEASE_EVENT.invokeExact(event);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
