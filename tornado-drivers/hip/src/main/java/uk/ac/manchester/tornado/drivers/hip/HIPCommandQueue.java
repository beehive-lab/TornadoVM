/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.hip;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandQueueInfo.CL_QUEUE_CONTEXT;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandQueueInfo.CL_QUEUE_DEVICE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import uk.ac.manchester.tornado.runtime.common.JavaKind;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.hip.ffm.ROCTXAPI;
import uk.ac.manchester.tornado.drivers.hip.natives.NativeCommandQueue;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class HIPCommandQueue extends CommandQueue {

    protected static final Event EMPTY_EVENT = new EmptyEvent();
    private TornadoLogger logger = new TornadoLogger(this.getClass());

    private final long commandQueuePtr;

    /**
     * Small buffer for querying properties regarding the command queue.
     * This is useful for debugging.
     */
    private final ByteBuffer buffer;
    private final long properties;
    private final int openclVersion;

    /**
     * Set when work is issued to this (secondary) queue under intra-plan concurrency; drained
     * by the per-plan join (see {@code HIPDeviceContext#joinRoleQueues}), which only needs to
     * synchronise queues that were actually touched since the last join.
     */
    private volatile boolean dirty;

    public HIPCommandQueue(long commandQueuePtr, long properties, int version) {
        this.commandQueuePtr = commandQueuePtr;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(HIPDriver.BYTE_ORDER);
        this.openclVersion = version;
    }

    public long getCommandQueuePtr() {
        return commandQueuePtr;
    }

    /** Marks this queue as having received work since the last join. */
    public void markDirty() {
        if (!dirty) {
            dirty = true;
        }
    }

    /** Returns whether this queue was touched since the last join, clearing the flag. */
    public boolean pollDirty() {
        if (dirty) {
            dirty = false;
            return true;
        }
        return false;
    }

    /**
     * Labels this queue's HIP stream with its role (DEFAULT / H2D / COMPUTE / D2H) so the rocprof
     * Systems timeline shows named stream rows instead of raw stream ids.
     */
    public void nameStream(String name) {
        nvtxNameStream(commandQueuePtr, name);
    }

    static void clReleaseCommandQueue(long queueId) throws HIPException {
        HIPHandles.Queue queue = (HIPHandles.Queue) HIPHandles.release(queueId);
        if (queue != null) {
            HIPDriverAPI.hipStreamDestroy(queue.stream());
        }
    }

    /**
     * Labels a HIP stream with a human-readable name (its role: DEFAULT / H2D / COMPUTE / D2H) so
     * the rocprof timeline shows named stream rows instead of raw stream ids.
     */
    private static void nvtxNameStream(long queueId, String name) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue != null && name != null) {
            ROCTXAPI.nameStream(queue.stream(), name);
        }
    }

    /**
     * Opens a host-side ROCTX range labelled {@code name} (no-op without a profiler), so native
     * library tasks (hipBLAS, MIOpen, Composable Kernel, ...) appear as named spans on the rocprof
     * Systems timeline next to the backend's own kernel and transfer ranges.
     */
    public static void nvtxRangePush(String name) {
        ROCTXAPI.rangePush(name == null ? "library-task" : name);
    }

    /** Closes the most recently opened host-side ROCTX range. */
    public static void nvtxRangePop() {
        ROCTXAPI.rangePop();
    }

    /**
     * Whether per-operation device timing is wanted, i.e. the TornadoVM profiler is active. Pushed
     * from Java before each plan execution. When off, the START timestamp event is skipped entirely
     * (halving the events per operation) and the remaining events are created with
     * {@code hipEventDisableTiming}, which is cheaper to record. Events without a start report an
     * elapsed time of zero.
     */
    private static volatile boolean timingEnabled = true;

    /** hipError_t for a call the backend rejects before it reaches the driver. */
    private static final int HIP_ERROR_INVALID_VALUE = 1;

    /** {@code hipEventDefault} and {@code hipEventDisableTiming}. */
    private static final int HIP_EVENT_DEFAULT = 0x0;
    private static final int HIP_EVENT_DISABLE_TIMING = 0x2;

    /** Default block size when the caller supplies no local work size. */
    private static final int DEFAULT_BLOCK_SIZE = 256;

    static void nativeEnableTiming(boolean enabled) {
        timingEnabled = enabled;
    }

    private static int eventFlags() {
        return timingEnabled ? HIP_EVENT_DEFAULT : HIP_EVENT_DISABLE_TIMING;
    }

    /**
     * How many times the issue path is exercised when a queue is created. The first calls through a
     * downcall handle are the expensive ones -- the handle is linked and the surrounding Java is
     * still interpreted -- and a few repetitions are enough to get past that.
     */
    private static final int WARM_UP_ITERATIONS = 256;

    /** Size of the throwaway buffer the warm-up copies; small enough that the copy itself is free. */
    private static final int WARM_UP_BYTES = 256;

    /**
     * Runs the transfer issue path a few times on a throwaway buffer, so the work of linking the
     * downcall handles happens here rather than inside the first transfer of the first plan.
     *
     * <p>
     * Without this the first few copies measure several times their steady-state cost. The device
     * timestamps the profiler reports bracket the host work that issues the copy -- the end event
     * cannot be recorded until the host gets to it -- so warm-up on the issue path shows up as
     * device time and skews the first execution's transfer numbers. Failures are ignored: this is
     * an optimisation, and anything genuinely wrong with the queue will be reported by the real
     * work that follows.
     */
    static void warmUpIssuePath(long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return;
        }
        long devicePointer = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate(WARM_UP_BYTES, Long.BYTES);
            MemorySegment allocation = FFMSupport.allocateLong(arena);
            if (HIPDriverAPI.hipMalloc(allocation, WARM_UP_BYTES) != HIPDriverAPI.HIP_SUCCESS) {
                return;
            }
            devicePointer = allocation.get(FFMSupport.C_LONG, 0);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                discardEvent(transferToDevice(queue, host.address(), 0, 0, WARM_UP_BYTES, devicePointer, null, true));
                discardEvent(transferToHost(queue, host.address(), 0, 0, WARM_UP_BYTES, devicePointer, null, true));
            }
        } catch (HIPException | RuntimeException e) {
            // An optimisation only; the real work that follows will report anything genuinely wrong.
        } finally {
            if (devicePointer != 0) {
                HIPDriverAPI.hipFree(devicePointer);
            }
        }
    }

    static void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws HIPException {
        Arrays.fill(buffer, (byte) 0);
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null || buffer.length < Long.BYTES) {
            return;
        }
        long value = 0;
        if (info == CL_QUEUE_CONTEXT.getValue()) {
            value = queue.context();
        } else if (info == CL_QUEUE_DEVICE.getValue()) {
            value = queue.device();
        }
        ByteBuffer.wrap(buffer).order(HIPDriver.BYTE_ORDER).putLong(value);
    }

    /**
     * Creates a {@code hipEvent_t}, records it on the queue's stream and returns its handle. This is
     * the event result the OpenCL clone expects from an enqueue. No start event is recorded, so the
     * reported elapsed time is zero -- which is what markers and barriers want, since they do not
     * bracket a timed operation.
     */
    private static long recordEvent(HIPHandles.Queue queue) throws HIPException {
        // An event can only be recorded on a stream of the context it was created in.
        HIPDriverAPI.hipCtxSetCurrent(queue.context());
        long event = createEvent(eventFlags(), "hipEventCreateWithFlags");
        // A marker alone is no work to wait for. With asynchronous graph launches it must not
        // re-arm the drain, or the flush after every execution waits for the graph just launched.
        if (!ASYNC_GRAPH_LAUNCH) {
            queue.markPending();
        }
        int result = HIPDriverAPI.hipEventRecord(event, queue.stream());
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            HIPDriverAPI.hipEventDestroy(event);
            throw new HIPException(HIPDriverAPI.describe("hipEventRecord", result));
        }
        return HIPHandles.register(new HIPHandles.Event(event, 0));
    }

    private static long createEvent(int flags, String call) throws HIPException {
        MemorySegment event = FFMSupport.scratchPointer();
        int result = HIPDriverAPI.hipEventCreateWithFlags(event, flags);
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            throw new HIPException(HIPDriverAPI.describe(call, result));
        }
        return event.get(FFMSupport.C_POINTER, 0).address();
    }

    /**
     * Records a START timestamp on the stream when the profiler is active, to be paired with
     * {@link #endEvent} after the operation so that {@code hipEventElapsedTime(start, end)} yields
     * its device time. Returns {@code 0} when timing is off.
     */
    private static long beginEvent(HIPHandles.Queue queue) throws HIPException {
        if (!timingEnabled) {
            return 0;
        }
        long start = createEvent(HIP_EVENT_DEFAULT, "hipEventCreateWithFlags(start)");
        queue.markPending();
        int result = HIPDriverAPI.hipEventRecord(start, queue.stream());
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            HIPDriverAPI.hipEventDestroy(start);
            throw new HIPException(HIPDriverAPI.describe("hipEventRecord(start)", result));
        }
        return start;
    }

    /**
     * Records the completion event for an operation opened with {@link #beginEvent} and returns the
     * handle carrying both. This event doubles as the operation's dependency handle, for
     * {@code hipStreamWaitEvent} and status queries, so it is always created.
     *
     * @param dispatchStart
     *     {@code System.nanoTime()} taken just before the driver call that issued the operation; the
     *     time since then is kept as the event's driver dispatch time when timing is on.
     */
    private static long endEvent(long start, HIPHandles.Queue queue, long dispatchStart) throws HIPException {
        long dispatchTime = timingEnabled ? System.nanoTime() - dispatchStart : 0;
        long end;
        try {
            end = createEvent(eventFlags(), "hipEventCreateWithFlags(end)");
        } catch (HIPException e) {
            // The start event recorded by beginEvent has to go back to the driver too.
            destroyEvent(start);
            throw e;
        }
        queue.markPending();
        int result = HIPDriverAPI.hipEventRecord(end, queue.stream());
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            destroyEvent(start);
            HIPDriverAPI.hipEventDestroy(end);
            throw new HIPException(HIPDriverAPI.describe("hipEventRecord(end)", result));
        }
        return HIPHandles.register(new HIPHandles.Event(end, start, dispatchTime));
    }

    private static void destroyEvent(long event) {
        if (event != 0) {
            HIPDriverAPI.hipEventDestroy(event);
        }
    }

    /**
     * Releases an event handle whose value is about to be dropped, so its hipEvent_ts go back to the
     * driver rather than leaking when the caller unwinds instead of returning it.
     */
    private static void discardEvent(long handle) {
        HIPHandles.Event event = (HIPHandles.Event) HIPHandles.release(handle);
        if (event != null) {
            destroyEvent(event.start());
            destroyEvent(event.event());
        }
    }

    /**
     * Makes the queue's stream wait, GPU-side, on each event of the wait list, which is laid out as
     * {@code [count, e0, e1, ...]}. hipEvent_ts are valid across streams, which is what allows
     * cross-queue ordering when a plan runs with intra-plan concurrency (one queue per role).
     */
    private static void waitEvents(HIPHandles.Queue queue, long[] events) {
        if (events == null || events.length == 0) {
            return;
        }
        int count = (int) Math.min(events[0], events.length - 1L);
        for (int i = 0; i < count; i++) {
            HIPHandles.Event event = HIPHandles.resolve(events[i + 1], HIPHandles.Event.class);
            if (event != null) {
                queue.markPending();
                HIPDriverAPI.hipStreamWaitEvent(queue.stream(), event.event(), 0);
            }
        }
    }

    /**
     * Whether the queue's stream is currently capturing into a HIP graph. During capture the
     * stream must not be synchronised -- that invalidates the capture -- so the transfer helpers
     * skip their post-copy synchronise while it is.
     */
    private static boolean isCapturing(HIPHandles.Queue queue) {
        MemorySegment status = FFMSupport.scratchInt();
        if (HIPDriverAPI.hipStreamIsCapturing(queue.stream(), status) != HIPDriverAPI.HIP_SUCCESS) {
            return false;
        }
        return status.get(FFMSupport.C_INT, 0) == HIPDriverAPI.HIP_STREAM_CAPTURE_STATUS_ACTIVE;
    }

    /**
     * Formats "H2D 24.0 MB" / "D2H 24 B" so individual copies are identifiable on the timeline.
     *
     * <p>
     * A plan moves the same buffers on every iteration, so the same handful of labels is wanted
     * over and over; they are formatted once and remembered. The cost of formatting one lands
     * between the copy and its end event, and therefore inside the device time the profiler
     * reports, which is what makes it worth avoiding on the repeat. The cache is capped so an
     * unusual workload with many distinct sizes formats afresh rather than growing without limit.
     */
    private static final int LABEL_CACHE_LIMIT = 512;

    private static final Map<Long, String> WRITE_LABELS = new ConcurrentHashMap<>();

    private static final Map<Long, String> READ_LABELS = new ConcurrentHashMap<>();

    private static String transferLabel(Map<Long, String> cache, String direction, long numBytes) {
        String label = cache.get(numBytes);
        if (label != null) {
            return label;
        }
        label = formatTransferLabel(direction, numBytes);
        if (cache.size() < LABEL_CACHE_LIMIT) {
            cache.put(numBytes, label);
        }
        return label;
    }

    private static String formatTransferLabel(String direction, long numBytes) {
        if (numBytes >= 1048576) {
            return String.format("%s %.1f MB", direction, numBytes / 1048576.0);
        } else if (numBytes >= 1024) {
            return String.format("%s %.1f KB", direction, numBytes / 1024.0);
        }
        return direction + " " + numBytes + " B";
    }

    /**
     * Dispatch a HIP kernel, translating an OpenCL NDRange into a grid/block launch.
     * {@code globalWorkSize} is the total thread count per dimension and {@code localWorkSize} the
     * block dimension; when no local size is given, a default block is picked and the grid derived.
     *
     * @param queueId
     *     command queue handle
     * @param kernelId
     *     kernel handle
     * @param dim
     *     dimensions of the kernel (1D, 2D or 3D)
     * @param globalWorkOffset
     *     offset within global access
     * @param globalWorkSize
     *     total number of threads to launch
     * @param localWorkSize
     *     local work group size
     * @param events
     *     wait list, laid out as {@code [count, e0, e1, ...]}
     * @return the launch event's handle
     * @throws HIPException
     *     if the launch fails
     */
    static long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        HIPHandles.Kernel kernel = HIPHandles.resolve(kernelId, HIPHandles.Kernel.class);
        if (queue == null || kernel == null) {
            return 0;
        }

        long[] global = { 1, 1, 1 };
        long[] local = { 0, 0, 0 };
        if (globalWorkSize != null) {
            System.arraycopy(globalWorkSize, 0, global, 0, Math.min(globalWorkSize.length, 3));
        }
        if (localWorkSize != null) {
            System.arraycopy(localWorkSize, 0, local, 0, Math.min(localWorkSize.length, 3));
        }

        int[] block = new int[3];
        block[0] = local[0] > 0 ? (int) local[0] : (dim >= 1 ? DEFAULT_BLOCK_SIZE : 1);
        block[1] = local[1] > 0 ? (int) local[1] : 1;
        block[2] = local[2] > 0 ? (int) local[2] : 1;
        if (global[0] > 0 && block[0] > global[0]) {
            block[0] = (int) global[0];
        }

        int[] grid = new int[3];
        for (int i = 0; i < 3; i++) {
            grid[i] = (int) ((global[i] + block[i] - 1) / block[i]);
            if (grid[i] == 0) {
                grid[i] = 1;
            }
        }

        // Label the launch with the actual kernel name: the profiler's own function-name table can
        // go stale when modules are unloaded and their handles recycled (kernels then show under a
        // previous kernel's name), so the ROCTX row is the reliable source.
        ROCTXAPI.rangePush(kernel.name);
        try (Arena arena = Arena.ofConfined()) {
            HIPDriverAPI.hipCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            queue.markPending();
            long dispatchStart = System.nanoTime();
            int result = HIPDriverAPI.hipModuleLaunchKernel(kernel.function, grid[0], grid[1], grid[2], block[0], block[1], block[2], 0, queue.stream(), kernelParameters(arena, kernel),
                    MemorySegment.NULL);
            long launchEvent = endEvent(start, queue, dispatchStart);
            // A failed launch leaves the kernel's outputs untouched. Surfacing it makes the caller
            // bail out instead of returning stale buffers as a valid result.
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                discardEvent(launchEvent);
                throw new HIPException(HIPDriverAPI.describe("hipModuleLaunchKernel", result));
            }
            return launchEvent;
        } finally {
            ROCTXAPI.rangePop();
        }
    }

    /** Builds the {@code kernelParams} vector of pointers into copies of the staged argument blobs. */
    private static MemorySegment kernelParameters(Arena arena, HIPHandles.Kernel kernel) {
        int count = kernel.arguments.size();
        if (count == 0) {
            return MemorySegment.NULL;
        }
        MemorySegment parameters = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, count);
        for (int i = 0; i < count; i++) {
            byte[] argument = kernel.arguments.get(i);
            MemorySegment slot = MemorySegment.NULL;
            if (argument != null && argument.length > 0) {
                slot = arena.allocate(argument.length, Long.BYTES);
                MemorySegment.copy(argument, 0, slot, FFMSupport.C_CHAR, 0, argument.length);
            }
            parameters.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), slot);
        }
        return parameters;
    }

    /**
     * Host-to-device copy of {@code numBytes} from {@code hostPointer + hostOffset} to
     * {@code devicePointer + deviceOffset}.
     *
     * @param syncAfter
     *     the caller requires the copy to have completed on return. Always true for Java-array
     *     transfers, whose staging buffer is reused as soon as this returns, and for blocking
     *     off-heap transfers; false for async off-heap transfers, whose completion is ordered by
     *     events and the end-of-plan sync. While capturing into a HIP graph the stream must not be
     *     synchronised, so the sync is skipped: the copy becomes a graph node whose host pointer, a
     *     stable off-heap segment, is re-read on each graph launch.
     */
    private static long transferToDevice(HIPHandles.Queue queue, long hostPointer, long hostOffset, long deviceOffset, long numBytes, long devicePointer, long[] events, boolean syncAfter)
            throws HIPException {
        ROCTXAPI.rangePush(transferLabel(WRITE_LABELS, "H2D", numBytes));
        try {
            HIPDriverAPI.hipCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            queue.markPending();
            long dispatchStart = System.nanoTime();
            int result = HIPDriverAPI.hipMemcpyHtoDAsync(devicePointer + deviceOffset, hostPointer + hostOffset, numBytes, queue.stream());
            // Record the completion event BEFORE draining, not after. Recording afterwards puts
            // fresh work on the stream and re-arms `pending`, so the flush that follows an
            // execution synchronises an otherwise idle stream. With this order the drain covers the
            // event record too and the stream is genuinely clear afterwards. The event still marks
            // the end of the copy either way -- it is recorded on the stream after it.
            long event = endEvent(start, queue, dispatchStart);
            result = syncIfNeeded(queue, result, syncAfter);
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                discardEvent(event);
                throw new HIPException(HIPDriverAPI.describe("hipMemcpyHtoDAsync", result));
            }
            return event;
        } finally {
            ROCTXAPI.rangePop();
        }
    }

    /** Device-to-host counterpart of {@link #transferToDevice}. */
    private static long transferToHost(HIPHandles.Queue queue, long hostPointer, long hostOffset, long deviceOffset, long numBytes, long devicePointer, long[] events, boolean syncAfter)
            throws HIPException {
        ROCTXAPI.rangePush(transferLabel(READ_LABELS, "D2H", numBytes));
        try {
            HIPDriverAPI.hipCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            queue.markPending();
            if (isCapturing(queue)) {
                HOST_COPY_CAPTURES.add(queue.stream());
            }
            long dispatchStart = System.nanoTime();
            int result = HIPDriverAPI.hipMemcpyDtoHAsync(hostPointer + hostOffset, devicePointer + deviceOffset, numBytes, queue.stream());
            // Record the completion event BEFORE draining, not after. Recording afterwards puts
            // fresh work on the stream and re-arms `pending`, so the flush that follows an
            // execution synchronises an otherwise idle stream. With this order the drain covers the
            // event record too and the stream is genuinely clear afterwards. The event still marks
            // the end of the copy either way -- it is recorded on the stream after it.
            long event = endEvent(start, queue, dispatchStart);
            result = syncIfNeeded(queue, result, syncAfter);
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                discardEvent(event);
                throw new HIPException(HIPDriverAPI.describe("hipMemcpyDtoHAsync", result));
            }
            return event;
        } finally {
            ROCTXAPI.rangePop();
        }
    }

    /** Returns the first failure of the copy and the synchronise that follows it, if any. */
    private static int syncIfNeeded(HIPHandles.Queue queue, int copyResult, boolean syncAfter) {
        if (!syncAfter || isCapturing(queue)) {
            return copyResult;
        }
        int sync = drain(queue);
        return copyResult != HIPDriverAPI.HIP_SUCCESS ? copyResult : sync;
    }

    /**
     * Synchronises the stream, unless nothing has been enqueued since it was last drained.
     *
     * <p>
     * A task-graph execution ends with a blocking read-back, then a flush, then a wait: three
     * {@code hipStreamSynchronize} calls, of which the last two are issued against a stream the
     * read-back already emptied. The driver round trip for those buys nothing, and on a
     * dispatch-bound plan it is a measurable share of the per-execution cost.
     *
     * <p>
     * The flag is only ever cleared after the driver reports success, so a failed synchronise
     * leaves the queue marked pending and the next caller tries again rather than assuming the
     * stream drained.
     */
    private static int drain(HIPHandles.Queue queue) {
        if (!queue.isPending()) {
            return HIPDriverAPI.HIP_SUCCESS;
        }
        int result = HIPDriverAPI.hipStreamSynchronize(queue.stream());
        if (result == HIPDriverAPI.HIP_SUCCESS) {
            queue.clearPending();
        }
        return result;
    }

    /**
     * Per-thread staging buffer for transfers whose host side is a Java array.
     *
     * <p>
     * A downcall cannot address the Java heap, so an array's bytes are copied through native memory
     * rather than pinned in place the way the JNI critical region used to. The buffer is per-thread
     * and reused, so the cost is one host memcpy per transfer and not an allocation as well; it is
     * safe to reuse because every array transfer synchronises before it returns. Off-heap
     * transfers, which is what the runtime's own segment-backed arrays use, never come through
     * here.
     */
    private static final FFMSupport.Staging STAGING = new FFMSupport.Staging();

    private static long writeArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        MemorySegment staging = STAGING.forBytes(bytes);
        MemorySegment.copy(array, elementOffset, staging, layout, 0, elementCount);
        return transferToDevice(queue, staging.address(), 0, offset, bytes, ptr, events, true);
    }

    private static long readArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        MemorySegment staging = STAGING.forBytes(bytes);
        long event = transferToHost(queue, staging.address(), 0, offset, bytes, ptr, events, true);
        MemorySegment.copy(staging, layout, 0, array, elementOffset, elementCount);
        return event;
    }

    static long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return transferToDevice(queue, hostPointer, hostOffset, offset, bytes, ptr, events, blocking);
    }

    static long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        return readArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDeviceOffHeap(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return transferToHost(queue, hostPointer, hostOffset, offset, bytes, ptr, events, blocking);
    }

    static void clEnqueueWaitForEvents(long queueId, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue != null) {
            waitEvents(queue, events);
        }
    }

    /** Allocates page-locked (pinned) host memory for the staged-transfer ring; 0 on failure. */
    static long hipHostMalloc(long numBytes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hostPointer = FFMSupport.allocatePointer(arena);
            if (HIPDriverAPI.hipHostMalloc(hostPointer, numBytes, 0) != HIPDriverAPI.HIP_SUCCESS) {
                return 0L;
            }
            return hostPointer.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /** Releases pinned host memory allocated by {@link #hipHostMalloc(long)}. */
    static void hipHostFree(long hostPtr) {
        HIPDriverAPI.hipHostFree(hostPtr);
    }

    /**
     * Plain host-side copy between raw pointers. The staged-transfer path uses it to fill a pinned
     * staging slot from the pageable source segment on the CPU while a previous slot's async DMA is
     * still in flight.
     */
    static void memcpyHostToHost(long dstPtr, long srcPtr, long numBytes) {
        MemorySegment.copy(FFMSupport.asSegment(srcPtr, numBytes), 0, FFMSupport.asSegment(dstPtr, numBytes), 0, numBytes);
    }

    static long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return recordEvent(queue);
    }

    static long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        // Honour the wait list, then place a marker event.
        waitEvents(queue, events);
        return recordEvent(queue);
    }

    static void clFlush(long queueId) throws HIPException {
        synchronizeStream(queueId);
    }

    static void clFinish(long queueId) throws HIPException {
        synchronizeStream(queueId);
    }

    private static void synchronizeStream(long queueId) throws HIPException {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return;
        }
        int result = drain(queue);
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            throw new HIPException(HIPDriverAPI.describe("hipStreamSynchronize", result));
        }
    }

    /* ---- HIP Graph (stream capture) ---- */

    /**
     * Puts the queue's stream into capture mode; {@code mode} maps directly onto
     * {@code hipStream_tCaptureMode}. Returns the {@code hipError_t}.
     */
    private static long hipStreamBeginCapture(long queueId, int mode) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return HIP_ERROR_INVALID_VALUE;
        }
        HIPDriverAPI.hipCtxSetCurrent(queue.context());
        return HIPDriverAPI.hipStreamBeginCapture(queue.stream(), mode);
    }

    /** Ends capture and returns the constructed {@code hipGraph_t} as a raw pointer (0 on failure). */
    private static long hipStreamEndCapture(long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        HIPDriverAPI.hipCtxSetCurrent(queue.context());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment graph = FFMSupport.allocatePointer(arena);
            if (HIPDriverAPI.hipStreamEndCapture(queue.stream(), graph) != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            return graph.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    private static boolean hipStreamIsCapturing(long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        return queue != null && isCapturing(queue);
    }

    /** Instantiates an executable graph and returns the {@code hipGraphExec_t} (0 on failure). */
    private static long hipGraphInstantiate(long graphHandle) {
        if (graphHandle == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment graphExec = FFMSupport.allocatePointer(arena);
            if (HIPDriverAPI.hipGraphInstantiateWithFlags(graphExec, graphHandle, 0) != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            return graphExec.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /**
     * Attempts to update an instantiated graph in place with the topology of a new {@code hipGraph_t}.
     * Returns {@code HIP_SUCCESS} when the update succeeded, otherwise the error code so the
     * caller can fall back to re-instantiation.
     */
    private static long hipGraphExecUpdate(long graphExecHandle, long graphHandle) {
        if (graphExecHandle == 0 || graphHandle == 0) {
            return HIP_ERROR_INVALID_VALUE;
        }
        try (Arena arena = Arena.ofConfined()) {
            // The node that failed and why: the runtime fills them in, and only the hipError_t is used.
            MemorySegment errorNode = FFMSupport.allocatePointer(arena);
            MemorySegment updateResult = FFMSupport.allocateInt(arena);
            return HIPDriverAPI.hipGraphExecUpdate(graphExecHandle, graphHandle, errorNode, updateResult);
        }
    }

    /** Launches an instantiated graph on the queue's stream. */
    private static long hipGraphLaunch(long graphExecHandle, long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (graphExecHandle == 0 || queue == null) {
            return HIP_ERROR_INVALID_VALUE;
        }
        HIPDriverAPI.hipCtxSetCurrent(queue.context());
        queue.markPending();
        return HIPDriverAPI.hipGraphLaunch(graphExecHandle, queue.stream());
    }

    private static long hipGraphExecDestroy(long graphExecHandle) {
        return graphExecHandle == 0 ? HIPDriverAPI.HIP_SUCCESS : HIPDriverAPI.hipGraphExecDestroy(graphExecHandle);
    }

    private static long hipGraphDestroy(long graphHandle) {
        return graphHandle == 0 ? HIPDriverAPI.HIP_SUCCESS : HIPDriverAPI.hipGraphDestroy(graphHandle);
    }

    /* ---- Native interop (external libraries, e.g. hipBLAS) ---- */

    /**
     * Hands the raw stream to an external library (hipBLAS, MIOpen, hipFFT, Composable Kernel, ...).
     *
     * <p>
     * Those libraries enqueue on it without going through this class, so the queue is marked
     * pending here: we cannot see what they do, and skipping a later synchronise because our own
     * bookkeeping says the stream is idle would read their results before they land. Marking on
     * handing out the pointer is deliberately conservative -- a library task simply never takes the
     * skip -- and it costs one flag write per dispatch, not per operation.
     */
    private static long getStreamPointer(long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        queue.markPending();
        return queue.stream();
    }

    private static long getContextPointer(long queueId) {
        HIPHandles.Queue queue = HIPHandles.resolve(queueId, HIPHandles.Queue.class);
        return queue == null ? 0 : queue.context();
    }

    /**
     * Raw hipStream_t handle of this queue, for handing to external native
     * libraries (e.g., {@code cublasSetStream}) so their work is ordered with
     * TornadoVM kernels and transfers on the same stream.
     */
    public long getNativeStream() {
        return getStreamPointer(commandQueuePtr);
    }

    /**
     * Raw context handle of this queue.
     */
    public long getNativeContext() {
        return getContextPointer(commandQueuePtr);
    }

    /**
     * hipStreamCaptureModeThreadLocal: while this stream is capturing, potentially unsafe calls
     * (allocation, synchronous memset, graph instantiation, ...) are refused on the capturing thread
     * only. In global mode they were refused on every thread of the process, so two plans capturing
     * on two threads at the same time failed at random: one plan's {@code hipGraphInstantiate} or
     * buffer allocation hit {@code HIP_ERROR_STREAM_CAPTURE_UNSUPPORTED} because of the other's
     * capture.
     */
    private static final int HIP_STREAM_CAPTURE_MODE_THREAD_LOCAL = 1;

    /** True while this queue's stream is recording operations into a HIP graph. */
    private boolean capturing = false;

    /**
     * With {@code tornado.hip.graph.asyncLaunch}, a graph that copies nothing back to the host is
     * launched without waiting for it, so the next launch on the stream overlaps its execution.
     */
    private static final boolean ASYNC_GRAPH_LAUNCH = Boolean.getBoolean("tornado.hip.graph.asyncLaunch");

    /** Streams whose capture in progress has recorded a device-to-host copy. */
    private static final Set<Long> HOST_COPY_CAPTURES = ConcurrentHashMap.newKeySet();

    /** The instantiated graphs of this queue that copy to the host. */
    private final Set<Long> hostCopyGraphs = ConcurrentHashMap.newKeySet();

    private long streamOf() {
        HIPHandles.Queue queue = HIPHandles.resolve(commandQueuePtr, HIPHandles.Queue.class);
        return queue == null ? 0 : queue.stream();
    }

    /**
     * Begins recording all subsequent operations submitted to this queue's
     * stream into a HIP graph.
     */
    public void beginGraphCapture() {
        long result = hipStreamBeginCapture(commandQueuePtr, HIP_STREAM_CAPTURE_MODE_THREAD_LOCAL);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("hipStreamBeginCapture failed. hipError_t=" + result);
        }
        capturing = true;
        HOST_COPY_CAPTURES.remove(streamOf());
    }

    /**
     * Ends capture and instantiates the recorded graph. The source hipGraph_t is
     * destroyed after instantiation; the returned handle is the hipGraphExec_t.
     *
     * @return opaque hipGraphExec_t handle
     */
    public long endGraphCaptureAndInstantiate() {
        capturing = false;
        boolean copiesToHost = HOST_COPY_CAPTURES.remove(streamOf());
        long graphHandle = hipStreamEndCapture(commandQueuePtr);
        if (graphHandle == 0) {
            throw new TornadoBailoutRuntimeException("hipStreamEndCapture returned a null graph");
        }
        long graphExecHandle = hipGraphInstantiate(graphHandle);
        hipGraphDestroy(graphHandle);
        if (graphExecHandle == 0) {
            throw new TornadoBailoutRuntimeException("hipGraphInstantiate failed");
        }
        if (copiesToHost) {
            hostCopyGraphs.add(graphExecHandle);
        }
        return graphExecHandle;
    }

    /**
     * Abandons a capture that failed part-way: ends it if the stream is still capturing and throws
     * the partial graph away, so that the stream can be used again.
     */
    public void abortGraphCapture() {
        if (!capturing) {
            return;
        }
        capturing = false;
        long graphHandle = hipStreamEndCapture(commandQueuePtr);
        if (graphHandle != 0) {
            hipGraphDestroy(graphHandle);
        }
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Launches a previously instantiated graph on this queue's stream and
     * blocks until completion so that captured device-to-host copies are
     * visible to the host once this call returns. With
     * {@code tornado.hip.graph.asyncLaunch}, a graph that copies nothing to
     * the host returns as soon as it is launched.
     */
    public void launchGraph(long graphExecHandle) {
        if (ASYNC_GRAPH_LAUNCH && !hostCopyGraphs.contains(graphExecHandle)) {
            // Not marked pending: the stream orders it before anything enqueued later, and a
            // later copy to the host drains the stream with it.
            HIPHandles.Queue queue = HIPHandles.resolve(commandQueuePtr, HIPHandles.Queue.class);
            HIPDriverAPI.hipCtxSetCurrent(queue.context());
            long result = HIPDriverAPI.hipGraphLaunch(graphExecHandle, queue.stream());
            if (result != 0) {
                throw new TornadoBailoutRuntimeException("hipGraphLaunch failed. hipError_t=" + result);
            }
            return;
        }
        long result = hipGraphLaunch(graphExecHandle, commandQueuePtr);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("hipGraphLaunch failed. hipError_t=" + result);
        }
        finish();
    }

    public void destroyGraph(long graphExecHandle) {
        hostCopyGraphs.remove(graphExecHandle);
        hipGraphExecDestroy(graphExecHandle);
    }

    public void flushEvents() {
        try {
            clFlush(commandQueuePtr);
        } catch (HIPException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long getContextId() {
        long result;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueuePtr, CL_QUEUE_CONTEXT.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long getDeviceId() {
        long result;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueuePtr, CL_QUEUE_DEVICE.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long getProperties() {
        return properties;
    }

    /**
     * Enqueues a barrier into the command queue of the specified device
     */
    public long enqueueBarrier() {
        return enqueueBarrier(null);
    }

    public long enqueueMarker() {
        return enqueueMarker(null);
    }

    public void cleanup() {
        try {
            clReleaseCommandQueue(commandQueuePtr);
        } catch (HIPException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long enqueueNDRangeKernel(HIPKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] waitEvents) {
        try {
            return clEnqueueNDRangeKernel(commandQueuePtr, kernel.getOclKernelID(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "null segment");
        try {
            return writeArrayToDevice(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "segment is null");
        try {
            return readArrayFromDeviceOffHeap(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void finish() {
        try {
            clFinish(commandQueuePtr);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(commandQueuePtr);
        } catch (HIPException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueBarrier(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueBarrier_HIPv1_1(waitEvents) : enqueueBarrier_HIPv1_2(waitEvents);
    }

    private int enqueueBarrier_HIPv1_1(long[] events) {
        try {
            if (events != null) {
                clEnqueueWaitForEvents(commandQueuePtr, events);
            }
        } catch (HIPException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return 0;
    }

    private long enqueueBarrier_HIPv1_2(long[] waitEvents) {
        try {
            return clEnqueueBarrierWithWaitList(commandQueuePtr, waitEvents);
        } catch (HIPException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueMarker(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_HIPv1_1(events);
    }

    private long enqueueMarker12(long[] waitEvents) {
        try {
            return clEnqueueMarkerWithWaitList(commandQueuePtr, waitEvents);
        } catch (HIPException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getOpenclVersion() {
        return openclVersion;
    }

    public long mapOnDeviceMemoryRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        long ptr;
        if (offset == 0) {
            ptr = NativeCommandQueue.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr);
        } else {
            // FIXME: PoC to check custom ranges from the source array
            final long headerSize = TornadoNativeArray.ARRAY_HEADER / JavaKind.Int.getByteCount(); // Header always contains integer values
            ptr = NativeCommandQueue.mapOnDeviceMemoryNDRegion(commandQueuePtr, destDevicePtr, srcDevicePtr, offset, sizeOfType, headerSize, sizeSource, sizeDest);
        }
        return ptr;
    }
}
