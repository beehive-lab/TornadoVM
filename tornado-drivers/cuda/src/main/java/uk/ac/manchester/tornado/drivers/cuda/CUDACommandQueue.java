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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueInfo.CL_QUEUE_CONTEXT;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueInfo.CL_QUEUE_DEVICE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.vm.ci.meta.JavaKind;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;
import uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.cuda.ffm.NVTXAPI;
import uk.ac.manchester.tornado.drivers.cuda.natives.NativeCommandQueue;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDACommandQueue extends CommandQueue {

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
     * by the per-plan join (see {@code CUDADeviceContext#joinRoleQueues}), which only needs to
     * synchronise queues that were actually touched since the last join.
     */
    private volatile boolean dirty;

    public CUDACommandQueue(long commandQueuePtr, long properties, int version) {
        this.commandQueuePtr = commandQueuePtr;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(CUDADriver.BYTE_ORDER);
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
     * Labels this queue's CUDA stream with its role (DEFAULT / H2D / COMPUTE / D2H) so the Nsight
     * Systems timeline shows named stream rows instead of raw stream ids.
     */
    public void nameStream(String name) {
        nvtxNameStream(commandQueuePtr, name);
    }

    static void clReleaseCommandQueue(long queueId) throws CUDAException {
        CUDAHandles.Queue queue = (CUDAHandles.Queue) CUDAHandles.release(queueId);
        if (queue != null) {
            CUDADriverAPI.cuStreamDestroy(queue.stream());
        }
    }

    /**
     * Labels a CUDA stream with a human-readable name (its role: DEFAULT / H2D / COMPUTE / D2H) so
     * the Nsight Systems timeline shows named stream rows instead of raw stream ids.
     */
    private static void nvtxNameStream(long queueId, String name) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue != null && name != null) {
            NVTXAPI.nameStream(queue.stream(), name);
        }
    }

    /**
     * Opens a host-side NVTX range labelled {@code name} (no-op without a profiler), so native
     * library tasks (cuBLAS, cuDNN, CUTLASS, cuTENSOR, ...) appear as named spans on the Nsight
     * Systems timeline next to the backend's own kernel and transfer ranges.
     */
    public static void nvtxRangePush(String name) {
        NVTXAPI.rangePush(name == null ? "library-task" : name);
    }

    /** Closes the most recently opened host-side NVTX range. */
    public static void nvtxRangePop() {
        NVTXAPI.rangePop();
    }

    /**
     * Whether per-operation device timing is wanted, i.e. the TornadoVM profiler is active. Pushed
     * from Java before each plan execution. When off, the START timestamp event is skipped entirely
     * (halving the events per operation) and the remaining events are created with
     * {@code CU_EVENT_DISABLE_TIMING}, which is cheaper to record. Events without a start report an
     * elapsed time of zero.
     */
    private static volatile boolean timingEnabled = true;

    /** CUresult for a call the backend rejects before it reaches the driver. */
    private static final int CUDA_ERROR_INVALID_VALUE = 1;

    /**
     * Size of {@code CUgraphExecUpdateResultInfo}: a 4-byte result enum padded to 8, then two node
     * pointers. The driver fills it in and only the returned CUresult is used, but it still has to
     * be given somewhere valid to write.
     */
    private static final int GRAPH_EXEC_UPDATE_RESULT_INFO_BYTES = 24;

    /** {@code CUevent_flags}. */
    private static final int CU_EVENT_DEFAULT = 0x0;
    private static final int CU_EVENT_DISABLE_TIMING = 0x2;

    /** Default block size when the caller supplies no local work size. */
    private static final int DEFAULT_BLOCK_SIZE = 256;

    static void nativeEnableTiming(boolean enabled) {
        timingEnabled = enabled;
    }

    private static int eventFlags() {
        return timingEnabled ? CU_EVENT_DEFAULT : CU_EVENT_DISABLE_TIMING;
    }

    static void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws CUDAException {
        Arrays.fill(buffer, (byte) 0);
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null || buffer.length < Long.BYTES) {
            return;
        }
        long value = 0;
        if (info == CL_QUEUE_CONTEXT.getValue()) {
            value = queue.context();
        } else if (info == CL_QUEUE_DEVICE.getValue()) {
            value = queue.device();
        }
        ByteBuffer.wrap(buffer).order(CUDADriver.BYTE_ORDER).putLong(value);
    }

    /**
     * Creates a {@code CUevent}, records it on the queue's stream and returns its handle. This is
     * the event result the OpenCL clone expects from an enqueue. No start event is recorded, so the
     * reported elapsed time is zero -- which is what markers and barriers want, since they do not
     * bracket a timed operation.
     */
    private static long recordEvent(CUDAHandles.Queue queue) throws CUDAException {
        long event = createEvent(eventFlags(), "cuEventCreate");
        int result = CUDADriverAPI.cuEventRecord(event, queue.stream());
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            CUDADriverAPI.cuEventDestroy(event);
            throw new CUDAException(CUDADriverAPI.describe("cuEventRecord", result));
        }
        return CUDAHandles.register(new CUDAHandles.Event(event, 0));
    }

    private static long createEvent(int flags, String call) throws CUDAException {
        MemorySegment event = FFMSupport.scratchPointer();
        int result = CUDADriverAPI.cuEventCreate(event, flags);
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            throw new CUDAException(CUDADriverAPI.describe(call, result));
        }
        return event.get(FFMSupport.C_POINTER, 0).address();
    }

    /**
     * Records a START timestamp on the stream when the profiler is active, to be paired with
     * {@link #endEvent} after the operation so that {@code cuEventElapsedTime(start, end)} yields
     * its device time. Returns {@code 0} when timing is off.
     */
    private static long beginEvent(CUDAHandles.Queue queue) throws CUDAException {
        if (!timingEnabled) {
            return 0;
        }
        long start = createEvent(CU_EVENT_DEFAULT, "cuEventCreate(start)");
        int result = CUDADriverAPI.cuEventRecord(start, queue.stream());
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            CUDADriverAPI.cuEventDestroy(start);
            throw new CUDAException(CUDADriverAPI.describe("cuEventRecord(start)", result));
        }
        return start;
    }

    /**
     * Records the completion event for an operation opened with {@link #beginEvent} and returns the
     * handle carrying both. This event doubles as the operation's dependency handle, for
     * {@code cuStreamWaitEvent} and status queries, so it is always created.
     */
    private static long endEvent(long start, CUDAHandles.Queue queue) throws CUDAException {
        long end;
        try {
            end = createEvent(eventFlags(), "cuEventCreate(end)");
        } catch (CUDAException e) {
            // The start event recorded by beginEvent has to go back to the driver too.
            destroyEvent(start);
            throw e;
        }
        int result = CUDADriverAPI.cuEventRecord(end, queue.stream());
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            destroyEvent(start);
            CUDADriverAPI.cuEventDestroy(end);
            throw new CUDAException(CUDADriverAPI.describe("cuEventRecord(end)", result));
        }
        return CUDAHandles.register(new CUDAHandles.Event(end, start));
    }

    private static void destroyEvent(long event) {
        if (event != 0) {
            CUDADriverAPI.cuEventDestroy(event);
        }
    }

    /**
     * Releases an event handle whose value is about to be dropped, so its CUevents go back to the
     * driver rather than leaking when the caller unwinds instead of returning it.
     */
    private static void discardEvent(long handle) {
        CUDAHandles.Event event = (CUDAHandles.Event) CUDAHandles.release(handle);
        if (event != null) {
            destroyEvent(event.start());
            destroyEvent(event.event());
        }
    }

    /**
     * Makes the queue's stream wait, GPU-side, on each event of the wait list, which is laid out as
     * {@code [count, e0, e1, ...]}. CUevents are valid across streams, which is what allows
     * cross-queue ordering when a plan runs with intra-plan concurrency (one queue per role).
     */
    private static void waitEvents(CUDAHandles.Queue queue, long[] events) {
        if (events == null || events.length == 0) {
            return;
        }
        int count = (int) Math.min(events[0], events.length - 1L);
        for (int i = 0; i < count; i++) {
            CUDAHandles.Event event = CUDAHandles.resolve(events[i + 1], CUDAHandles.Event.class);
            if (event != null) {
                CUDADriverAPI.cuStreamWaitEvent(queue.stream(), event.event(), 0);
            }
        }
    }

    /**
     * Whether the queue's stream is currently capturing into a CUDA graph. During capture the
     * stream must not be synchronised -- that invalidates the capture -- so the transfer helpers
     * skip their post-copy synchronise while it is.
     */
    private static boolean isCapturing(CUDAHandles.Queue queue) {
        MemorySegment status = FFMSupport.scratchInt();
        if (CUDADriverAPI.cuStreamIsCapturing(queue.stream(), status) != CUDADriverAPI.CUDA_SUCCESS) {
            return false;
        }
        return status.get(FFMSupport.C_INT, 0) == CUDADriverAPI.CU_STREAM_CAPTURE_STATUS_ACTIVE;
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
     * Dispatch a CUDA kernel, translating an OpenCL NDRange into a grid/block launch.
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
     * @throws CUDAException
     *     if the launch fails
     */
    static long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        CUDAHandles.Kernel kernel = CUDAHandles.resolve(kernelId, CUDAHandles.Kernel.class);
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
        // previous kernel's name), so the NVTX row is the reliable source.
        NVTXAPI.rangePush(kernel.name);
        try (Arena arena = Arena.ofConfined()) {
            CUDADriverAPI.cuCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            int result = CUDADriverAPI.cuLaunchKernel(kernel.function, grid[0], grid[1], grid[2], block[0], block[1], block[2], 0, queue.stream(), kernelParameters(arena, kernel),
                    MemorySegment.NULL);
            long launchEvent = endEvent(start, queue);
            // A failed launch leaves the kernel's outputs untouched. Surfacing it makes the caller
            // bail out instead of returning stale buffers as a valid result.
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                discardEvent(launchEvent);
                throw new CUDAException(CUDADriverAPI.describe("cuLaunchKernel", result));
            }
            return launchEvent;
        } finally {
            NVTXAPI.rangePop();
        }
    }

    /** Builds the {@code kernelParams} vector of pointers into copies of the staged argument blobs. */
    private static MemorySegment kernelParameters(Arena arena, CUDAHandles.Kernel kernel) {
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
     *     events and the end-of-plan sync. While capturing into a CUDA graph the stream must not be
     *     synchronised, so the sync is skipped: the copy becomes a graph node whose host pointer, a
     *     stable off-heap segment, is re-read on each graph launch.
     */
    private static long transferToDevice(CUDAHandles.Queue queue, long hostPointer, long hostOffset, long deviceOffset, long numBytes, long devicePointer, long[] events, boolean syncAfter)
            throws CUDAException {
        NVTXAPI.rangePush(transferLabel(WRITE_LABELS, "H2D", numBytes));
        try {
            CUDADriverAPI.cuCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            int result = CUDADriverAPI.cuMemcpyHtoDAsync(devicePointer + deviceOffset, hostPointer + hostOffset, numBytes, queue.stream());
            result = syncIfNeeded(queue, result, syncAfter);
            long event = endEvent(start, queue);
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                discardEvent(event);
                throw new CUDAException(CUDADriverAPI.describe("cuMemcpyHtoDAsync", result));
            }
            return event;
        } finally {
            NVTXAPI.rangePop();
        }
    }

    /** Device-to-host counterpart of {@link #transferToDevice}. */
    private static long transferToHost(CUDAHandles.Queue queue, long hostPointer, long hostOffset, long deviceOffset, long numBytes, long devicePointer, long[] events, boolean syncAfter)
            throws CUDAException {
        NVTXAPI.rangePush(transferLabel(READ_LABELS, "D2H", numBytes));
        try {
            CUDADriverAPI.cuCtxSetCurrent(queue.context());
            waitEvents(queue, events);
            long start = beginEvent(queue);
            int result = CUDADriverAPI.cuMemcpyDtoHAsync(hostPointer + hostOffset, devicePointer + deviceOffset, numBytes, queue.stream());
            result = syncIfNeeded(queue, result, syncAfter);
            long event = endEvent(start, queue);
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                discardEvent(event);
                throw new CUDAException(CUDADriverAPI.describe("cuMemcpyDtoHAsync", result));
            }
            return event;
        } finally {
            NVTXAPI.rangePop();
        }
    }

    /** Returns the first failure of the copy and the synchronise that follows it, if any. */
    private static int syncIfNeeded(CUDAHandles.Queue queue, int copyResult, boolean syncAfter) {
        if (!syncAfter || isCapturing(queue)) {
            return copyResult;
        }
        int sync = CUDADriverAPI.cuStreamSynchronize(queue.stream());
        return copyResult != CUDADriverAPI.CUDA_SUCCESS ? copyResult : sync;
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
    private static final ThreadLocal<Staging> STAGING = ThreadLocal.withInitial(Staging::new);

    private static final class Staging {

        private Arena arena;
        private MemorySegment segment;

        MemorySegment forBytes(long numBytes) {
            if (segment == null || segment.byteSize() < numBytes) {
                if (arena != null) {
                    arena.close();
                }
                arena = Arena.ofConfined();
                segment = arena.allocate(numBytes, Long.BYTES);
            }
            return segment;
        }
    }

    private static long writeArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        MemorySegment staging = STAGING.get().forBytes(bytes);
        MemorySegment.copy(array, elementOffset, staging, layout, 0, elementCount);
        return transferToDevice(queue, staging.address(), 0, offset, bytes, ptr, events, true);
    }

    private static long readArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        MemorySegment staging = STAGING.get().forBytes(bytes);
        long event = transferToHost(queue, staging.address(), 0, offset, bytes, ptr, events, true);
        MemorySegment.copy(staging, layout, 0, array, elementOffset, elementCount);
        return event;
    }

    static long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return transferToDevice(queue, hostPointer, hostOffset, offset, bytes, ptr, events, blocking);
    }

    static long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        return readArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDeviceOffHeap(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return transferToHost(queue, hostPointer, hostOffset, offset, bytes, ptr, events, blocking);
    }

    static void clEnqueueWaitForEvents(long queueId, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue != null) {
            waitEvents(queue, events);
        }
    }

    /** Allocates page-locked (pinned) host memory for the staged-transfer ring; 0 on failure. */
    static long cuMemAllocHost(long numBytes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hostPointer = FFMSupport.allocatePointer(arena);
            if (CUDADriverAPI.cuMemAllocHost(hostPointer, numBytes) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0L;
            }
            return hostPointer.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /** Releases pinned host memory allocated by {@link #cuMemAllocHost(long)}. */
    static void cuMemFreeHost(long hostPtr) {
        CUDADriverAPI.cuMemFreeHost(hostPtr);
    }

    /**
     * Plain host-side copy between raw pointers. The staged-transfer path uses it to fill a pinned
     * staging slot from the pageable source segment on the CPU while a previous slot's async DMA is
     * still in flight.
     */
    static void memcpyHostToHost(long dstPtr, long srcPtr, long numBytes) {
        MemorySegment.copy(FFMSupport.asSegment(srcPtr, numBytes), 0, FFMSupport.asSegment(dstPtr, numBytes), 0, numBytes);
    }

    static long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        return recordEvent(queue);
    }

    static long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        // Honour the wait list, then place a marker event.
        waitEvents(queue, events);
        return recordEvent(queue);
    }

    static void clFlush(long queueId) throws CUDAException {
        synchronizeStream(queueId);
    }

    static void clFinish(long queueId) throws CUDAException {
        synchronizeStream(queueId);
    }

    private static void synchronizeStream(long queueId) throws CUDAException {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return;
        }
        int result = CUDADriverAPI.cuStreamSynchronize(queue.stream());
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            throw new CUDAException(CUDADriverAPI.describe("cuStreamSynchronize", result));
        }
    }

    /* ---- CUDA Graph (stream capture) ---- */

    /**
     * Puts the queue's stream into capture mode; {@code mode} maps directly onto
     * {@code CUstreamCaptureMode}. Returns the {@code CUresult}.
     */
    private static long cuStreamBeginCapture(long queueId, int mode) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return CUDA_ERROR_INVALID_VALUE;
        }
        CUDADriverAPI.cuCtxSetCurrent(queue.context());
        return CUDADriverAPI.cuStreamBeginCapture(queue.stream(), mode);
    }

    /** Ends capture and returns the constructed {@code CUgraph} as a raw pointer (0 on failure). */
    private static long cuStreamEndCapture(long queueId) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (queue == null) {
            return 0;
        }
        CUDADriverAPI.cuCtxSetCurrent(queue.context());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment graph = FFMSupport.allocatePointer(arena);
            if (CUDADriverAPI.cuStreamEndCapture(queue.stream(), graph) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            return graph.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    private static boolean cuStreamIsCapturing(long queueId) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        return queue != null && isCapturing(queue);
    }

    /** Instantiates an executable graph and returns the {@code CUgraphExec} (0 on failure). */
    private static long cuGraphInstantiate(long graphHandle) {
        if (graphHandle == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment graphExec = FFMSupport.allocatePointer(arena);
            if (CUDADriverAPI.cuGraphInstantiateWithFlags(graphExec, graphHandle, 0) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            return graphExec.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /**
     * Attempts to update an instantiated graph in place with the topology of a new {@code CUgraph}.
     * Returns {@code CUDA_SUCCESS} when the update succeeded, otherwise the error code so the
     * caller can fall back to re-instantiation.
     */
    private static long cuGraphExecUpdate(long graphExecHandle, long graphHandle) {
        if (graphExecHandle == 0 || graphHandle == 0) {
            return CUDA_ERROR_INVALID_VALUE;
        }
        try (Arena arena = Arena.ofConfined()) {
            // CUgraphExecUpdateResultInfo: the driver fills it in, and only the CUresult is used.
            MemorySegment info = arena.allocate(GRAPH_EXEC_UPDATE_RESULT_INFO_BYTES, Long.BYTES);
            info.fill((byte) 0);
            return CUDADriverAPI.cuGraphExecUpdate(graphExecHandle, graphHandle, info);
        }
    }

    /** Launches an instantiated graph on the queue's stream. */
    private static long cuGraphLaunch(long graphExecHandle, long queueId) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        if (graphExecHandle == 0 || queue == null) {
            return CUDA_ERROR_INVALID_VALUE;
        }
        CUDADriverAPI.cuCtxSetCurrent(queue.context());
        return CUDADriverAPI.cuGraphLaunch(graphExecHandle, queue.stream());
    }

    private static long cuGraphExecDestroy(long graphExecHandle) {
        return graphExecHandle == 0 ? CUDADriverAPI.CUDA_SUCCESS : CUDADriverAPI.cuGraphExecDestroy(graphExecHandle);
    }

    private static long cuGraphDestroy(long graphHandle) {
        return graphHandle == 0 ? CUDADriverAPI.CUDA_SUCCESS : CUDADriverAPI.cuGraphDestroy(graphHandle);
    }

    /* ---- Native interop (external libraries, e.g. cuBLAS) ---- */

    private static long getStreamPointer(long queueId) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        return queue == null ? 0 : queue.stream();
    }

    private static long getContextPointer(long queueId) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(queueId, CUDAHandles.Queue.class);
        return queue == null ? 0 : queue.context();
    }

    /**
     * Raw CUstream handle of this queue, for handing to external native
     * libraries (e.g., {@code cublasSetStream}) so their work is ordered with
     * TornadoVM kernels and transfers on the same stream.
     */
    public long getNativeStream() {
        return getStreamPointer(commandQueuePtr);
    }

    /**
     * Raw CUcontext handle of this queue.
     */
    public long getNativeContext() {
        return getContextPointer(commandQueuePtr);
    }

    /** CU_STREAM_CAPTURE_MODE_GLOBAL. */
    private static final int CU_STREAM_CAPTURE_MODE_GLOBAL = 0;

    /** True while this queue's stream is recording operations into a CUDA graph. */
    private boolean capturing = false;

    /**
     * Begins recording all subsequent operations submitted to this queue's
     * stream into a CUDA graph.
     */
    public void beginGraphCapture() {
        long result = cuStreamBeginCapture(commandQueuePtr, CU_STREAM_CAPTURE_MODE_GLOBAL);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("cuStreamBeginCapture failed. CUresult=" + result);
        }
        capturing = true;
    }

    /**
     * Ends capture and instantiates the recorded graph. The source CUgraph is
     * destroyed after instantiation; the returned handle is the CUgraphExec.
     *
     * @return opaque CUgraphExec handle
     */
    public long endGraphCaptureAndInstantiate() {
        capturing = false;
        long graphHandle = cuStreamEndCapture(commandQueuePtr);
        if (graphHandle == 0) {
            throw new TornadoBailoutRuntimeException("cuStreamEndCapture returned a null graph");
        }
        long graphExecHandle = cuGraphInstantiate(graphHandle);
        cuGraphDestroy(graphHandle);
        if (graphExecHandle == 0) {
            throw new TornadoBailoutRuntimeException("cuGraphInstantiate failed");
        }
        return graphExecHandle;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Launches a previously instantiated graph on this queue's stream and
     * blocks until completion so that captured device-to-host copies are
     * visible to the host once this call returns.
     */
    public void launchGraph(long graphExecHandle) {
        long result = cuGraphLaunch(graphExecHandle, commandQueuePtr);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("cuGraphLaunch failed. CUresult=" + result);
        }
        finish();
    }

    public void destroyGraph(long graphExecHandle) {
        cuGraphExecDestroy(graphExecHandle);
    }

    public void flushEvents() {
        try {
            clFlush(commandQueuePtr);
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long enqueueNDRangeKernel(CUDAKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] waitEvents) {
        try {
            return clEnqueueNDRangeKernel(commandQueuePtr, kernel.getOclKernelID(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "null segment");
        try {
            return writeArrayToDevice(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "segment is null");
        try {
            return readArrayFromDeviceOffHeap(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void finish() {
        try {
            clFinish(commandQueuePtr);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(commandQueuePtr);
        } catch (CUDAException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueBarrier(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueBarrier_CUDAv1_1(waitEvents) : enqueueBarrier_CUDAv1_2(waitEvents);
    }

    private int enqueueBarrier_CUDAv1_1(long[] events) {
        try {
            if (events != null) {
                clEnqueueWaitForEvents(commandQueuePtr, events);
            }
        } catch (CUDAException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return 0;
    }

    private long enqueueBarrier_CUDAv1_2(long[] waitEvents) {
        try {
            return clEnqueueBarrierWithWaitList(commandQueuePtr, waitEvents);
        } catch (CUDAException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueMarker(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_CUDAv1_1(events);
    }

    private long enqueueMarker12(long[] waitEvents) {
        try {
            return clEnqueueMarkerWithWaitList(commandQueuePtr, waitEvents);
        } catch (CUDAException e) {
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
