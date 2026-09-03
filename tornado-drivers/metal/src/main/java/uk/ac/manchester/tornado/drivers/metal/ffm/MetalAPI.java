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
package uk.ac.manchester.tornado.drivers.metal.ffm;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Metal, reached through the Objective-C runtime rather than a JNI shim.
 *
 * <p>
 * Every method here is one Objective-C message send, named after the selector it sends and paired
 * with the signature that send has to be made through -- see {@link ObjCRuntime} for why the
 * signature has to be spelled out per selector rather than shared.
 *
 * <p>
 * The shapes come from the Metal headers and from the {@code objc_metal_jni.mm} shim this replaces.
 * The backend runs on these sends on Apple silicon; the completion-handler block of
 * {@code addCompletedHandler:} is intentionally not covered (the synchronous
 * {@link #waitUntilCompleted} the backend uses is enough -- see the note there), and the
 * {@code MTLSize} by-value arguments to {@code dispatchThreads:threadsPerThreadgroup:} are the one
 * argument-passing case worth re-reading if this is ever ported to an Intel Mac.
 */
public final class MetalAPI {

    /** {@code MTLResourceStorageModeShared}, the mode the CPU and GPU both address. */
    public static final long MTL_RESOURCE_STORAGE_MODE_SHARED = 0L;
    /** {@code MTLResourceStorageModePrivate}, GPU-only, needing a blit to reach. */
    public static final long MTL_RESOURCE_STORAGE_MODE_PRIVATE = 2L << 4;

    /** {@code MTLPipelineOptionArgumentInfo}, the reflection bit for pipeline creation. */
    public static final long MTL_PIPELINE_OPTION_ARGUMENT_INFO = 1L;

    private static final MethodHandle MTL_CREATE_SYSTEM_DEFAULT_DEVICE;
    private static final MethodHandle MTL_COPY_ALL_DEVICES;

    static {
        if (!ObjCRuntime.isAvailable()) {
            MTL_CREATE_SYSTEM_DEFAULT_DEVICE = null;
            MTL_COPY_ALL_DEVICES = null;
        } else {
            // The two entry points into Metal that are plain C functions rather than message sends.
            MTL_CREATE_SYSTEM_DEFAULT_DEVICE = FFMSupport.downcall(ObjCRuntime.metal(), FunctionDescriptor.of(C_LONG), "MTLCreateSystemDefaultDevice");
            // macOS only; absent on iOS. Returns an owned NSArray, so the caller releases it.
            MTL_COPY_ALL_DEVICES = FFMSupport.downcall(ObjCRuntime.metal(), FunctionDescriptor.of(C_LONG), "MTLCopyAllDevices");
        }
    }

    private MetalAPI() {
    }

    /** Whether Metal and the Objective-C runtime are both present on this host. */
    public static boolean isAvailable() {
        return ObjCRuntime.isAvailable() && MTL_CREATE_SYSTEM_DEFAULT_DEVICE != null;
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

    /* ---- Device discovery ---- */

    /** {@code MTLCreateSystemDefaultDevice()}. Owned by the caller. */
    public static long createSystemDefaultDevice() {
        if (MTL_CREATE_SYSTEM_DEFAULT_DEVICE == null) {
            return 0;
        }
        try {
            return (long) MTL_CREATE_SYSTEM_DEFAULT_DEVICE.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code MTLCopyAllDevices()}: an owned {@code NSArray<id<MTLDevice>>}; release when done. */
    public static long copyAllDevices() {
        if (MTL_COPY_ALL_DEVICES == null) {
            return 0;
        }
        try {
            return (long) MTL_COPY_ALL_DEVICES.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[NSArray count]}. */
    public static long arrayCount(long array) {
        return ObjCRuntime.send(array, "count");
    }

    /** {@code -[NSArray objectAtIndex:]}; the element is not owned by the caller. */
    public static long arrayObjectAtIndex(long array, long index) {
        return ObjCRuntime.send(array, "objectAtIndex:", index);
    }

    /* ---- Device properties ---- */

    /** {@code -[MTLDevice name]} as a Java string. */
    public static String deviceName(long device) {
        return ObjCRuntime.toJavaString(ObjCRuntime.send(device, "name"));
    }

    /** {@code -[MTLDevice recommendedMaxWorkingSetSize]}, the closest thing to a memory size. */
    public static long deviceRecommendedMaxWorkingSetSize(long device) {
        return ObjCRuntime.send(device, "recommendedMaxWorkingSetSize");
    }

    /** {@code -[MTLDevice maxThreadgroupMemoryLength]}, the threadgroup (local) memory budget. */
    public static long deviceMaxThreadgroupMemoryLength(long device) {
        return ObjCRuntime.send(device, "maxThreadgroupMemoryLength");
    }

    /** {@code -[MTLDevice hasUnifiedMemory]}. */
    public static boolean deviceHasUnifiedMemory(long device) {
        return ObjCRuntime.sendBoolean(device, "hasUnifiedMemory");
    }

    /**
     * {@code -[MTLDevice maxThreadsPerThreadgroup]}, an {@code MTLSize} returned by value.
     *
     * <p>
     * This is the one struct-returning send in the set, which is why it goes through
     * {@link ObjCRuntime#msgSendStret}: on x86_64 a 24-byte return uses a different trampoline.
     * The segment is allocated from {@code arena} because Panama materialises a by-value struct
     * return into caller-provided memory.
     */
    public static MemorySegment deviceMaxThreadsPerThreadgroup(Arena arena, long device) {
        try {
            MethodHandle handle = ObjCRuntime.msgSendStret(FunctionDescriptor.of(ObjCRuntime.MTL_SIZE, C_LONG, C_LONG));
            return (MemorySegment) handle.invoke(arena, device, ObjCRuntime.sel("maxThreadsPerThreadgroup"));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /* ---- Queues, buffers ---- */

    /** {@code -[MTLDevice newCommandQueue]}. Owned by the caller. */
    public static long newCommandQueue(long device) {
        return ObjCRuntime.send(device, "newCommandQueue");
    }

    /** {@code -[MTLDevice newCommandQueueWithMaxCommandBufferCount:]}. Owned by the caller. */
    public static long newCommandQueueWithMaxCommandBufferCount(long device, long maxInFlight) {
        return ObjCRuntime.send(device, "newCommandQueueWithMaxCommandBufferCount:", maxInFlight);
    }

    /** {@code -[MTLDevice newBufferWithLength:options:]}. Owned by the caller. */
    public static long newBufferWithLength(long device, long length, long options) {
        return ObjCRuntime.send(device, "newBufferWithLength:options:", length, options);
    }

    /**
     * {@code -[MTLDevice newBufferWithBytes:length:options:]}: Metal copies the bytes, so the
     * source segment does not have to outlive the call.
     */
    public static long newBufferWithBytes(long device, MemorySegment bytes, long length, long options) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG));
            return (long) handle.invokeExact(device, ObjCRuntime.sel("newBufferWithBytes:length:options:"), bytes, length, options);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code -[MTLBuffer contents]}: the CPU-visible address of a shared-storage buffer, which is
     * how a transfer becomes a plain memory copy rather than a blit. Returns 0 for a private
     * buffer, where it is not addressable from the CPU at all.
     */
    public static long bufferContents(long buffer) {
        return ObjCRuntime.send(buffer, "contents");
    }

    /** {@code -[MTLBuffer length]}. */
    public static long bufferLength(long buffer) {
        return ObjCRuntime.send(buffer, "length");
    }

    /* ---- Libraries, functions, pipelines ---- */

    /**
     * {@code -[MTLDevice newLibraryWithSource:options:error:]}, the MSL compile.
     *
     * <p>
     * {@code error} is an out-parameter taking the address of an {@code NSError *}; on failure the
     * library is nil and the error's {@code -localizedDescription} carries the compiler diagnostic,
     * which the caller should surface rather than reporting a bare nil.
     */
    public static long newLibraryWithSource(long device, long sourceNSString, long options, MemorySegment error) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER));
            return (long) handle.invokeExact(device, ObjCRuntime.sel("newLibraryWithSource:options:error:"), sourceNSString, options, error);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code -[MTLDevice newLibraryWithData:error:]}, building a library from a compiled
     * {@code .metallib}. {@code data} is a {@code dispatch_data_t}; {@code error} takes the address
     * of an {@code NSError *}. Owned by the caller.
     */
    public static long newLibraryWithData(long device, long data, MemorySegment error) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER));
            return (long) handle.invokeExact(device, ObjCRuntime.sel("newLibraryWithData:error:"), data, error);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLLibrary newFunctionWithName:]}. Owned by the caller. */
    public static long newFunctionWithName(long library, long nameNSString) {
        return ObjCRuntime.send(library, "newFunctionWithName:", nameNSString);
    }

    /** {@code -[MTLDevice newComputePipelineStateWithFunction:error:]}. Owned by the caller. */
    public static long newComputePipelineStateWithFunction(long device, long function, MemorySegment error) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER));
            return (long) handle.invokeExact(device, ObjCRuntime.sel("newComputePipelineStateWithFunction:error:"), function, error);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code -[MTLDevice newComputePipelineStateWithFunction:options:reflection:error:]}: the
     * reflection-carrying overload. {@code reflection} takes the address of a
     * {@code MTLComputePipelineReflection *} out-slot and {@code error} the address of an
     * {@code NSError *}; the pipeline is owned by the caller.
     */
    public static long newComputePipelineStateWithFunctionReflection(long device, long function, long options, MemorySegment reflection, MemorySegment error) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER, C_POINTER));
            return (long) handle.invokeExact(device, ObjCRuntime.sel("newComputePipelineStateWithFunction:options:reflection:error:"), function, options, reflection, error);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLComputePipelineState threadExecutionWidth]}, the SIMD width used as a default group size. */
    public static long pipelineThreadExecutionWidth(long pipeline) {
        return ObjCRuntime.send(pipeline, "threadExecutionWidth");
    }

    /** {@code -[MTLComputePipelineState maxTotalThreadsPerThreadgroup]}, the group-size ceiling. */
    public static long pipelineMaxTotalThreadsPerThreadgroup(long pipeline) {
        return ObjCRuntime.send(pipeline, "maxTotalThreadsPerThreadgroup");
    }

    /* ---- Reflection (MTLComputePipelineReflection / MTLArgument) ---- */

    /** {@code -[MTLComputePipelineReflection arguments]}: an {@code NSArray<MTLArgument *>}, not owned. */
    public static long reflectionArguments(long reflection) {
        return ObjCRuntime.send(reflection, "arguments");
    }

    /** {@code -[MTLArgument name]} as a Java string. */
    public static String argumentName(long argument) {
        return ObjCRuntime.toJavaString(ObjCRuntime.send(argument, "name"));
    }

    /** {@code -[MTLArgument index]}. */
    public static long argumentIndex(long argument) {
        return ObjCRuntime.send(argument, "index");
    }

    /** {@code -[MTLArgument type]}, an {@code MTLArgumentType} (buffer 0, threadgroupMemory 1, texture 2, sampler 3). */
    public static long argumentType(long argument) {
        return ObjCRuntime.send(argument, "type");
    }

    /** {@code -[MTLArgument access]}, an {@code MTLArgumentAccess} (readOnly 0, readWrite 1, writeOnly 2). */
    public static long argumentAccess(long argument) {
        return ObjCRuntime.send(argument, "access");
    }

    /** {@code -[MTLArgument arrayLength]}. */
    public static long argumentArrayLength(long argument) {
        return ObjCRuntime.send(argument, "arrayLength");
    }

    /** {@code -[NSError localizedDescription]} as a Java string, for the compile-failure path. */
    public static String errorDescription(long error) {
        return ObjCRuntime.toJavaString(ObjCRuntime.send(error, "localizedDescription"));
    }

    /** {@code -[MTLCommandQueue device]}, the queue's owning {@code MTLDevice}. */
    public static long queueDevice(long queue) {
        return ObjCRuntime.send(queue, "device");
    }

    /* ---- Encoding and dispatch ---- */

    /** {@code -[MTLCommandQueue commandBuffer]}: autoreleased, so retain it to hold it. */
    public static long commandBuffer(long queue) {
        return ObjCRuntime.send(queue, "commandBuffer");
    }

    /** {@code -[MTLCommandBuffer computeCommandEncoder]}: autoreleased. */
    public static long computeCommandEncoder(long commandBuffer) {
        return ObjCRuntime.send(commandBuffer, "computeCommandEncoder");
    }

    /** {@code -[MTLCommandBuffer blitCommandEncoder]}: autoreleased. */
    public static long blitCommandEncoder(long commandBuffer) {
        return ObjCRuntime.send(commandBuffer, "blitCommandEncoder");
    }

    /** {@code -[MTLComputeCommandEncoder setComputePipelineState:]}. */
    public static void setComputePipelineState(long encoder, long pipelineState) {
        ObjCRuntime.sendVoid(encoder, "setComputePipelineState:", pipelineState);
    }

    /** {@code -[MTLComputeCommandEncoder setBuffer:offset:atIndex:]}. */
    public static void setBuffer(long encoder, long buffer, long offset, long index) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG));
            handle.invokeExact(encoder, ObjCRuntime.sel("setBuffer:offset:atIndex:"), buffer, offset, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLComputeCommandEncoder setBytes:length:atIndex:]}, for small by-value arguments. */
    public static void setBytes(long encoder, MemorySegment bytes, long length, long index) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG));
            handle.invokeExact(encoder, ObjCRuntime.sel("setBytes:length:atIndex:"), bytes, length, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLComputeCommandEncoder setThreadgroupMemoryLength:atIndex:]}. */
    public static void setThreadgroupMemoryLength(long encoder, long length, long index) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, C_LONG, C_LONG));
            handle.invokeExact(encoder, ObjCRuntime.sel("setThreadgroupMemoryLength:atIndex:"), length, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code -[MTLComputeCommandEncoder dispatchThreadgroups:threadsPerThreadgroup:]}.
     *
     * <p>
     * Both arguments are {@code MTLSize} <em>by value</em>, three 64-bit fields each. Panama passes
     * them per the platform ABI when the descriptor names the struct layout, which is why this does
     * not take pointers. Allocate the two segments with {@link ObjCRuntime#MTL_SIZE} and fill
     * width/height/depth at offsets 0, 8, 16.
     */
    public static void dispatchThreadgroups(long encoder, MemorySegment threadgroupsPerGrid, MemorySegment threadsPerThreadgroup) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, ObjCRuntime.MTL_SIZE, ObjCRuntime.MTL_SIZE));
            handle.invokeExact(encoder, ObjCRuntime.sel("dispatchThreadgroups:threadsPerThreadgroup:"), threadgroupsPerGrid, threadsPerThreadgroup);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * {@code -[MTLComputeCommandEncoder dispatchThreads:threadsPerThreadgroup:]}, the non-uniform
     * dispatch the JNI shim used: Metal derives the threadgroup grid from the total thread count,
     * so a global size that is not a multiple of the threadgroup size is handled without a manual
     * ceiling division. Both arguments are {@code MTLSize} by value; see
     * {@link #dispatchThreadgroups} for the layout convention.
     */
    public static void dispatchThreads(long encoder, MemorySegment threadsPerGrid, MemorySegment threadsPerThreadgroup) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, ObjCRuntime.MTL_SIZE, ObjCRuntime.MTL_SIZE));
            handle.invokeExact(encoder, ObjCRuntime.sel("dispatchThreads:threadsPerThreadgroup:"), threadsPerGrid, threadsPerThreadgroup);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLBlitCommandEncoder copyFromBuffer:sourceOffset:toBuffer:destinationOffset:size:]}. */
    public static void blitCopy(long encoder, long source, long sourceOffset, long destination, long destinationOffset, long size) {
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG));
            handle.invokeExact(encoder, ObjCRuntime.sel("copyFromBuffer:sourceOffset:toBuffer:destinationOffset:size:"), source, sourceOffset, destination, destinationOffset, size);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -[MTLCommandEncoder endEncoding]}. */
    public static void endEncoding(long encoder) {
        ObjCRuntime.sendVoid(encoder, "endEncoding");
    }

    /** {@code -[MTLCommandBuffer commit]}. */
    public static void commit(long commandBuffer) {
        ObjCRuntime.sendVoid(commandBuffer, "commit");
    }

    /**
     * {@code -[MTLCommandBuffer waitUntilCompleted]}.
     *
     * <p>
     * Note for the device-side work: this is the blocking wait, and it is all that is needed to
     * reproduce the JNI shim's synchronous behaviour. Asynchronous completion --
     * {@code addCompletedHandler:} -- takes an Objective-C <em>block</em>, which is a struct with a
     * function pointer and an isa field rather than a plain callback, so it cannot be built from a
     * bare Panama upcall stub without laying out {@code _NSConcreteGlobalBlock} by hand. If the
     * backend needs it, the cheaper route is a Java thread parked on this call.
     */
    public static void waitUntilCompleted(long commandBuffer) {
        ObjCRuntime.sendVoid(commandBuffer, "waitUntilCompleted");
    }

    /** Alias documenting the wait used by the synchronous transfer path. */
    public static void commandBufferWaitUntilCompleted(long commandBuffer) {
        waitUntilCompleted(commandBuffer);
    }

    /** {@code -[MTLCommandBuffer GPUStartTime]} / {@code GPUEndTime}, in seconds, for the profiler. */
    public static double gpuStartTime(long commandBuffer) {
        return sendDouble(commandBuffer, "GPUStartTime");
    }

    public static double gpuEndTime(long commandBuffer) {
        return sendDouble(commandBuffer, "GPUEndTime");
    }

    /**
     * A selector returning a {@code double}.
     *
     * <p>
     * On x86_64 a floating-point return needs {@code objc_msgSend_fpret}; on Apple silicon the
     * ordinary send returns it in {@code d0} like any other function, so this is correct there and
     * is one of the things to re-check if an Intel Mac is ever a target.
     */
    private static double sendDouble(long receiver, String selector) {
        if (receiver == 0) {
            return 0.0d;
        }
        try {
            MethodHandle handle = ObjCRuntime.msgSend(FunctionDescriptor.of(FFMSupport.C_DOUBLE, C_LONG, C_LONG));
            return (double) handle.invokeExact(receiver, ObjCRuntime.sel(selector));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
