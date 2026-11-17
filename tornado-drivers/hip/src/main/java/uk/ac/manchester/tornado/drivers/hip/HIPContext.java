/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoNoOpenCLPlatformException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandQueueProperties;
import uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.hip.mm.HIPPinnedMemoryRegistry;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class HIPContext implements HIPContextInterface {

    /** One context handle per device, indexed like {@link #devices}; {@code 0} until first use. */
    private final long[] contextIDs;
    private final List<HIPTargetDevice> devices;
    private final List<HIPDeviceContext> deviceContexts;

    private final HIPPlatform platform;

    private final TornadoLogger logger;

    /** Refcounted bookkeeping for user host memory pinned via {@code hipHostRegister}. */
    private final HIPPinnedMemoryRegistry pinnedMemoryRegistry;

    public HIPContext(HIPPlatform platform, List<HIPTargetDevice> devices) {
        this.platform = platform;
        this.contextIDs = new long[devices.size()];
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.logger = new TornadoLogger(this.getClass());
        this.pinnedMemoryRegistry = new HIPPinnedMemoryRegistry(this);
    }

    public HIPPinnedMemoryRegistry getPinnedMemoryRegistry() {
        return pinnedMemoryRegistry;
    }

    /** {@code hipStream_t flags}: the stream does not synchronise with the NULL stream. */
    private static final int HIP_STREAM_NON_BLOCKING = 0x1;
    /** {@code hipHostRegisterPortable}: keep the pin valid across every HIP context. */
    private static final int HIP_HOST_REGISTER_PORTABLE = 0x01;
    private static final int HIP_ERROR_INVALID_VALUE = 1;
    private static final int HIP_ERROR_INVALID_CONTEXT = 201;

    /**
     * The arena backing each off-heap host allocation, keyed by its address, so that
     * {@link #freeOffHeapMemory(long)} can release exactly the region it was handed.
     */
    private static final Map<Long, Arena> OFF_HEAP_ARENAS = new ConcurrentHashMap<>();

    void clReleaseContext(long id) throws HIPException {
        HIPHandles.Context context = (HIPHandles.Context) HIPHandles.release(id);
        if (context == null) {
            return;
        }
        HIPDriverAPI.hipDevicePrimaryCtxRelease(context.device());
    }

    /**
     * Returns the handle of the context of the device at {@code deviceIndex}. The device's primary
     * context is retained on first use, so GPUs a program never runs on are left untouched.
     */
    @Override
    public synchronized long getContextId(int deviceIndex) {
        if (contextIDs[deviceIndex] == 0) {
            try {
                contextIDs[deviceIndex] = HIPPlatform.retainPrimaryContext(devices.get(deviceIndex).getDevicePointer());
            } catch (HIPException e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
        }
        return contextIDs[deviceIndex];
    }

    /**
     * Makes the context of the device at {@code deviceIndex} current on the calling thread. Driver
     * calls that take no stream (allocation, event creation, the HIP runtime under the native
     * libraries) act on the current context, so they land on the right GPU only after this.
     */
    public void makeCurrent(int deviceIndex) {
        HIPHandles.Context context = HIPHandles.resolve(getContextId(deviceIndex), HIPHandles.Context.class);
        if (context != null) {
            HIPDriverAPI.hipCtxSetCurrent(context.context());
        }
    }

    /**
     * Runs the transfer warm-up of a device's context on a throwaway stream, so the first execution
     * on that device does not pay for the driver's lazy set-up of its first copies.
     */
    public void warmUp(int deviceIndex) {
        try {
            long queue = clCreateCommandQueue(getContextId(deviceIndex), devices.get(deviceIndex).getDevicePointer(), getProperties());
            HIPCommandQueue.clReleaseCommandQueue(queue);
        } catch (HIPException e) {
            // Only an optimisation: the first real queue on the device warms the path again.
            logger.debug("warm-up of device %d skipped: %s", deviceIndex, e.getMessage());
        }
    }

    /**
     * Waits for the outstanding work of every device that has a context. The lock is not held while
     * waiting: the devices may be running work that other threads have to join first.
     */
    private void synchronizeAll() {
        long[] ids;
        synchronized (this) {
            ids = contextIDs.clone();
        }
        for (long id : ids) {
            HIPHandles.Context context = id == 0 ? null : HIPHandles.resolve(id, HIPHandles.Context.class);
            if (context != null) {
                HIPDriverAPI.hipCtxSetCurrent(context.context());
                HIPDriverAPI.hipCtxSynchronize();
            }
        }
    }

    /** Any context already in use; host-memory registration is portable, so which one does not matter. */
    private synchronized long anyContextId() {
        for (long id : contextIDs) {
            if (id != 0) {
                return id;
            }
        }
        return getContextId(0);
    }

    void clGetContextInfo(long id, int info, byte[] buffer) throws HIPException {
        // Context info is only used for debugging logs on the Java side; zero-fill.
        Arrays.fill(buffer, (byte) 0);
    }

    /** Maps to a {@code hipStream_t} pinned to the device's context. */
    public long clCreateCommandQueue(long contextId, long deviceId, long properties) throws HIPException {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        if (context == null) {
            return 0;
        }
        int result = HIPDriverAPI.hipCtxSetCurrent(context.context());
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            throw new HIPException(HIPDriverAPI.describe("hipCtxSetCurrent", result));
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stream = FFMSupport.allocatePointer(arena);
            result = HIPDriverAPI.hipStreamCreateWithFlags(stream, HIP_STREAM_NON_BLOCKING);
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                throw new HIPException(HIPDriverAPI.describe("hipStreamCreateWithFlags", result));
            }
            long streamPointer = stream.get(FFMSupport.C_POINTER, 0).address();
            long handle = HIPHandles.register(new HIPHandles.Queue(streamPointer, context.context(), context.device(), properties));
            HIPCommandQueue.warmUpIssuePath(handle);
            return handle;
        }
    }

    /**
     * Allocates zeroed, aligned off-heap host memory. The arena is remembered so the region can be
     * released again; it is shared rather than confined because the address outlives this call and
     * is read and written from whichever thread the runtime later stages a transfer on.
     */
    public long allocateOffHeapMemory(long size, long alignment) {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment segment = arena.allocate(size, alignment);
            long address = segment.address();
            OFF_HEAP_ARENAS.put(address, arena);
            return address;
        } catch (RuntimeException e) {
            arena.close();
            System.out.printf("HIP off-heap memory allocation of %d bytes failed: %s%n", size, e.getMessage());
            return 0;
        }
    }

    public void freeOffHeapMemory(long address) {
        Arena arena = OFF_HEAP_ARENAS.remove(address);
        if (arena != null) {
            arena.close();
        }
    }

    public ByteBuffer asByteBuffer(long address, long size) {
        return FFMSupport.asSegment(address, size).asByteBuffer();
    }

    /**
     * Allocates device memory. The returned {@code hipDeviceptr_t} is the "buffer" the Java side
     * stores and later passes to read/write/launch.
     */
    HIPBufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws HIPException {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        if (context == null) {
            return new HIPBufferResult(0L, hostPointer, HIP_ERROR_INVALID_CONTEXT);
        }
        HIPDriverAPI.hipCtxSetCurrent(context.context());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment devicePointer = FFMSupport.allocateLong(arena);
            // hipMalloc's status is returned rather than thrown: the caller distinguishes
            // out-of-memory from other failures and raises TornadoOutOfMemoryException itself.
            int result = HIPDriverAPI.hipMalloc(devicePointer, size);
            return new HIPBufferResult(devicePointer.get(FFMSupport.C_LONG, 0), hostPointer, result);
        }
    }

    /**
     * Zero-initialises a device buffer. {@code hipMalloc} returns uninitialised device memory and
     * TornadoVM reuses pooled device buffers across executions, so a write-only output the kernel
     * never writes (an early-returning kernel, say) would otherwise read back stale data.
     *
     * <p>{@code hipMemsetD8} on device memory is asynchronous with respect to the host, and it runs
     * on the legacy default stream, which TornadoVM's streams -- created
     * {@code hipStreamNonBlocking} -- are not ordered against. Without waiting for it, the
     * zeroing can land after the plan's own stream has written the buffer (a library call or a
     * kernel issued right after allocation), wiping what it wrote. So the legacy stream is
     * synchronised here: the zeroing is complete before the buffer is handed out. This is on the
     * allocation path only, not per execution.
     */
    int memSetZero(long contextId, long devicePointer, long bytes) {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        if (context == null || devicePointer == 0 || bytes <= 0) {
            return HIP_ERROR_INVALID_VALUE;
        }
        HIPDriverAPI.hipCtxSetCurrent(context.context());
        int status = HIPDriverAPI.hipMemsetD8(devicePointer, (byte) 0, bytes);
        if (status != 0) {
            return status;
        }
        return HIPDriverAPI.hipStreamSynchronize(0L);
    }

    /** HIP has no sub-buffer concept; the parent buffer stands in for one. */
    long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws HIPException {
        return buffer;
    }

    void clReleaseMemObject(long contextId, long memId) throws HIPException {
        if (memId == 0) {
            return;
        }
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        if (context != null) {
            HIPDriverAPI.hipCtxSetCurrent(context.context());
        }
        // Transfers are asynchronous: drain outstanding work before releasing device memory,
        // otherwise the free races an in-flight copy or kernel that still uses this allocation.
        // Frees only happen on eviction and teardown, so the heavy context synchronise is fine here.
        HIPDriverAPI.hipCtxSynchronize();
        HIPDriverAPI.hipFree(memId);
    }

    /**
     * Stashes the generated HIP C++ source; hipRTC compilation happens later in
     * {@code clBuildProgram}, mirroring the OpenCL two-step create-then-build.
     */
    long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws HIPException {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        String source = new String(data, StandardCharsets.UTF_8);
        return HIPHandles.register(new HIPHandles.Program(context == null ? 0 : context.context(), source, null));
    }

    /** Accepts a pre-compiled module image (a code object) as the "binary" and skips hipRTC. */
    long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws HIPException {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        HIPHandles.Program program = new HIPHandles.Program(context == null ? 0 : context.context(), "", data);
        program.buildStatus = HIPHandles.Program.BUILD_SUCCESS; // ready to load; clBuildProgram loads the module
        return HIPHandles.register(program);
    }

    /**
     * HIP has no SPIR-V ingestion path; {@code -1} makes the Java side raise
     * TornadoNoOpenCLPlatformException, matching the OpenCL &lt; 2.1 behaviour.
     */
    long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws HIPException {
        return -1;
    }

    /**
     * Registers an existing off-heap host buffer as pinned (page-locked) memory. After
     * registration, the async copies DMA directly to and from this address instead of going through
     * HIP's internal staging copy, which is what allows true host-device overlap without blocking
     * the calling thread.
     */
    private static int hipHostRegister(long contextId, long hostPointer, long numBytes) {
        HIPHandles.Context context = HIPHandles.resolve(contextId, HIPHandles.Context.class);
        if (context == null) {
            return -1;
        }
        HIPDriverAPI.hipCtxSetCurrent(context.context());
        return HIPDriverAPI.hipHostRegister(hostPointer, numBytes, HIP_HOST_REGISTER_PORTABLE);
    }

    /**
     * Unregisters host memory previously registered with {@link #hipHostRegister}. Every context
     * is synchronised first so no async DMA can still be reading or writing the region when the pin
     * is dropped: unregistering under an in-flight copy is the data hazard, since the transfer would
     * keep using the page-locked mapping while the driver tears it down. The registration is
     * portable, so a copy on any device may be using it.
     */
    private int hipHostUnregister(long hostPointer) {
        synchronizeAll();
        return HIPDriverAPI.hipHostUnregister(hostPointer);
    }

    /** Raw {@code hipError_t} for "this host range is already page-locked" (see registry policy). */
    public static final int HIP_ERROR_HOST_MEMORY_ALREADY_REGISTERED = 712;

    /**
     * Registers an off-heap host region as pinned (page-locked) so async transfers DMA
     * directly (no driver staging copy, true transfer/compute overlap).
     *
     * @return the raw {@code hipError_t}: {@code 0} on success,
     *     {@link #HIP_ERROR_HOST_MEMORY_ALREADY_REGISTERED} when another registration
     *     already covers this address (memory is pinned, but not owned by the caller).
     */
    public int registerPinnedMemory(long hostPointer, long numBytes) {
        return hipHostRegister(anyContextId(), hostPointer, numBytes);
    }

    /**
     * Unregisters a previously pinned host region. Every context is synchronised
     * first, so no in-flight async DMA can still touch the region when the pin is
     * dropped.
     */
    public int unregisterPinnedMemory(long hostPointer) {
        return hipHostUnregister(hostPointer);
    }

    /** Unregisters host memory away from the threads that run plans (see {@link #unregisterPinnedMemoryLater}). */
    private final java.util.concurrent.ExecutorService unpinner = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tornado-hip-unpin");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Unregisters host memory on a thread of its own, then runs {@code done}. HIP synchronises
     * every device when host memory is unregistered, so a thread that runs plans can block there on
     * work only it can unblock, such as an NCCL collective on another GPU waiting for this thread's
     * next collective. The unpinning thread may wait as long as it takes.
     */
    public void unregisterPinnedMemoryLater(long hostPointer, Runnable done) {
        unpinner.execute(() -> {
            try {
                hipHostUnregister(hostPointer);
            } finally {
                done.run();
            }
        });
    }

    /** Pinned host buffers of freed kernel stack frames, kept for the next frame; all of {@link #pinnedFrameBytes}. */
    private final java.util.ArrayDeque<Long> pinnedFrames = new java.util.ArrayDeque<>();

    private long pinnedFrameBytes;

    /**
     * A pinned host buffer of {@code bytes} for a kernel stack frame: one a freed frame left behind,
     * or a new one, allocated and registered. Frame buffers stay pinned until {@link #cleanup}:
     * HIP synchronises every device when host memory is unregistered, so unregistering while a
     * plan of another thread runs can deadlock against work only this thread can unblock, such as
     * an NCCL collective on another GPU waiting for this rank's next collective.
     *
     * @return the buffer, or 0 when no off-heap memory could be allocated
     */
    public long acquirePinnedFrame(long bytes, long alignment) {
        synchronized (pinnedFrames) {
            if (bytes == pinnedFrameBytes && !pinnedFrames.isEmpty()) {
                return pinnedFrames.pop();
            }
        }
        long hostPointer = allocateOffHeapMemory(bytes, alignment);
        if (hostPointer != 0) {
            registerPinnedMemory(hostPointer, bytes);
            synchronized (pinnedFrames) {
                if (pinnedFrames.isEmpty()) {
                    pinnedFrameBytes = bytes;
                }
            }
        }
        return hostPointer;
    }

    /**
     * Returns the buffer of a freed kernel stack frame of the device at {@code deviceIndex} for reuse,
     * once that device has no copy from it in flight. Only that device's context is synchronised,
     * and no lock is held while waiting: the device may be running a collective that another
     * thread has to join first.
     */
    public void releasePinnedFrame(long hostPointer, long bytes, int deviceIndex) {
        makeCurrent(deviceIndex);
        HIPDriverAPI.hipCtxSynchronize();
        synchronized (pinnedFrames) {
            if (bytes == pinnedFrameBytes) {
                pinnedFrames.push(hostPointer);
                return;
            }
        }
        unregisterPinnedMemoryLater(hostPointer, () -> freeOffHeapMemory(hostPointer));
    }

    public int getNumDevices() {
        return devices.size();
    }

    public List<HIPTargetDevice> devices() {
        return devices;
    }

    private void createCommandQueue(int index, long properties) {
        HIPTargetDevice device = devices.get(index);
        try {

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;

            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            clCreateCommandQueue(getContextId(index), device.getDevicePointer(), properties);
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException("[ERROR] HIPDriver Command Queue Initialization not valid");
        }
    }

    public long getProperties() {
        long properties = 0;
        if (TornadoOptions.ENABLE_OPENCL_PROFILING) {
            properties |= HIPCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
        }

        if (TornadoOptions.ENABLE_OOO_EXECUTION) {
            properties |= HIPCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        return properties;
    }

    @Override
    public void createCommandQueue(int index) {
        long properties = getProperties();
        createCommandQueue(index, properties);
    }

    public HIPProgram createProgramWithSource(byte[] source, long[] lengths, HIPDeviceContext deviceContext) {
        HIPProgram program = null;

        try {
            program = new HIPProgram(clCreateProgramWithSource(getContextId(deviceContext.getDeviceIndex()), source, lengths), deviceContext);
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public HIPProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, HIPDeviceContext deviceContext) {
        HIPProgram program;
        try {
            long programID = clCreateProgramWithIL(getContextId(deviceContext.getDeviceIndex()), spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoNoOpenCLPlatformException("HIPDriver version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new HIPProgram(programID, deviceContext);
        } catch (HIPException e) {
            throw new TornadoRuntimeException(e);
        }

        return program;
    }

    public HIPProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, HIPDeviceContext deviceContext) {
        HIPProgram program = null;

        try {
            program = new HIPProgram(clCreateProgramWithBinary(getContextId(deviceContext.getDeviceIndex()), deviceId, binary, lengths), deviceContext);
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {
        unpinner.shutdown();
        try {
            if (!unpinner.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("host memory still being unregistered at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (pinnedFrames) {
            // pooled frames exist only once a context did
            if (!pinnedFrames.isEmpty()) {
                for (long hostPointer : pinnedFrames) {
                    hipHostUnregister(hostPointer);
                    freeOffHeapMemory(hostPointer);
                }
            }
            pinnedFrames.clear();
        }

        if (TornadoOptions.DUMP_EVENTS) {
            for (HIPDeviceContext deviceContext : deviceContexts) {
                deviceContext.dumpEvents();
            }
        }

        try {
            long t1 = System.nanoTime();
            synchronized (this) {
                for (int i = 0; i < contextIDs.length; i++) {
                    if (contextIDs[i] != 0) {
                        clReleaseContext(contextIDs[i]);
                        contextIDs[i] = 0;
                    }
                }
            }
            long t2 = System.nanoTime();

            if (TornadoOptions.FULL_DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s%n", "context", (t2 - t1) * 1e-9);
            }
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("platform=%d, device count=%d", getPlatformIndex(), getNumDevices());
    }

    @Override
    public HIPDeviceContext createDeviceContext(int index) {
        logger.debug("creating device context for device: %s", devices.get(index).toString());
        final HIPDeviceContext deviceContext = new HIPDeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    public HIPBufferResult createBuffer(int deviceIndex, long flags, long bytes) {
        return createBuffer(deviceIndex, flags, bytes, 0L);
    }

    private HIPBufferResult createBuffer(int deviceIndex, long flags, long bytes, long hostPointer) {
        try {
            final HIPBufferResult result = createBuffer(getContextId(deviceIndex), flags, bytes, hostPointer);
            // hipMalloc reports failures (notably HIP_ERROR_OUT_OF_MEMORY) via the
            // result status and a null device pointer rather than throwing. Surface it
            // as a clean exception here: otherwise the zero buffer is used by a later
            // copy/kernel launch and triggers an unrecoverable HIP_ERROR_ILLEGAL_ADDRESS
            // that poisons the whole context.
            if (result == null || result.getResult() != HIPDriver.HIP_SUCCESS || result.getBuffer() == 0L) {
                int status = (result == null) ? -1 : result.getResult();
                throw new TornadoOutOfMemoryException("[ERROR] Unable to allocate " + RuntimeUtilities.humanReadableByteCount(bytes, false) + " on the HIP device (hipMalloc status=" + status
                        + ").\n\tThe allocation exceeds available device memory. Reduce the working set, or enable HIP managed memory to over-subscribe VRAM.");
            }
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(int deviceIndex, long bufferId) {
        try {
            clReleaseMemObject(getContextId(deviceIndex), bufferId);
            logger.info("buffer released 0x%x", bufferId);
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Zero-initialise a device buffer. Used to guarantee that (re)allocated
     * device buffers do not expose stale/garbage data when a kernel does not
     * write all (or any) of an output buffer.
     */
    public void zeroBuffer(int deviceIndex, long bufferId, long bytes) {
        if (bufferId == 0 || bytes <= 0) {
            return;
        }
        memSetZero(getContextId(deviceIndex), bufferId, bytes);
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public HIPPlatform getPlatform() {
        return platform;
    }

    public static class HIPBufferResult {

        private final long oclBuffer;
        private final long address;
        private final int result;

        public HIPBufferResult(long oclBuffer, long address, int result) {
            this.oclBuffer = oclBuffer;
            this.address = address;
            this.result = result;
        }

        public long getBuffer() {
            return oclBuffer;
        }

        public long getAddress() {
            return address;
        }

        public int getResult() {
            return result;
        }
    }
}
