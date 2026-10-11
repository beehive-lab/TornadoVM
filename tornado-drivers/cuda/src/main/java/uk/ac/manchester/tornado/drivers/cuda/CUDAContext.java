/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueProperties;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAPinnedMemoryRegistry;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class CUDAContext implements CUDAContextInterface {

    /** One context handle per device, indexed like {@link #devices}; {@code 0} until first use. */
    private final long[] contextIDs;
    private final List<CUDATargetDevice> devices;
    private final List<CUDADeviceContext> deviceContexts;

    private final CUDAPlatform platform;

    private final TornadoLogger logger;

    /** Refcounted bookkeeping for user host memory pinned via {@code cuMemHostRegister}. */
    private final CUDAPinnedMemoryRegistry pinnedMemoryRegistry;

    public CUDAContext(CUDAPlatform platform, List<CUDATargetDevice> devices) {
        this.platform = platform;
        this.contextIDs = new long[devices.size()];
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.logger = new TornadoLogger(this.getClass());
        this.pinnedMemoryRegistry = new CUDAPinnedMemoryRegistry(this);
    }

    public CUDAPinnedMemoryRegistry getPinnedMemoryRegistry() {
        return pinnedMemoryRegistry;
    }

    /** {@code CUstream_flags}: the stream does not synchronise with the NULL stream. */
    private static final int CU_STREAM_NON_BLOCKING = 0x1;
    /** {@code CU_MEMHOSTREGISTER_PORTABLE}: keep the pin valid across every CUDA context. */
    private static final int CU_MEMHOSTREGISTER_PORTABLE = 0x01;
    private static final int CUDA_ERROR_INVALID_VALUE = 1;
    private static final int CUDA_ERROR_INVALID_CONTEXT = 201;

    /**
     * The arena backing each off-heap host allocation, keyed by its address, so that
     * {@link #freeOffHeapMemory(long)} can release exactly the region it was handed.
     */
    private static final Map<Long, Arena> OFF_HEAP_ARENAS = new ConcurrentHashMap<>();

    void clReleaseContext(long id) throws CUDAException {
        CUDAHandles.Context context = (CUDAHandles.Context) CUDAHandles.release(id);
        if (context == null) {
            return;
        }
        CUDADriverAPI.cuDevicePrimaryCtxRelease(context.device());
    }

    /**
     * Returns the handle of the context of the device at {@code deviceIndex}. The device's primary
     * context is retained on first use, so GPUs a program never runs on are left untouched.
     */
    @Override
    public synchronized long getContextId(int deviceIndex) {
        if (contextIDs[deviceIndex] == 0) {
            try {
                contextIDs[deviceIndex] = CUDAPlatform.retainPrimaryContext(devices.get(deviceIndex).getDevicePointer());
            } catch (CUDAException e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
        }
        return contextIDs[deviceIndex];
    }

    /**
     * Makes the context of the device at {@code deviceIndex} current on the calling thread. Driver
     * calls that take no stream (allocation, event creation, the CUDA runtime under the native
     * libraries) act on the current context, so they land on the right GPU only after this.
     */
    public void makeCurrent(int deviceIndex) {
        CUDAHandles.Context context = CUDAHandles.resolve(getContextId(deviceIndex), CUDAHandles.Context.class);
        if (context != null) {
            CUDADriverAPI.cuCtxSetCurrent(context.context());
        }
    }

    /**
     * Runs the transfer warm-up of a device's context on a throwaway stream, so the first execution
     * on that device does not pay for the driver's lazy set-up of its first copies.
     */
    public void warmUp(int deviceIndex) {
        try {
            long queue = clCreateCommandQueue(getContextId(deviceIndex), devices.get(deviceIndex).getDevicePointer(), getProperties());
            CUDACommandQueue.clReleaseCommandQueue(queue);
        } catch (CUDAException e) {
            // Only an optimisation: the first real queue on the device warms the path again.
            logger.debug("warm-up of device %d skipped: %s", deviceIndex, e.getMessage());
        }
    }

    /** Waits for the outstanding work of every device that has a context. */
    private synchronized void synchronizeAll() {
        for (long id : contextIDs) {
            CUDAHandles.Context context = id == 0 ? null : CUDAHandles.resolve(id, CUDAHandles.Context.class);
            if (context != null) {
                CUDADriverAPI.cuCtxSetCurrent(context.context());
                CUDADriverAPI.cuCtxSynchronize();
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

    void clGetContextInfo(long id, int info, byte[] buffer) throws CUDAException {
        // Context info is only used for debugging logs on the Java side; zero-fill.
        Arrays.fill(buffer, (byte) 0);
    }

    /** Maps to a {@code CUstream} pinned to the device's context. */
    public long clCreateCommandQueue(long contextId, long deviceId, long properties) throws CUDAException {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        if (context == null) {
            return 0;
        }
        int result = CUDADriverAPI.cuCtxSetCurrent(context.context());
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            throw new CUDAException(CUDADriverAPI.describe("cuCtxSetCurrent", result));
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stream = FFMSupport.allocatePointer(arena);
            result = CUDADriverAPI.cuStreamCreate(stream, CU_STREAM_NON_BLOCKING);
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                throw new CUDAException(CUDADriverAPI.describe("cuStreamCreate", result));
            }
            long streamPointer = stream.get(FFMSupport.C_POINTER, 0).address();
            long handle = CUDAHandles.register(new CUDAHandles.Queue(streamPointer, context.context(), context.device(), properties));
            CUDACommandQueue.warmUpIssuePath(handle);
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
            System.out.printf("CUDA off-heap memory allocation of %d bytes failed: %s%n", size, e.getMessage());
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
     * Allocates device memory. The returned {@code CUdeviceptr} is the "buffer" the Java side
     * stores and later passes to read/write/launch.
     */
    CUDABufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws CUDAException {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        if (context == null) {
            return new CUDABufferResult(0L, hostPointer, CUDA_ERROR_INVALID_CONTEXT);
        }
        CUDADriverAPI.cuCtxSetCurrent(context.context());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment devicePointer = FFMSupport.allocateLong(arena);
            // cuMemAlloc's status is returned rather than thrown: the caller distinguishes
            // out-of-memory from other failures and raises TornadoOutOfMemoryException itself.
            int result = CUDADriverAPI.cuMemAlloc(devicePointer, size);
            return new CUDABufferResult(devicePointer.get(FFMSupport.C_LONG, 0), hostPointer, result);
        }
    }

    /**
     * Zero-initialises a device buffer. {@code cuMemAlloc} returns uninitialised device memory and
     * TornadoVM reuses pooled device buffers across executions, so a write-only output the kernel
     * never writes (an early-returning kernel, say) would otherwise read back stale data.
     *
     * <p>{@code cuMemsetD8} on device memory is asynchronous with respect to the host, and it runs
     * on the legacy default stream, which TornadoVM's streams -- created
     * {@code CU_STREAM_NON_BLOCKING} -- are not ordered against. Without waiting for it, the
     * zeroing can land after the plan's own stream has written the buffer (a library call or a
     * kernel issued right after allocation), wiping what it wrote. So the legacy stream is
     * synchronised here: the zeroing is complete before the buffer is handed out. This is on the
     * allocation path only, not per execution.
     */
    int memSetZero(long contextId, long devicePointer, long bytes) {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        if (context == null || devicePointer == 0 || bytes <= 0) {
            return CUDA_ERROR_INVALID_VALUE;
        }
        CUDADriverAPI.cuCtxSetCurrent(context.context());
        int status = CUDADriverAPI.cuMemsetD8(devicePointer, (byte) 0, bytes);
        if (status != 0) {
            return status;
        }
        return CUDADriverAPI.cuStreamSynchronize(0L);
    }

    /** CUDA has no sub-buffer concept; the parent buffer stands in for one. */
    long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws CUDAException {
        return buffer;
    }

    void clReleaseMemObject(long contextId, long memId) throws CUDAException {
        if (memId == 0) {
            return;
        }
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        if (context != null) {
            CUDADriverAPI.cuCtxSetCurrent(context.context());
        }
        // Transfers are asynchronous: drain outstanding work before releasing device memory,
        // otherwise the free races an in-flight copy or kernel that still uses this allocation.
        // Frees only happen on eviction and teardown, so the heavy context synchronise is fine here.
        CUDADriverAPI.cuCtxSynchronize();
        CUDADriverAPI.cuMemFree(memId);
    }

    /**
     * Stashes the generated CUDA C source; NVRTC compilation happens later in
     * {@code clBuildProgram}, mirroring the OpenCL two-step create-then-build.
     */
    long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws CUDAException {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        String source = new String(data, StandardCharsets.UTF_8);
        return CUDAHandles.register(new CUDAHandles.Program(context == null ? 0 : context.context(), source, null));
    }

    /** Accepts a pre-compiled module image (cubin or PTX) as the "binary" and skips NVRTC. */
    long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws CUDAException {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        CUDAHandles.Program program = new CUDAHandles.Program(context == null ? 0 : context.context(), "", data);
        program.buildStatus = CUDAHandles.Program.BUILD_SUCCESS; // ready to load; clBuildProgram loads the module
        return CUDAHandles.register(program);
    }

    /**
     * CUDA has no SPIR-V ingestion path; {@code -1} makes the Java side raise
     * TornadoNoOpenCLPlatformException, matching the OpenCL &lt; 2.1 behaviour.
     */
    long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws CUDAException {
        return -1;
    }

    /**
     * Registers an existing off-heap host buffer as pinned (page-locked) memory. After
     * registration, the async copies DMA directly to and from this address instead of going through
     * CUDA's internal staging copy, which is what allows true host-device overlap without blocking
     * the calling thread.
     */
    private static int cuMemHostRegister(long contextId, long hostPointer, long numBytes) {
        CUDAHandles.Context context = CUDAHandles.resolve(contextId, CUDAHandles.Context.class);
        if (context == null) {
            return -1;
        }
        CUDADriverAPI.cuCtxSetCurrent(context.context());
        return CUDADriverAPI.cuMemHostRegister(hostPointer, numBytes, CU_MEMHOSTREGISTER_PORTABLE);
    }

    /**
     * Unregisters host memory previously registered with {@link #cuMemHostRegister}. Every context
     * is synchronised first so no async DMA can still be reading or writing the region when the pin
     * is dropped: unregistering under an in-flight copy is the data hazard, since the transfer would
     * keep using the page-locked mapping while the driver tears it down. The registration is
     * portable, so a copy on any device may be using it.
     */
    private int cuMemHostUnregister(long hostPointer) {
        synchronizeAll();
        return CUDADriverAPI.cuMemHostUnregister(hostPointer);
    }

    /** Raw {@code CUresult} for "this host range is already page-locked" (see registry policy). */
    public static final int CUDA_ERROR_HOST_MEMORY_ALREADY_REGISTERED = 712;

    /**
     * Registers an off-heap host region as pinned (page-locked) so async transfers DMA
     * directly (no driver staging copy, true transfer/compute overlap).
     *
     * @return the raw {@code CUresult}: {@code 0} on success,
     *     {@link #CUDA_ERROR_HOST_MEMORY_ALREADY_REGISTERED} when another registration
     *     already covers this address (memory is pinned, but not owned by the caller).
     */
    public int registerPinnedMemory(long hostPointer, long numBytes) {
        return cuMemHostRegister(anyContextId(), hostPointer, numBytes);
    }

    /**
     * Unregisters a previously pinned host region. Every context is synchronised
     * first, so no in-flight async DMA can still touch the region when the pin is
     * dropped.
     */
    public int unregisterPinnedMemory(long hostPointer) {
        return cuMemHostUnregister(hostPointer);
    }

    public int getNumDevices() {
        return devices.size();
    }

    public List<CUDATargetDevice> devices() {
        return devices;
    }

    private void createCommandQueue(int index, long properties) {
        CUDATargetDevice device = devices.get(index);
        try {

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;

            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            clCreateCommandQueue(getContextId(index), device.getDevicePointer(), properties);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException("[ERROR] CUDADriver Command Queue Initialization not valid");
        }
    }

    public long getProperties() {
        long properties = 0;
        if (TornadoOptions.ENABLE_OPENCL_PROFILING) {
            properties |= CUDACommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
        }

        if (TornadoOptions.ENABLE_OOO_EXECUTION) {
            properties |= CUDACommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        return properties;
    }

    @Override
    public void createCommandQueue(int index) {
        long properties = getProperties();
        createCommandQueue(index, properties);
    }

    public CUDAProgram createProgramWithSource(byte[] source, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program = null;

        try {
            program = new CUDAProgram(clCreateProgramWithSource(getContextId(deviceContext.getDeviceIndex()), source, lengths), deviceContext);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public CUDAProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program;
        try {
            long programID = clCreateProgramWithIL(getContextId(deviceContext.getDeviceIndex()), spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoNoOpenCLPlatformException("CUDADriver version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new CUDAProgram(programID, deviceContext);
        } catch (CUDAException e) {
            throw new TornadoRuntimeException(e);
        }

        return program;
    }

    public CUDAProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program = null;

        try {
            program = new CUDAProgram(clCreateProgramWithBinary(getContextId(deviceContext.getDeviceIndex()), deviceId, binary, lengths), deviceContext);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (TornadoOptions.DUMP_EVENTS) {
            for (CUDADeviceContext deviceContext : deviceContexts) {
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
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("platform=%d, device count=%d", getPlatformIndex(), getNumDevices());
    }

    @Override
    public CUDADeviceContext createDeviceContext(int index) {
        logger.debug("creating device context for device: %s", devices.get(index).toString());
        final CUDADeviceContext deviceContext = new CUDADeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    public CUDABufferResult createBuffer(int deviceIndex, long flags, long bytes) {
        return createBuffer(deviceIndex, flags, bytes, 0L);
    }

    private CUDABufferResult createBuffer(int deviceIndex, long flags, long bytes, long hostPointer) {
        try {
            final CUDABufferResult result = createBuffer(getContextId(deviceIndex), flags, bytes, hostPointer);
            // cuMemAlloc reports failures (notably CUDA_ERROR_OUT_OF_MEMORY) via the
            // result status and a null device pointer rather than throwing. Surface it
            // as a clean exception here: otherwise the zero buffer is used by a later
            // copy/kernel launch and triggers an unrecoverable CUDA_ERROR_ILLEGAL_ADDRESS
            // that poisons the whole context.
            if (result == null || result.getResult() != CUDADriver.CUDA_SUCCESS || result.getBuffer() == 0L) {
                int status = (result == null) ? -1 : result.getResult();
                throw new TornadoOutOfMemoryException("[ERROR] Unable to allocate " + RuntimeUtilities.humanReadableByteCount(bytes, false) + " on the CUDA device (cuMemAlloc status=" + status
                        + ").\n\tThe allocation exceeds available device memory. Reduce the working set, or enable CUDA Unified Memory to over-subscribe VRAM.");
            }
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(int deviceIndex, long bufferId) {
        try {
            clReleaseMemObject(getContextId(deviceIndex), bufferId);
            logger.info("buffer released 0x%x", bufferId);
        } catch (CUDAException e) {
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

    public CUDAPlatform getPlatform() {
        return platform;
    }

    public static class CUDABufferResult {

        private final long oclBuffer;
        private final long address;
        private final int result;

        public CUDABufferResult(long oclBuffer, long address, int result) {
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
