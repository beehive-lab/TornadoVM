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

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * The stateful half of the Metal FFM port.
 *
 * <p>
 * {@link MetalAPI} is one Objective-C message send per method and holds nothing. The former JNI
 * shim, by contrast, boxed a compiled library, a pipeline plus its pending arguments, and a
 * host-timing record in Objective-C wrapper objects and handed their addresses back as
 * {@code long}s. Those wrappers have no C ABI to call, so here they become plain Java records kept
 * in registries; the {@code long} handles the backend passes around are registry keys rather than
 * raw pointers, exactly as the CUDA port did with its driver handles. Real Metal objects -- devices,
 * queues, buffers, libraries, pipelines, command buffers -- stay raw pointers and flow through
 * unchanged.
 */
public final class MetalObjects {

    /* Info/param selectors, matching the values the Java layer passes (see the enums under
     * ...metal.enums). The old shim keyed off OpenCL-style constants that did not line up with those
     * enums for the program-info query, so that query returned zeroes; that behaviour is preserved. */
    private static final int METAL_PROGRAM_BUILD_STATUS = 0x1181;
    private static final int METAL_PROGRAM_BUILD_LOG = 0x1183;
    private static final int METAL_KERNEL_FUNCTION_NAME = 0x1190;
    private static final int METAL_QUEUE_CONTEXT = 0x1090;
    private static final int METAL_QUEUE_DEVICE = 0x1091;
    private static final long METAL_PROFILING_COMMAND_QUEUED = 0x1280;
    private static final long METAL_PROFILING_COMMAND_SUBMIT = 0x1281;
    private static final long METAL_PROFILING_COMMAND_START = 0x1282;
    private static final long METAL_PROFILING_COMMAND_END = 0x1283;
    private static final long METAL_PROFILING_COMMAND_COMPLETE = 0x1284;

    /** Bit 0 of a compile-flags mask enables fast/relaxed math; mirrors MetalContext. */
    private static final int METAL_COMPILE_FAST_MATH = 0x1;

    private static final MethodHandle DISPATCH_DATA_CREATE = FFMSupport.downcall(FFMSupport.loadLibrary("/usr/lib/libSystem.B.dylib", "libSystem.dylib"),
            FunctionDescriptor.of(C_LONG, C_POINTER, C_LONG, C_POINTER, C_POINTER), "dispatch_data_create");

    private MetalObjects() {
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

    // ------------------------------------------------------------------ programs

    private record ProgramState(long library, long device, int buildStatus, String buildLog) {
    }

    private static final Map<Long, ProgramState> PROGRAMS = new ConcurrentHashMap<>();
    private static final AtomicLong PROGRAM_IDS = new AtomicLong();

    // ------------------------------------------------------------------ kernels

    /** kind: 0 = device buffer (by pointer), 1 = inline bytes, 2 = threadgroup (local) memory. */
    private static final class Arg {
        int kind;
        long buffer;
        byte[] bytes;
        long size;
    }

    private static final class KernelState {
        final long pipeline;
        final long device;
        final String functionName;
        final String[] argInfo;
        final List<Arg> args = new ArrayList<>();

        KernelState(long pipeline, long device, String functionName, String[] argInfo) {
            this.pipeline = pipeline;
            this.device = device;
            this.functionName = functionName;
            this.argInfo = argInfo;
        }
    }

    private static final Map<Long, KernelState> KERNELS = new ConcurrentHashMap<>();
    private static final AtomicLong KERNEL_IDS = new AtomicLong();

    // ------------------------------------------------------------------ events

    /**
     * A host-side timing record. When {@link #commandBuffer} is non-zero this is a kernel event and
     * execution times are read back from the buffer's GPU timestamps; otherwise it is a CPU-side
     * transfer whose times were taken on the host clock. A batched dispatch has no command buffer of
     * its own but names its {@link #queue}: waiting on it drains that queue.
     */
    private static final class TimingEvent {
        final long queuedNs;
        final long startNs;
        final long endNs;
        final long commandBuffer;
        final long queue;

        TimingEvent(long queuedNs, long startNs, long endNs, long commandBuffer) {
            this(queuedNs, startNs, endNs, commandBuffer, 0);
        }

        TimingEvent(long queuedNs, long startNs, long endNs, long commandBuffer, long queue) {
            this.queuedNs = queuedNs;
            this.startNs = startNs;
            this.endNs = endNs;
            this.commandBuffer = commandBuffer;
            this.queue = queue;
        }
    }

    private static final Map<Long, TimingEvent> EVENTS = new ConcurrentHashMap<>();
    private static final AtomicLong EVENT_IDS = new AtomicLong();

    /** Event keys are odd, so they can never collide with 0 (the empty event) or a wait-list count. */
    private static long registerEvent(TimingEvent event) {
        long key = (EVENT_IDS.incrementAndGet() << 1) | 1L;
        EVENTS.put(key, event);
        return key;
    }

    // ------------------------------------------------------------------ platform / device

    public static int platformCount() {
        return 1;
    }

    /** Fills {@code out} with the discovered device pointers, used as opaque platform handles too. */
    public static int platformIDs(long[] out) {
        return deviceIDs(MetalDeviceType.ALL, out);
    }

    /** The platform-info strings the OpenCL-shaped Java layer asks for. */
    public static String platformInfo(int info) {
        return switch (info) {
            case 0x0900 -> "FULL_PROFILE";
            case 0x0901 -> "Metal 3.0";
            case 0x0902 -> "Apple Metal";
            case 0x0903 -> "Apple Inc.";
            case 0x0904 -> "";
            default -> "unsupported_info";
        };
    }

    /** CL_DEVICE_TYPE_* bit values the Java layer still speaks; only GPU/default/all select a GPU. */
    private static final class MetalDeviceType {
        static final long CPU = 1L << 1;
        static final long GPU = 1L << 2;
        static final long ACCELERATOR = 1L << 3;
        static final long DEFAULT = 1L << 0;
        static final long ALL = 0xFFFFFFFFL;
    }

    private static boolean wantsGpu(long type) {
        if (type == MetalDeviceType.CPU || type == MetalDeviceType.ACCELERATOR) {
            return false;
        }
        return (type & (MetalDeviceType.GPU | MetalDeviceType.DEFAULT | MetalDeviceType.ALL)) != 0;
    }

    public static int deviceCount(long type) {
        if (!wantsGpu(type)) {
            return 0;
        }
        long array = MetalAPI.copyAllDevices();
        if (array == 0) {
            return 0;
        }
        int count = (int) MetalAPI.arrayCount(array);
        ObjCRuntime.release(array);
        return count;
    }

    /**
     * Fills {@code out} with the available device pointers and returns the total device count. Each
     * device handed out is retained so it survives the release of the enclosing {@code NSArray} and
     * lives for the process, matching the old shim's {@code CFRetain} per device.
     */
    public static int deviceIDs(long type, long[] out) {
        if (!wantsGpu(type)) {
            return 0;
        }
        long array = MetalAPI.copyAllDevices();
        if (array == 0) {
            return 0;
        }
        int total = (int) MetalAPI.arrayCount(array);
        int fill = Math.min(total, out.length);
        for (int i = 0; i < fill; i++) {
            long device = MetalAPI.arrayObjectAtIndex(array, i);
            ObjCRuntime.retain(device);
            out[i] = device;
        }
        ObjCRuntime.release(array);
        return total;
    }

    public static String deviceName(long device) {
        String name = MetalAPI.deviceName(device);
        return name == null ? "unknown" : name;
    }

    public static long deviceGlobalMemorySize(long device) {
        return MetalAPI.deviceRecommendedMaxWorkingSetSize(device);
    }

    public static long deviceLocalMemorySize(long device) {
        return MetalAPI.deviceMaxThreadgroupMemoryLength(device);
    }

    public static int hasUnifiedMemory(long device) {
        return MetalAPI.deviceHasUnifiedMemory(device) ? 1 : 0;
    }

    // ------------------------------------------------------------------ context / queue

    /** A context, in the shim's model, is a command queue on the first device. */
    public static long createContext(long[] devices) {
        if (devices.length == 0) {
            return 0;
        }
        long device = devices[0];
        return device == 0 ? 0 : MetalAPI.newCommandQueue(device);
    }

    public static void releaseContext(long context) {
        ObjCRuntime.release(context);
    }

    /** Writes the device pointer (or the queue itself) into {@code buffer} for the debug queries. */
    public static void contextInfo(long context, int info, byte[] buffer) {
        writeLong(buffer, MetalAPI.queueDevice(context));
    }

    public static long createCommandQueue(long device, int maxInFlight) {
        return maxInFlight > 0 ? MetalAPI.newCommandQueueWithMaxCommandBufferCount(device, maxInFlight) : MetalAPI.newCommandQueue(device);
    }

    public static void releaseCommandQueue(long queue) {
        drain(queue);
        BATCHES.remove(queue);
        QueueEvent queueEvent = QUEUE_EVENTS.remove(queue);
        if (queueEvent != null && queueEvent.event != 0) {
            ObjCRuntime.release(queueEvent.event);
        }
        ObjCRuntime.release(queue);
    }

    public static void queueInfo(long queue, int info, byte[] buffer) {
        long value = switch (info) {
            case METAL_QUEUE_DEVICE -> MetalAPI.queueDevice(queue);
            case METAL_QUEUE_CONTEXT -> queue;
            default -> 0L;
        };
        writeLong(buffer, value);
    }

    // ------------------------------------------------------------------ buffers

    /** Returns {@code [bufferPtr, cpuAddress, status]} for a fresh shared-storage buffer. */
    public static long[] createBuffer(long context, long size) {
        long device = MetalAPI.queueDevice(context);
        if (device == 0) {
            return new long[] { 0, 0, -1 };
        }
        long buffer = MetalAPI.newBufferWithLength(device, size, MetalAPI.MTL_RESOURCE_STORAGE_MODE_SHARED);
        if (buffer == 0) {
            return new long[] { 0, 0, -1 };
        }
        return new long[] { buffer, MetalAPI.bufferContents(buffer), 0 };
    }

    public static void releaseMemObject(long buffer) {
        drainFor(buffer);
        ObjCRuntime.release(buffer);
    }

    // ------------------------------------------------------------------ programs

    public static long createProgramWithSource(long context, byte[] source, int compileFlags) {
        long device = MetalAPI.queueDevice(context);
        if (device == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            long sourceString = ObjCRuntime.newNSString(new String(source, StandardCharsets.UTF_8));
            long options = 0;
            if ((compileFlags & METAL_COMPILE_FAST_MATH) != 0) {
                long optionsClass = ObjCRuntime.objc_getClass("MTLCompileOptions");
                options = ObjCRuntime.send(ObjCRuntime.send(optionsClass, "alloc"), "init");
                ObjCRuntime.sendVoid(options, "setFastMathEnabled:", 1);
            }
            MemorySegment errorSlot = FFMSupport.allocatePointer(arena);
            long library = MetalAPI.newLibraryWithSource(device, sourceString, options, errorSlot);
            long program = registerProgram(library, device, errorSlot);
            ObjCRuntime.release(sourceString);
            if (options != 0) {
                ObjCRuntime.release(options);
            }
            return program;
        }
    }

    public static long createProgramWithBinary(long context, byte[] binary) {
        long device = MetalAPI.queueDevice(context);
        if (device == 0 || binary.length == 0 || DISPATCH_DATA_CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            MemorySegment bytes = arena.allocate(binary.length);
            MemorySegment.copy(binary, 0, bytes, FFMSupport.C_CHAR, 0, binary.length);
            // A NULL destructor is DISPATCH_DATA_DESTRUCTOR_DEFAULT: dispatch copies the bytes, so the
            // arena-owned source may go away when this returns.
            long data = (long) DISPATCH_DATA_CREATE.invokeExact(bytes, (long) binary.length, MemorySegment.NULL, MemorySegment.NULL);
            MemorySegment errorSlot = FFMSupport.allocatePointer(arena);
            long library = MetalAPI.newLibraryWithData(device, data, errorSlot);
            long program = registerProgram(library, device, errorSlot);
            ObjCRuntime.release(data);
            return program;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long registerProgram(long library, long device, MemorySegment errorSlot) {
        long key = PROGRAM_IDS.incrementAndGet();
        if (library == 0) {
            long error = errorSlot.get(C_POINTER, 0).address();
            String log = error == 0 ? "compile error" : MetalAPI.errorDescription(error);
            PROGRAMS.put(key, new ProgramState(0, device, -1, log));
        } else {
            PROGRAMS.put(key, new ProgramState(library, device, 0, ""));
        }
        return key;
    }

    public static void releaseProgram(long program) {
        ProgramState state = PROGRAMS.remove(program);
        if (state != null && state.library() != 0) {
            ObjCRuntime.release(state.library());
        }
    }

    /** Program-info queries: preserved as all-zero, which is what the old shim effectively returned. */
    public static void programInfo(long program, int param, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
    }

    public static void programBuildInfo(long program, int param, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        ProgramState state = PROGRAMS.get(program);
        if (param == METAL_PROGRAM_BUILD_STATUS) {
            writeInt(buffer, state == null ? 0 : state.buildStatus());
        } else if (param == METAL_PROGRAM_BUILD_LOG && state != null && state.buildLog() != null) {
            writeCString(buffer, state.buildLog());
        }
    }

    // ------------------------------------------------------------------ kernels

    /** Creates a compute pipeline for {@code name} and captures its argument reflection. */
    public static long createKernel(long program, String name) {
        ProgramState state = PROGRAMS.get(program);
        if (state == null || state.library() == 0 || name == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            long nameString = ObjCRuntime.newNSString(name);
            long function = MetalAPI.newFunctionWithName(state.library(), nameString);
            ObjCRuntime.release(nameString);
            if (function == 0) {
                return 0;
            }
            long device = state.device() != 0 ? state.device() : MetalAPI.createSystemDefaultDevice();
            MemorySegment reflectionSlot = FFMSupport.allocatePointer(arena);
            MemorySegment errorSlot = FFMSupport.allocatePointer(arena);
            long pipeline = MetalAPI.newComputePipelineStateWithFunctionReflection(device, function, MetalAPI.MTL_PIPELINE_OPTION_ARGUMENT_INFO, reflectionSlot, errorSlot);
            ObjCRuntime.release(function);
            if (pipeline == 0) {
                return 0;
            }
            String[] argInfo = readArgumentInfo(reflectionSlot.get(C_POINTER, 0).address());
            long key = KERNEL_IDS.incrementAndGet();
            KERNELS.put(key, new KernelState(pipeline, device, name, argInfo));
            return key;
        }
    }

    private static String[] readArgumentInfo(long reflection) {
        if (reflection == 0) {
            return new String[0];
        }
        long arguments = MetalAPI.reflectionArguments(reflection);
        if (arguments == 0) {
            return new String[0];
        }
        int count = (int) MetalAPI.arrayCount(arguments);
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            long argument = MetalAPI.arrayObjectAtIndex(arguments, i);
            String argName = MetalAPI.argumentName(argument);
            long index = MetalAPI.argumentIndex(argument);
            String type = switch ((int) MetalAPI.argumentType(argument)) {
                case 0 -> "buffer";
                case 1 -> "threadgroup";
                case 2 -> "texture";
                case 3 -> "sampler";
                default -> "unknown";
            };
            String access = switch ((int) MetalAPI.argumentAccess(argument)) {
                case 0 -> "read";
                case 1 -> "readwrite";
                case 2 -> "write";
                default -> "unknown";
            };
            long arrayLength = MetalAPI.argumentArrayLength(argument);
            out[i] = (argName == null ? "" : argName) + ":" + index + ":" + type + ":" + access + ":" + arrayLength;
        }
        return out;
    }

    public static void releaseKernel(long kernel) {
        drainAll();
        KernelState state = KERNELS.remove(kernel);
        if (state != null && state.pipeline != 0) {
            ObjCRuntime.release(state.pipeline);
        }
    }

    public static void kernelInfo(long kernel, int info, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        if (info == METAL_KERNEL_FUNCTION_NAME) {
            KernelState state = KERNELS.get(kernel);
            if (state != null && state.functionName != null) {
                writeCString(buffer, state.functionName);
            }
        }
    }

    public static int kernelArgCount(long kernel) {
        KernelState state = KERNELS.get(kernel);
        return state == null ? 0 : state.argInfo.length;
    }

    public static void kernelArgInfo(long kernel, int index, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        KernelState state = KERNELS.get(kernel);
        if (state != null && index >= 0 && index < state.argInfo.length) {
            writeCString(buffer, state.argInfo[index]);
        }
    }

    /** Records an argument at {@code index}: inline bytes, or (when {@code data} is null) a local slot. */
    public static void setKernelArg(long kernel, int index, long size, byte[] data) {
        KernelState state = KERNELS.get(kernel);
        if (state == null) {
            return;
        }
        Arg arg = new Arg();
        if (data != null) {
            int length = (int) Math.min(size, data.length);
            arg.kind = 1;
            arg.bytes = java.util.Arrays.copyOf(data, length);
            arg.size = size;
        } else if (size > 0) {
            arg.kind = 2;
            arg.size = size;
        } else {
            arg.kind = 1;
            arg.bytes = new byte[0];
            arg.size = 0;
        }
        placeArg(state, index, arg);
    }

    public static void setKernelArgRef(long kernel, int index, long buffer) {
        KernelState state = KERNELS.get(kernel);
        if (state == null) {
            return;
        }
        Arg arg = new Arg();
        arg.kind = 0;
        arg.buffer = buffer;
        placeArg(state, index, arg);
    }

    private static void placeArg(KernelState state, int index, Arg arg) {
        while (state.args.size() <= index) {
            state.args.add(null);
        }
        state.args.set(index, arg);
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * Encodes one compute dispatch. With batching on (the default, see {@link #BATCH}) it is appended
     * to the queue's open command buffer and the returned event names the queue; otherwise it runs
     * synchronously and the event holds the retained command buffer for its GPU timestamps.
     */
    public static long enqueueNDRangeKernel(long queue, long kernel, long[] globalWorkSize, long[] localWorkSize) {
        KernelState state = KERNELS.get(kernel);
        if (queue == 0 || state == null || state.pipeline == 0) {
            return -1;
        }
        long queuedNs = System.nanoTime();
        long gx = globalWorkSize != null && globalWorkSize.length > 0 ? globalWorkSize[0] : 1;
        long gy = globalWorkSize != null && globalWorkSize.length > 1 ? globalWorkSize[1] : 1;
        long gz = globalWorkSize != null && globalWorkSize.length > 2 ? globalWorkSize[2] : 1;

        long lx;
        long ly = 1;
        long lz = 1;
        if (localWorkSize != null && localWorkSize.length > 0) {
            lx = localWorkSize[0];
            ly = localWorkSize.length > 1 ? localWorkSize[1] : 1;
            lz = localWorkSize.length > 2 ? localWorkSize[2] : 1;
        } else {
            lx = MetalAPI.pipelineThreadExecutionWidth(state.pipeline);
        }
        long maxPer = MetalAPI.pipelineMaxTotalThreadsPerThreadgroup(state.pipeline);
        if (lx * ly * lz > maxPer) {
            lx = Math.min(lx, maxPer);
            ly = 1;
            lz = 1;
        }

        if (batching()) {
            if (LAZY_SYNC) {
                drainOtherQueuesUsing(queue, state);
            }
            QueueBatch batch = BATCHES.computeIfAbsent(queue, q -> new QueueBatch());
            synchronized (batch) {
                try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
                    if (batch.commandBuffer == 0) {
                        batch.commandBuffer = ObjCRuntime.retain(MetalAPI.commandBuffer(queue));
                        batch.encoder = ObjCRuntime.retain(MetalAPI.computeCommandEncoder(batch.commandBuffer));
                    }
                    encodeDispatch(batch.encoder, state, arena, gx, gy, gz, lx, ly, lz);
                    for (Arg arg : state.args) {
                        if (arg != null && arg.kind == 0 && arg.buffer != 0) {
                            batch.buffers.add(arg.buffer);
                        }
                    }
                    if (++batch.dispatches >= BATCH_COMMIT_INTERVAL) {
                        commitOpen(queue, batch);
                    }
                }
            }
            return registerEvent(new TimingEvent(queuedNs, queuedNs, System.nanoTime(), 0, queue));
        }

        // Synchronous: anything batched before (e.g. before the profiler was switched on) runs first.
        drain(queue);
        long retainedCommandBuffer;
        try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            long commandBuffer = MetalAPI.commandBuffer(queue);
            long encoder = MetalAPI.computeCommandEncoder(commandBuffer);
            encodeDispatch(encoder, state, arena, gx, gy, gz, lx, ly, lz);
            MetalAPI.endEncoding(encoder);
            commitAndWait(queue, commandBuffer);
            // Retain across the pool so GPUStartTime/GPUEndTime can be read when the profiler asks.
            retainedCommandBuffer = ObjCRuntime.retain(commandBuffer);
        }
        return registerEvent(new TimingEvent(queuedNs, 0, 0, retainedCommandBuffer));
    }

    /** Binds the kernel's recorded arguments and the global sizes, then encodes the dispatch. */
    private static void encodeDispatch(long encoder, KernelState state, Arena arena, long gx, long gy, long gz, long lx, long ly, long lz) {
        MetalAPI.setComputePipelineState(encoder, state.pipeline);

        for (int i = 0; i < state.args.size(); i++) {
            Arg arg = state.args.get(i);
            if (arg == null) {
                continue;
            }
            switch (arg.kind) {
                case 0 -> {
                    if (arg.buffer != 0) {
                        MetalAPI.setBuffer(encoder, arg.buffer, 0, i);
                    }
                }
                case 1 -> {
                    if (arg.bytes != null && arg.size > 0) {
                        MemorySegment segment = arena.allocate(arg.bytes.length);
                        MemorySegment.copy(arg.bytes, 0, segment, FFMSupport.C_CHAR, 0, arg.bytes.length);
                        MetalAPI.setBytes(encoder, segment, arg.size, i);
                    }
                }
                case 2 -> MetalAPI.setThreadgroupMemoryLength(encoder, arg.size, i);
                default -> {
                }
            }
        }

        // The three global sizes, bound past the user arguments, fill the _global_sizes parameter
        // the generated MSL reads. setBytes copies them into the command buffer, which is cheaper
        // than creating (and mapping for the GPU) a new buffer on every launch.
        int sizesIndex = state.args.size();
        MemorySegment sizes = arena.allocate(3 * Integer.BYTES);
        sizes.set(C_INT, 0, (int) gx);
        sizes.set(C_INT, 4, (int) gy);
        sizes.set(C_INT, 8, (int) gz);
        MetalAPI.setBytes(encoder, sizes, 3L * Integer.BYTES, sizesIndex);

        MemorySegment grid = mtlSize(arena, gx, gy, gz);
        MemorySegment group = mtlSize(arena, lx, ly, lz);
        MetalAPI.dispatchThreads(encoder, grid, group);
    }

    // ------------------------------------------------------------------ batched dispatch

    /**
     * Whether kernel launches are batched: appended to one open command buffer per queue instead of
     * each being committed and waited for on its own. A synchronous launch leaves the GPU idle while
     * the CPU waits and encodes the next one, which costs the round trip on every kernel and keeps
     * the GPU clock low. Dispatches in a batch share one serial compute encoder, so each runs after
     * the previous one finished and sees its writes; command buffers on a queue run in commit order.
     * The batch is drained (committed and waited for) before anything that touches device memory
     * from outside it: host reads and writes, blits, event waits, {@link #finish}, a library task
     * taking the queue or a buffer address, and releases. The profiler needs per-kernel GPU
     * timestamps, so it keeps synchronous launches. {@code -Dtornado.metal.dispatch.batch=False}
     * switches batching off.
     */
    private static final boolean BATCH = Boolean.parseBoolean(System.getProperty("tornado.metal.dispatch.batch", "True"));

    /**
     * Dispatches per command buffer before it is committed (without waiting), so the GPU starts on a
     * long batch while the CPU encodes the rest of it. {@code -Dtornado.metal.dispatch.batchSize}.
     */
    private static final int BATCH_COMMIT_INTERVAL = Math.max(1, Integer.getInteger("tornado.metal.dispatch.batchSize", 16));

    /**
     * Whether {@link #finish} commits the batch without waiting for it, so that {@code execute()} of
     * a task graph returns once its work is queued rather than done. Nothing on the host can observe
     * the difference: every host read or write of a buffer a pending dispatch uses, every event wait,
     * library call and release drains first, and a dispatch on another queue that binds such a buffer
     * drains that queue first. What moves is the wall-clock time of the wait, to the next of those.
     * Off by default; {@code -Dtornado.metal.dispatch.lazySync=True}.
     */
    private static final boolean LAZY_SYNC = Boolean.parseBoolean(System.getProperty("tornado.metal.dispatch.lazySync", "False"));

    /** Drains every other queue with a pending dispatch bound to one of {@code state}'s buffers. */
    private static void drainOtherQueuesUsing(long queue, KernelState state) {
        if (BATCHES.size() < 2) {
            return;
        }
        for (Map.Entry<Long, QueueBatch> entry : BATCHES.entrySet()) {
            if (entry.getKey() == queue) {
                continue;
            }
            QueueBatch other = entry.getValue();
            boolean pending = false;
            synchronized (other) {
                for (Arg arg : state.args) {
                    if (arg != null && arg.kind == 0 && other.buffers.contains(arg.buffer)) {
                        pending = true;
                        break;
                    }
                }
            }
            if (pending) {
                drain(entry.getKey());
            }
        }
    }

    /** The open command buffer of a queue and the last one committed from it. */
    private static final class QueueBatch {
        /** Retained, open; 0 when nothing is being encoded. */
        long commandBuffer;
        /** Retained compute encoder of {@link #commandBuffer}. */
        long encoder;
        int dispatches;
        /** Retained, committed and not yet waited for; 0 when none. */
        long lastCommitted;
        /** The shared-event value {@link #lastCommitted} signals, or 0 when it signals none. */
        long lastValue;
        /** Buffers bound by the dispatches not yet waited for: a host access to one has to drain. */
        final Set<Long> buffers = new HashSet<>();
    }

    private static final Map<Long, QueueBatch> BATCHES = new ConcurrentHashMap<>();


    private static boolean batching() {
        return BATCH && !TornadoOptions.isProfilerEnabled();
    }

    /** Whether launches are batched now; the launch path then binds the kernel frame by value. */
    public static boolean isBatching() {
        return batching();
    }

    /** Ends and commits the open command buffer of {@code batch} without waiting for it. Holds the batch lock. */
    private static void commitOpen(long queue, QueueBatch batch) {
        if (batch.commandBuffer == 0) {
            return;
        }
        MetalAPI.endEncoding(batch.encoder);
        ObjCRuntime.release(batch.encoder);
        long value = signalAndCommit(queue, batch.commandBuffer);
        if (batch.lastCommitted != 0) {
            ObjCRuntime.release(batch.lastCommitted);
        }
        batch.lastCommitted = batch.commandBuffer;
        batch.lastValue = value;
        batch.commandBuffer = 0;
        batch.encoder = 0;
        batch.dispatches = 0;
    }

    /** Commits whatever is batched on {@code queue} and returns once the GPU has finished all of it. */
    public static void drain(long queue) {
        QueueBatch batch = BATCHES.get(queue);
        if (batch == null) {
            return;
        }
        synchronized (batch) {
            commitOpen(queue, batch);
            if (batch.lastCommitted != 0) {
                awaitCommitted(queue, batch.lastCommitted, batch.lastValue);
                ObjCRuntime.release(batch.lastCommitted);
                batch.lastCommitted = 0;
                batch.lastValue = 0;
            }
            batch.buffers.clear();
        }
    }

    /** {@link #drain} for every queue: for an access that may touch any buffer. */
    public static void drainAll() {
        for (Long queue : BATCHES.keySet()) {
            drain(queue);
        }
    }

    /**
     * {@link #drain} for every queue with a pending dispatch bound to {@code buffer}: for a host
     * read or write of it. A buffer no pending dispatch uses -- the next kernel's stack frame, an
     * input of the next graph -- is accessed without waiting.
     */
    private static void drainFor(long buffer) {
        for (Map.Entry<Long, QueueBatch> entry : BATCHES.entrySet()) {
            QueueBatch batch = entry.getValue();
            boolean pending;
            synchronized (batch) {
                pending = batch.buffers.contains(buffer);
            }
            if (pending) {
                drain(entry.getKey());
            }
        }
    }

    /**
     * Wait for a committed command buffer by polling an {@code MTLSharedEvent} it signals, rather
     * than with {@code waitUntilCompleted}. The event is set as soon as the GPU finishes the encoded
     * work, while completion of the command buffer reaches the CPU some 60-90 us later on Apple
     * silicon, so this takes that delay off every synchronous launch. A queue whose last wait took
     * longer than {@link #SPIN_BUDGET_NS} blocks right away on the next one (see {@link QueueEvent#block}). Set
     * {@code -Dtornado.metal.dispatch.spinWait=False} to wait with {@code waitUntilCompleted}.
     */
    private static final boolean SPIN_WAIT = Boolean.parseBoolean(System.getProperty("tornado.metal.dispatch.spinWait", "True"));

    /** Command buffer status and the spin budget are checked every this many polls. */
    private static final int STATUS_POLL_INTERVAL = 64;

    /**
     * How long to poll before blocking in {@code waitUntilCompleted}. Polling keeps a CPU core busy,
     * which is worth it for the short kernels whose latency it cuts; past this, the completion delay
     * it saves is a few percent of the kernel time at most.
     */
    private static final long SPIN_BUDGET_NS = 1_000_000;

    /** One shared event per command queue, with the last value it was asked to signal. */
    private static final class QueueEvent {
        final long event;
        long value;
        /**
         * Whether the last wait on this queue took longer than {@link #SPIN_BUDGET_NS}. The next wait
         * then blocks right away: for long kernels, polling cannot save a meaningful share of the time,
         * and a busy CPU core can slow the GPU on a chip where they share power.
         */
        volatile boolean block;

        QueueEvent(long event) {
            this.event = event;
        }
    }

    private static final Map<Long, QueueEvent> QUEUE_EVENTS = new ConcurrentHashMap<>();

    /** Commits {@code commandBuffer} on {@code queue} and returns once the GPU has finished its work. */
    private static void commitAndWait(long queue, long commandBuffer) {
        awaitCommitted(queue, commandBuffer, signalAndCommit(queue, commandBuffer));
    }

    /**
     * Commits {@code commandBuffer}, first encoding a signal of the queue's shared event when spin
     * waiting is on. Returns the value it will signal, or 0 when it signals none.
     */
    private static long signalAndCommit(long queue, long commandBuffer) {
        QueueEvent queueEvent = SPIN_WAIT ? QUEUE_EVENTS.computeIfAbsent(queue, q -> new QueueEvent(MetalAPI.newSharedEvent(MetalAPI.queueDevice(q)))) : null;
        if (queueEvent == null || queueEvent.event == 0) {
            MetalAPI.commit(commandBuffer);
            return 0;
        }
        // Take the value and commit under one lock, so that command buffers are committed in the order
        // of their values. Otherwise a thread sharing the queue could commit a higher value first, and a
        // waiter for a lower value would return before its own buffer had finished.
        synchronized (queueEvent) {
            long value = ++queueEvent.value;
            MetalAPI.encodeSignalEvent(commandBuffer, queueEvent.event, value);
            MetalAPI.commit(commandBuffer);
            return value;
        }
    }

    /** Returns once committed {@code commandBuffer}, which signals {@code value} (0: none), has finished. */
    private static void awaitCommitted(long queue, long commandBuffer, long value) {
        QueueEvent queueEvent = value == 0 ? null : QUEUE_EVENTS.get(queue);
        if (queueEvent == null) {
            MetalAPI.waitUntilCompleted(commandBuffer);
            return;
        }
        long start = System.nanoTime();
        if (queueEvent.block) {
            MetalAPI.waitUntilCompleted(commandBuffer);
            queueEvent.block = System.nanoTime() - start > SPIN_BUDGET_NS;
            return;
        }
        int polls = 0;
        while (MetalAPI.sharedEventSignaledValue(queueEvent.event) < value) {
            if (++polls % STATUS_POLL_INTERVAL == 0) {
                if (System.nanoTime() - start > SPIN_BUDGET_NS) {
                    MetalAPI.waitUntilCompleted(commandBuffer);
                    queueEvent.block = true;
                    return;
                }
                if (MetalAPI.commandBufferStatus(commandBuffer) >= MetalAPI.MTL_COMMAND_BUFFER_STATUS_COMPLETED) {
                    // Finished without signalling: the buffer failed, and its status says so.
                    return;
                }
            }
            Thread.yield();
        }
    }

    private static MemorySegment mtlSize(Arena arena, long width, long height, long depth) {
        MemorySegment segment = arena.allocate(ObjCRuntime.MTL_SIZE);
        segment.set(C_LONG, 0, width);
        segment.set(C_LONG, 8, height);
        segment.set(C_LONG, 16, depth);
        return segment;
    }

    // ------------------------------------------------------------------ transfers

    /** Host-to-device copy from a Java array into the shared buffer's CPU-visible memory. */
    public static long writeArray(long buffer, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes) {
        drainFor(buffer);
        long queuedNs = System.nanoTime();
        long contents = MetalAPI.bufferContents(buffer);
        if (contents == 0) {
            return -1;
        }
        long startNs = System.nanoTime();
        MemorySegment destination = FFMSupport.asSegment(contents + offset, bytes);
        MemorySegment.copy(array, elementOffset, destination, layout, 0, elementCount);
        long endNs = System.nanoTime();
        return registerEvent(new TimingEvent(queuedNs, startNs, endNs, 0));
    }

    /** Device-to-host copy out of the shared buffer's CPU-visible memory into a Java array. */
    public static long readArray(long buffer, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes) {
        drainFor(buffer);
        long queuedNs = System.nanoTime();
        long contents = MetalAPI.bufferContents(buffer);
        if (contents == 0) {
            return -1;
        }
        long startNs = System.nanoTime();
        MemorySegment source = FFMSupport.asSegment(contents + offset, bytes);
        MemorySegment.copy(source, layout, 0, array, elementOffset, elementCount);
        long endNs = System.nanoTime();
        return registerEvent(new TimingEvent(queuedNs, startNs, endNs, 0));
    }

    public static long writeSegment(long buffer, long hostPointer, long hostOffset, long offset, long bytes) {
        drainFor(buffer);
        long queuedNs = System.nanoTime();
        long contents = MetalAPI.bufferContents(buffer);
        if (contents == 0 || hostPointer == 0) {
            return -1;
        }
        long startNs = System.nanoTime();
        MemorySegment source = FFMSupport.asSegment(hostPointer + hostOffset, bytes);
        MemorySegment destination = FFMSupport.asSegment(contents + offset, bytes);
        MemorySegment.copy(source, 0, destination, 0, bytes);
        long endNs = System.nanoTime();
        return registerEvent(new TimingEvent(queuedNs, startNs, endNs, 0));
    }

    public static long readSegment(long buffer, long hostPointer, long hostOffset, long offset, long bytes) {
        drainFor(buffer);
        long queuedNs = System.nanoTime();
        long contents = MetalAPI.bufferContents(buffer);
        if (contents == 0 || hostPointer == 0) {
            return -1;
        }
        long startNs = System.nanoTime();
        MemorySegment source = FFMSupport.asSegment(contents + offset, bytes);
        MemorySegment destination = FFMSupport.asSegment(hostPointer + hostOffset, bytes);
        MemorySegment.copy(source, 0, destination, 0, bytes);
        long endNs = System.nanoTime();
        return registerEvent(new TimingEvent(queuedNs, startNs, endNs, 0));
    }

    // ------------------------------------------------------------------ queue lifecycle

    public static void flush(long queue) {
        // Metal has no flush distinct from committing a command buffer; nothing to do.
    }

    public static void finish(long queue) {
        if (queue == 0) {
            return;
        }
        if (LAZY_SYNC && batching()) {
            QueueBatch batch = BATCHES.get(queue);
            if (batch != null) {
                synchronized (batch) {
                    commitOpen(queue, batch);
                }
            }
            return;
        }
        drain(queue);
        QueueEvent queueEvent = QUEUE_EVENTS.get(queue);
        if (queueEvent != null && workFinished(queueEvent)) {
            // Every command buffer on this queue signals the event when its work is done, and the
            // event has reached the last value asked for, so there is nothing left to wait for. A
            // committed empty buffer would instead wait for the completion notices of the earlier
            // buffers, the delay that polling the event avoided.
            return;
        }
        try (ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            long commandBuffer = MetalAPI.commandBuffer(queue);
            if (commandBuffer != 0) {
                MetalAPI.commit(commandBuffer);
                MetalAPI.waitUntilCompleted(commandBuffer);
            }
        }
    }

    private static boolean workFinished(QueueEvent queueEvent) {
        long value;
        synchronized (queueEvent) {
            value = queueEvent.value;
        }
        return MetalAPI.sharedEventSignaledValue(queueEvent.event) >= value;
    }

    /** Barriers and markers reduce to waiting on the listed events, all of which already completed. */
    public static void waitForEventList(long[] events) {
        if (events == null) {
            return;
        }
        for (long event : events) {
            waitOne(event);
        }
    }

    private static void waitOne(long event) {
        if (event == 0) {
            return;
        }
        TimingEvent state = EVENTS.get(event);
        if (state != null && state.queue != 0) {
            drain(state.queue);
        } else if (state != null && state.commandBuffer != 0) {
            MetalAPI.waitUntilCompleted(state.commandBuffer);
        }
    }

    // ------------------------------------------------------------------ events

    public static void waitForEvents(long[] events) {
        if (events == null) {
            return;
        }
        for (long event : events) {
            waitOne(event);
        }
    }

    public static void eventInfo(long event, int param, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        // CL_COMPLETE == 0 as a little-endian int; every event here has already run to completion.
        writeInt(buffer, 0);
    }

    public static void eventProfilingInfo(long event, long param, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        TimingEvent state = EVENTS.get(event);
        if (state == null || buffer.length < 8) {
            return;
        }
        if (state.queue != 0) {
            drain(state.queue);
        }
        long time;
        if (state.commandBuffer != 0) {
            // The GPU timestamps are set when the buffer completes, which can trail the wait by tens of us.
            MetalAPI.waitUntilCompleted(state.commandBuffer);
            if (param == METAL_PROFILING_COMMAND_QUEUED || param == METAL_PROFILING_COMMAND_SUBMIT) {
                time = state.queuedNs;
            } else if (param == METAL_PROFILING_COMMAND_START) {
                time = (long) (MetalAPI.gpuStartTime(state.commandBuffer) * 1.0e9);
            } else if (param == METAL_PROFILING_COMMAND_END || param == METAL_PROFILING_COMMAND_COMPLETE) {
                time = (long) (MetalAPI.gpuEndTime(state.commandBuffer) * 1.0e9);
            } else {
                time = 0;
            }
        } else {
            if (param == METAL_PROFILING_COMMAND_QUEUED) {
                time = state.queuedNs;
            } else if (param == METAL_PROFILING_COMMAND_SUBMIT || param == METAL_PROFILING_COMMAND_START) {
                time = state.startNs;
            } else if (param == METAL_PROFILING_COMMAND_END || param == METAL_PROFILING_COMMAND_COMPLETE) {
                time = state.endNs;
            } else {
                time = 0;
            }
        }
        writeLong(buffer, time);
    }

    public static void releaseEvent(long event) {
        TimingEvent state = EVENTS.remove(event);
        if (state != null && state.commandBuffer != 0) {
            ObjCRuntime.release(state.commandBuffer);
        }
    }

    // ------------------------------------------------------------------ device-to-device map

    public static long mapOnDeviceMemoryRegion(long destination, long source) {
        return source;
    }

    /** GPU-to-GPU blit of a sub-range from {@code source} into {@code destination}. */
    public static long mapOnDeviceMemoryNDRegion(long queue, long destination, long source, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        if (queue == 0 || source == 0 || destination == 0) {
            return destination;
        }
        long headerBytes = headerSize * 4;
        long sourceOffset = headerBytes + offset * sizeDataType;
        long copySize = sizeDest > headerBytes ? sizeDest - headerBytes : 0;
        if (copySize == 0) {
            return destination;
        }
        drain(queue);
        try (ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
            long commandBuffer = MetalAPI.commandBuffer(queue);
            long blit = MetalAPI.blitCommandEncoder(commandBuffer);
            MetalAPI.blitCopy(blit, source, sourceOffset, destination, headerBytes, copySize);
            MetalAPI.endEncoding(blit);
            commitAndWait(queue, commandBuffer);
        }
        return destination;
    }

    // ------------------------------------------------------------------ little-endian byte helpers

    private static void writeInt(byte[] buffer, int value) {
        if (buffer.length >= Integer.BYTES) {
            buffer[0] = (byte) value;
            buffer[1] = (byte) (value >>> 8);
            buffer[2] = (byte) (value >>> 16);
            buffer[3] = (byte) (value >>> 24);
        }
    }

    private static void writeLong(byte[] buffer, long value) {
        if (buffer.length >= Long.BYTES) {
            for (int i = 0; i < Long.BYTES; i++) {
                buffer[i] = (byte) (value >>> (8 * i));
            }
        }
    }

    private static void writeCString(byte[] buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(bytes.length, buffer.length - 1);
        System.arraycopy(bytes, 0, buffer, 0, Math.max(length, 0));
        if (length >= 0 && length < buffer.length) {
            buffer[length] = 0;
        }
    }
}
