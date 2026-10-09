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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
     * transfer whose times were taken on the host clock.
     */
    private static final class TimingEvent {
        final long queuedNs;
        final long startNs;
        final long endNs;
        final long commandBuffer;
        /** The queue a kernel was encoded on and the shared-event value its command buffer signals; 0 for transfers. */
        final long queue;
        final long value;

        TimingEvent(long queuedNs, long startNs, long endNs, long commandBuffer) {
            this(queuedNs, startNs, endNs, commandBuffer, 0, 0);
        }

        TimingEvent(long queuedNs, long startNs, long endNs, long commandBuffer, long queue, long value) {
            this.queuedNs = queuedNs;
            this.startNs = startNs;
            this.endNs = endNs;
            this.commandBuffer = commandBuffer;
            this.queue = queue;
            this.value = value;
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

    public static long deviceMaxBufferLength(long device) {
        return MetalAPI.deviceMaxBufferLength(device);
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
        finish(queue);
        QueueEvent queueEvent = QUEUE_EVENTS.remove(queue);
        if (queueEvent != null) {
            synchronized (queueEvent) {
                if (queueEvent.lastCommitted != 0) {
                    ObjCRuntime.release(queueEvent.lastCommitted);
                    queueEvent.lastCommitted = 0;
                }
            }
            if (queueEvent.event != 0) {
                ObjCRuntime.release(queueEvent.event);
            }
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
        // A command buffer still in flight keeps its own reference to the buffer.
        BUFFER_USES.remove(buffer);
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
     * Kernels are encoded into one open command buffer per queue and committed in batches of
     * {@link #BATCH_SIZE} without waiting, instead of one command buffer per kernel that the CPU
     * commits and waits for. Commands in a compute encoder and command buffers on a queue run in the
     * order they were encoded, so the kernels of a task graph still run one after another. The CPU
     * waits only where it needs a result: a host read or write of a buffer that a pending command
     * buffer binds (see {@link #awaitBuffer}), a wait on an event, {@link #finish} and blits. Set
     * {@code -Dtornado.metal.dispatch.batch=False} to commit and wait for every kernel.
     */
    private static final boolean BATCH = Boolean.parseBoolean(System.getProperty("tornado.metal.dispatch.batch", "True"));

    /**
     * Kernels per command buffer before it is committed. Small batches keep the GPU working while the
     * CPU encodes the next kernels; large ones leave it idle until a whole batch is encoded. Measured
     * on an M4 Pro, Qwen3-0.6B decode (tg64, tok/s, F16 / Q8_0) at batch sizes 1, 2, 4, 8 and 32:
     * 58 / 60, 64 / 64, 57 / 58, 50 / 48 and 41 / 43, against 19 / 18 committing and waiting for
     * every kernel ({@code -Dtornado.metal.dispatch.batchSize=N} to change it).
     */
    private static final int BATCH_SIZE = Math.max(1, Integer.getInteger("tornado.metal.dispatch.batchSize", 2));

    /** Whether kernels are batched into shared command buffers rather than waited for one by one. */
    public static boolean batchesDispatches() {
        return BATCH;
    }

    /** An open command buffer and its compute encoder, both retained until the buffer is committed. */
    private static final class Batch {
        final long commandBuffer;
        final long encoder;
        /** The shared-event value the command buffer signals when its work is done. */
        final long value;
        int dispatches;

        Batch(long commandBuffer, long encoder, long value) {
            this.commandBuffer = commandBuffer;
            this.encoder = encoder;
            this.value = value;
        }
    }

    /** The queue and shared-event value of the last command buffer that bound a buffer. */
    private record BufferUse(long queue, long value) {
    }

    private static final Map<Long, BufferUse> BUFFER_USES = new ConcurrentHashMap<>();

    /**
     * Encodes one compute dispatch on {@code queue}, returning a timing event that holds the retained
     * command buffer for its GPU timestamps. With batching the dispatch joins the queue's open
     * command buffer and the call returns without waiting; otherwise it runs synchronously.
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

        QueueEvent queueEvent = queueEvent(queue);
        boolean batched = BATCH && queueEvent.event != 0;
        // Command buffers on one queue run in order; across queues they do not. A buffer that work
        // pending on another queue binds is waited for here, before this queue's lock is taken.
        for (Arg arg : state.args) {
            if (arg != null && arg.kind == 0 && arg.buffer != 0) {
                BufferUse use = BUFFER_USES.get(arg.buffer);
                if (use != null && use.queue() != queue) {
                    awaitBuffer(arg.buffer);
                }
            }
        }
        long retainedCommandBuffer;
        long value;
        // Encoding into a queue's open command buffer must not interleave with another thread's.
        synchronized (queueEvent) {
            try (Arena arena = Arena.ofConfined(); ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
                Batch batch = batched ? openBatch(queueEvent, queue) : null;
                long commandBuffer = batch != null ? batch.commandBuffer : MetalAPI.commandBuffer(queue);
                long encoder = batch != null ? batch.encoder : MetalAPI.computeCommandEncoder(commandBuffer);
                value = batch != null ? batch.value : ++queueEvent.value;
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
                                BUFFER_USES.put(arg.buffer, new BufferUse(queue, value));
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
                // Retain across the pool so GPUStartTime/GPUEndTime can be read when the profiler asks.
                retainedCommandBuffer = ObjCRuntime.retain(commandBuffer);
                if (batch != null) {
                    if (++batch.dispatches >= BATCH_SIZE) {
                        commitOpenBatch(queueEvent);
                    }
                } else {
                    MetalAPI.endEncoding(encoder);
                    commitSignalled(queueEvent, commandBuffer, value);
                }
            }
        }
        if (!batched) {
            waitForValue(queueEvent, value);
        }
        return registerEvent(new TimingEvent(queuedNs, 0, 0, retainedCommandBuffer, queue, value));
    }

    /** The queue's open command buffer, started if there is none. Caller holds the queue event's lock. */
    private static Batch openBatch(QueueEvent queueEvent, long queue) {
        if (queueEvent.open == null) {
            long commandBuffer = ObjCRuntime.retain(MetalAPI.commandBuffer(queue));
            long encoder = ObjCRuntime.retain(MetalAPI.computeCommandEncoder(commandBuffer));
            queueEvent.open = new Batch(commandBuffer, encoder, ++queueEvent.value);
        }
        return queueEvent.open;
    }

    /** Ends and commits the queue's open command buffer, if there is one. Caller holds the queue event's lock. */
    private static void commitOpenBatch(QueueEvent queueEvent) {
        Batch batch = queueEvent.open;
        if (batch == null) {
            return;
        }
        queueEvent.open = null;
        MetalAPI.endEncoding(batch.encoder);
        ObjCRuntime.release(batch.encoder);
        commitSignalled(queueEvent, batch.commandBuffer, batch.value);
        // The kernels' timing events hold their own references to the command buffer.
        ObjCRuntime.release(batch.commandBuffer);
    }

    /**
     * Commits {@code commandBuffer} so that it sets the queue's shared event to {@code value} when its
     * work is done. Values are taken and committed under the queue event's lock, so command buffers are
     * committed in the order of their values and a waiter for a lower value cannot return before its
     * own buffer has finished. Caller holds the lock.
     */
    private static void commitSignalled(QueueEvent queueEvent, long commandBuffer, long value) {
        if (queueEvent.event != 0) {
            MetalAPI.encodeSignalEvent(commandBuffer, queueEvent.event, value);
        }
        MetalAPI.commit(commandBuffer);
        long previous = queueEvent.lastCommitted;
        queueEvent.lastCommitted = ObjCRuntime.retain(commandBuffer);
        if (previous != 0) {
            ObjCRuntime.release(previous);
        }
    }

    /**
     * Wait for a committed command buffer by polling an {@code MTLSharedEvent} it signals, rather
     * than with {@code waitUntilCompleted}. The event is set as soon as the GPU finishes the encoded
     * work, while completion of the command buffer reaches the CPU some 60-90 us later on Apple
     * silicon, so this takes that delay off every wait. A queue whose last wait took longer than
     * {@link #SPIN_BUDGET_NS} blocks right away on the next one (see {@link QueueEvent#block}). Set
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

    /** Per command queue: a shared event, the last value it was asked to signal, and the open command buffer. */
    private static final class QueueEvent {
        final long event;
        long value;
        /** The command buffer kernels are being encoded into, or null. Guarded by this object's lock. */
        Batch open;
        /** The last committed command buffer, retained, for blocking waits. Guarded by this object's lock. */
        long lastCommitted;
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

    private static QueueEvent queueEvent(long queue) {
        return QUEUE_EVENTS.computeIfAbsent(queue, q -> new QueueEvent(MetalAPI.newSharedEvent(MetalAPI.queueDevice(q))));
    }

    /** Commits {@code commandBuffer} on {@code queue}, after anything still open there, and returns once the GPU has finished it. */
    private static void commitAndWait(long queue, long commandBuffer) {
        QueueEvent queueEvent = queueEvent(queue);
        long value;
        synchronized (queueEvent) {
            commitOpenBatch(queueEvent);
            value = ++queueEvent.value;
            commitSignalled(queueEvent, commandBuffer, value);
        }
        waitForValue(queueEvent, value);
    }

    /** Returns once the command buffer that signals {@code value} on the queue has finished, committing it first if it is still open. */
    private static void waitForValue(QueueEvent queueEvent, long value) {
        long commandBuffer;
        synchronized (queueEvent) {
            if (queueEvent.open != null && queueEvent.open.value <= value) {
                commitOpenBatch(queueEvent);
            }
            if (queueEvent.event != 0 && MetalAPI.sharedEventSignaledValue(queueEvent.event) >= value) {
                return;
            }
            if (queueEvent.lastCommitted == 0) {
                return;
            }
            // Command buffers on a queue finish in order, so the last committed one finishing
            // implies the one that signals value has finished.
            commandBuffer = ObjCRuntime.retain(queueEvent.lastCommitted);
        }
        try {
            if (!SPIN_WAIT || queueEvent.event == 0) {
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
        } finally {
            ObjCRuntime.release(commandBuffer);
        }
    }

    /**
     * Returns once no command buffer that binds {@code buffer} is pending, so the host can read or
     * write its contents. Buffers the pending work does not bind -- the common case for a task's
     * inputs -- need no wait.
     */
    private static void awaitBuffer(long buffer) {
        BufferUse use = BUFFER_USES.get(buffer);
        if (use == null) {
            return;
        }
        QueueEvent queueEvent = QUEUE_EVENTS.get(use.queue());
        if (queueEvent == null || (queueEvent.event != 0 && MetalAPI.sharedEventSignaledValue(queueEvent.event) >= use.value())) {
            return;
        }
        waitForValue(queueEvent, use.value());
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
        awaitBuffer(buffer);
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
        awaitBuffer(buffer);
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
        awaitBuffer(buffer);
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
        awaitBuffer(buffer);
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

    /** Commits the queue's open command buffer without waiting for it. */
    public static void flush(long queue) {
        QueueEvent queueEvent = QUEUE_EVENTS.get(queue);
        if (queueEvent != null) {
            synchronized (queueEvent) {
                commitOpenBatch(queueEvent);
            }
        }
    }

    /** Returns once everything encoded on the queue has finished, committing its open command buffer first. */
    public static void finish(long queue) {
        if (queue == 0) {
            return;
        }
        QueueEvent queueEvent = QUEUE_EVENTS.get(queue);
        if (queueEvent == null) {
            // Nothing was committed on this queue here; an empty command buffer waits for anything else on it.
            try (ObjCRuntime.AutoreleasePool pool = new ObjCRuntime.AutoreleasePool()) {
                long commandBuffer = MetalAPI.commandBuffer(queue);
                if (commandBuffer != 0) {
                    MetalAPI.commit(commandBuffer);
                    MetalAPI.waitUntilCompleted(commandBuffer);
                }
            }
            return;
        }
        long value;
        synchronized (queueEvent) {
            value = queueEvent.value;
        }
        waitForValue(queueEvent, value);
    }

    /** Barriers and markers reduce to waiting on the listed events. */
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
        if (state == null) {
            return;
        }
        QueueEvent queueEvent = state.queue != 0 ? QUEUE_EVENTS.get(state.queue) : null;
        if (queueEvent != null) {
            waitForValue(queueEvent, state.value);
        } else if (state.commandBuffer != 0) {
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
        // CL_COMPLETE == 0 as a little-endian int. Kernels may still be pending, but every wait on an
        // event commits and waits for its command buffer, so callers never act on a stale status.
        writeInt(buffer, 0);
    }

    public static void eventProfilingInfo(long event, long param, byte[] buffer) {
        java.util.Arrays.fill(buffer, (byte) 0);
        TimingEvent state = EVENTS.get(event);
        if (state == null || buffer.length < 8) {
            return;
        }
        long time;
        if (state.commandBuffer != 0) {
            // Commits the kernel's command buffer if it is still open. The GPU timestamps are set
            // when the buffer completes, which can trail the wait by tens of us. They cover every
            // kernel of a batch; the profiler waits after each kernel, so its buffers hold one.
            waitOne(event);
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
