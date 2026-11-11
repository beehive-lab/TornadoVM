/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.nccl.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Bindings to NVIDIA NCCL and the one CUDA runtime call the provider needs. Communicators, streams
 * and device pointers are opaque handles carried as {@code long}; device pointers are raw
 * {@code CUdeviceptr} values of TornadoVM-managed buffers, and every collective is enqueued on the
 * TornadoVM execution stream of its plan, so it is ordered with the kernels and transfers around it.
 *
 * <p>
 * On AMD GPUs the same bindings reach RCCL, ROCm's implementation of the NCCL API and ABI, with the
 * HIP runtime in place of the CUDA runtime (the calls used here have the same signatures). Each
 * device uses the bindings of its backend ({@link #forBackend}): RCCL for HIP devices, NCCL for CUDA
 * ones, each loaded when first used; {@code -Dtornado.nccl.library=rccl|nccl} picks one for every
 * device.
 */
public final class NcclNativeLib {

    /** {@code ncclSuccess} and {@code cudaSuccess}. */
    private static final int NCCL_SUCCESS = 0;
    private static final int CUDA_SUCCESS = 0;

    /** {@code ncclDataType_t} values. */
    public static final int NCCL_INT8 = 0;
    public static final int NCCL_INT32 = 2;
    public static final int NCCL_INT64 = 4;
    public static final int NCCL_FLOAT16 = 6;
    public static final int NCCL_FLOAT32 = 7;
    public static final int NCCL_FLOAT64 = 8;
    public static final int NCCL_BFLOAT16 = 9;

    /** {@code NCCL_UNIQUE_ID_BYTES}. */
    public static final int UNIQUE_ID_BYTES = 128;

    /** {@code ncclUniqueId}: a struct holding {@code char internal[128]}, passed by value to {@code ncclCommInitRank}. */
    private static final StructLayout UNIQUE_ID = MemoryLayout.structLayout(MemoryLayout.sequenceLayout(UNIQUE_ID_BYTES, ValueLayout.JAVA_BYTE).withName("internal"));

    /** Whether these bindings reach RCCL and the HIP runtime rather than NCCL and the CUDA runtime. */
    private final boolean rccl;

    private final MethodHandle ncclGetVersion;
    private final MethodHandle ncclGetErrorString;
    private final MethodHandle ncclCommInitAll;
    private final MethodHandle ncclGetUniqueId;
    private final MethodHandle ncclCommInitRank;
    private final MethodHandle ncclCommDestroy;
    private final MethodHandle ncclCommAbort;
    private final MethodHandle ncclAllReduce;
    private final MethodHandle ncclBroadcast;
    private final MethodHandle ncclReduce;
    private final MethodHandle ncclAllGather;
    private final MethodHandle ncclReduceScatter;
    private final MethodHandle ncclSend;
    private final MethodHandle ncclRecv;
    private final MethodHandle ncclGroupStart;
    private final MethodHandle ncclGroupEnd;
    private final MethodHandle cudaGetDevice;
    private final MethodHandle cudaStreamCreateWithFlags;
    private final MethodHandle cudaEventCreateWithFlags;
    private final MethodHandle cudaEventRecord;
    private final MethodHandle cudaStreamWaitEvent;

    /** {@code cudaStreamNonBlocking} and {@code cudaEventDisableTiming}. */
    private static final int CUDA_STREAM_NON_BLOCKING = 1;
    private static final int CUDA_EVENT_DISABLE_TIMING = 2;

    private NcclNativeLib(boolean rccl) {
        this.rccl = rccl;
        // The CUDA runtime (for RCCL, the HIP runtime) is only used to ask which device the current
        // context belongs to, and for the side streams: NCCL addresses devices by runtime ordinal,
        // while TornadoVM addresses them by its own index.
        SymbolLookup libnccl = rccl ? ROCmLibraries.load(List.of("librccl.so.1", "librccl.so"), List.of("rccl.dll", "librccl.dll"))
                : FFMSupport.loadLibrary("libnccl.so.2", "libnccl.so");
        SymbolLookup libcudart = rccl ? ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"))
                : FFMSupport.loadLibrary("libcudart.so.13", "libcudart.so.12", "libcudart.so");
        if (libnccl == null || libcudart == null) {
            ncclGetVersion = null;
            ncclGetErrorString = null;
            ncclCommInitAll = null;
            ncclGetUniqueId = null;
            ncclCommInitRank = null;
            ncclCommDestroy = null;
            ncclCommAbort = null;
            ncclAllReduce = null;
            ncclBroadcast = null;
            ncclReduce = null;
            ncclAllGather = null;
            ncclReduceScatter = null;
            ncclSend = null;
            ncclRecv = null;
            ncclGroupStart = null;
            ncclGroupEnd = null;
            cudaGetDevice = null;
            cudaStreamCreateWithFlags = null;
            cudaEventCreateWithFlags = null;
            cudaEventRecord = null;
            cudaStreamWaitEvent = null;
        } else {
            ncclGetVersion = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_POINTER), "ncclGetVersion");
            ncclGetErrorString = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_POINTER, C_INT), "ncclGetErrorString");
            ncclCommInitAll = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER), "ncclCommInitAll");
            ncclGetUniqueId = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_POINTER), "ncclGetUniqueId");
            // (comm*, nranks, ncclUniqueId passed by value, rank)
            ncclCommInitRank = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, UNIQUE_ID, C_INT), "ncclCommInitRank");
            ncclCommDestroy = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG), "ncclCommDestroy");
            ncclCommAbort = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG), "ncclCommAbort");
            // (sendbuff, recvbuff, count, datatype, op, comm, stream)
            ncclAllReduce = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclAllReduce");
            // (sendbuff, recvbuff, count, datatype, root, comm, stream)
            ncclBroadcast = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclBroadcast");
            // (sendbuff, recvbuff, count, datatype, op, root, comm, stream)
            ncclReduce = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_INT, C_LONG, C_LONG), "ncclReduce");
            // (sendbuff, recvbuff, sendcount, datatype, comm, stream)
            ncclAllGather = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_LONG, C_LONG), "ncclAllGather");
            // (sendbuff, recvbuff, recvcount, datatype, op, comm, stream)
            ncclReduceScatter = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclReduceScatter");
            // (buff, count, datatype, peer, comm, stream)
            ncclSend = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclSend");
            ncclRecv = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclRecv");
            ncclGroupStart = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT), "ncclGroupStart");
            ncclGroupEnd = FFMSupport.downcall(libnccl, FunctionDescriptor.of(C_INT), "ncclGroupEnd");
            cudaGetDevice = FFMSupport.downcall(libcudart, FunctionDescriptor.of(C_INT, C_POINTER), rccl ? "hipGetDevice" : "cudaGetDevice");
            // the side stream and the events of the collectives started with allReduceInPlaceStart
            cudaStreamCreateWithFlags = FFMSupport.downcall(libcudart, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), rccl ? "hipStreamCreateWithFlags" : "cudaStreamCreateWithFlags");
            cudaEventCreateWithFlags = FFMSupport.downcall(libcudart, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), rccl ? "hipEventCreateWithFlags" : "cudaEventCreateWithFlags");
            cudaEventRecord = FFMSupport.downcall(libcudart, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), rccl ? "hipEventRecord" : "cudaEventRecord");
            cudaStreamWaitEvent = FFMSupport.downcall(libcudart, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT), rccl ? "hipStreamWaitEvent" : "cudaStreamWaitEvent");
        }
    }

    private static final class Nccl {
        static final NcclNativeLib BINDINGS = new NcclNativeLib(false);
    }

    private static final class Rccl {
        static final NcclNativeLib BINDINGS = new NcclNativeLib(true);
    }

    /**
     * The bindings for devices of {@code backend}: RCCL for HIP devices, NCCL for the others, or the
     * library {@code -Dtornado.nccl.library=rccl|nccl} names. Each is loaded on first use.
     */
    public static NcclNativeLib forBackend(TornadoVMBackendType backend) {
        String library = System.getProperty("tornado.nccl.library", "").trim();
        boolean rccl = library.isEmpty() ? backend == TornadoVMBackendType.HIP : library.equalsIgnoreCase("rccl");
        return rccl ? Rccl.BINDINGS : Nccl.BINDINGS;
    }

    /**
     * The bindings for calls that name no device, chosen without starting any backend: the library
     * {@code -Dtornado.nccl.library=rccl|nccl} names, else NCCL when it can be loaded, else RCCL when
     * it can, else NCCL (whose {@link #load()} then reports it missing).
     */
    public static NcclNativeLib withoutDevice() {
        if (!System.getProperty("tornado.nccl.library", "").isBlank()) {
            return forBackend(null);
        }
        if (!Nccl.BINDINGS.isAvailable() && Rccl.BINDINGS.isAvailable()) {
            return Rccl.BINDINGS;
        }
        return Nccl.BINDINGS;
    }

    /** Whether the collectives run through RCCL (AMD GPUs) rather than NCCL. */
    public boolean isRccl() {
        return rccl;
    }

    /** Whether NCCL and the CUDA runtime can both be reached on this host. */
    public boolean isAvailable() {
        return ncclCommInitAll != null && cudaGetDevice != null;
    }

    /** Fails with a clear message when NCCL cannot be reached. */
    public void load() {
        if (!isAvailable() && rccl) {
            throw new TornadoRuntimeException("[ERROR] Unable to load RCCL. Install ROCm with RCCL and set ROCM_PATH (or HIP_PATH) to its root.");
        }
        if (!isAvailable()) {
            throw new TornadoRuntimeException("[ERROR] Unable to load NCCL. Install NCCL (e.g. the nvidia-nccl-cu13 wheel or the libnccl2 package) and make sure libnccl.so.2 is on the library path.");
        }
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

    /** NCCL version as {@code major * 10000 + minor * 100 + patch}. */
    public int version() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            check((int) ncclGetVersion.invokeExact(version), "ncclGetVersion");
            return version.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** CUDA runtime ordinal of the device whose context is current on the calling thread. */
    public int currentDeviceOrdinal() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment device = FFMSupport.allocateInt(arena);
            int result = (int) cudaGetDevice.invokeExact(device);
            if (result != CUDA_SUCCESS) {
                throw new TornadoRuntimeException("[ERROR] cudaGetDevice failed with status " + result);
            }
            return device.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Creates one communicator per device ordinal, in the same order; rank {@code i} runs on {@code ordinals[i]}. */
    public long[] commInitAll(int[] ordinals) {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment comms = FFMSupport.allocateArray(arena, C_LONG, ordinals.length);
            MemorySegment devices = FFMSupport.allocateArray(arena, C_INT, ordinals.length);
            for (int i = 0; i < ordinals.length; i++) {
                devices.setAtIndex(C_INT, i, ordinals[i]);
            }
            check((int) ncclCommInitAll.invokeExact(comms, ordinals.length, devices), "ncclCommInitAll");
            long[] result = new long[ordinals.length];
            for (int i = 0; i < ordinals.length; i++) {
                result[i] = comms.getAtIndex(C_LONG, i);
            }
            return result;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** A new NCCL unique id: the 128 bytes every process of a communicator must be given. */
    public byte[] getUniqueId() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(UNIQUE_ID);
            check((int) ncclGetUniqueId.invokeExact(id), "ncclGetUniqueId");
            return id.toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Creates the communicator of one rank of a communicator spanning several processes, on the
     * CUDA device current on the calling thread. Blocks until every rank has joined. Several ranks
     * of one process are created inside {@link #groupStart()}/{@link #groupEnd()}, the calls then
     * returning without waiting.
     */
    public long commInitRank(int worldSize, byte[] uniqueId, int rank) {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment comm = FFMSupport.allocateLong(arena);
            MemorySegment id = arena.allocate(UNIQUE_ID);
            MemorySegment.copy(uniqueId, 0, id, ValueLayout.JAVA_BYTE, 0, UNIQUE_ID_BYTES);
            check((int) ncclCommInitRank.invokeExact(comm, worldSize, id, rank), "ncclCommInitRank");
            return comm.get(C_LONG, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public void groupStart() {
        try {
            check((int) ncclGroupStart.invokeExact(), "ncclGroupStart");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public void groupEnd() {
        try {
            check((int) ncclGroupEnd.invokeExact(), "ncclGroupEnd");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public void commDestroy(long comm) {
        try {
            check((int) ncclCommDestroy.invokeExact(comm), "ncclCommDestroy");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Frees a communicator and aborts its operations still running on the device, so that ranks
     * waiting for a peer that will never arrive return. Safe to call from another thread.
     */
    public void commAbort(long comm) {
        try {
            check((int) ncclCommAbort.invokeExact(comm), "ncclCommAbort");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void allReduce(long send, long recv, long count, int dataType, int op, long comm, long stream) {
        try {
            check((int) ncclAllReduce.invokeExact(send, recv, count, dataType, op, comm, stream), "ncclAllReduce");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * A non-blocking stream and two events (no timing) of the device current on this thread: where a
     * started collective runs, and the fork and join points that order it with the plan's stream.
     */
    long[] createSideChannel() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocateArray(arena, C_LONG, 1);
            long[] result = new long[3];
            cudaCheck((int) cudaStreamCreateWithFlags.invokeExact(handle, CUDA_STREAM_NON_BLOCKING), "cudaStreamCreateWithFlags");
            result[0] = handle.get(C_LONG, 0);
            for (int i = 1; i < 3; i++) {
                cudaCheck((int) cudaEventCreateWithFlags.invokeExact(handle, CUDA_EVENT_DISABLE_TIMING), "cudaEventCreateWithFlags");
                result[i] = handle.get(C_LONG, 0);
            }
            return result;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void eventRecord(long event, long stream) {
        try {
            cudaCheck((int) cudaEventRecord.invokeExact(event, stream), "cudaEventRecord");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Work enqueued on {@code stream} after this waits for {@code event}. */
    void streamWaitEvent(long stream, long event) {
        try {
            cudaCheck((int) cudaStreamWaitEvent.invokeExact(stream, event, 0), "cudaStreamWaitEvent");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void cudaCheck(int result, String call) {
        if (result != CUDA_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + call + " failed with status " + result);
        }
    }

    void broadcast(long send, long recv, long count, int dataType, int root, long comm, long stream) {
        try {
            check((int) ncclBroadcast.invokeExact(send, recv, count, dataType, root, comm, stream), "ncclBroadcast");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void reduce(long send, long recv, long count, int dataType, int op, int root, long comm, long stream) {
        try {
            check((int) ncclReduce.invokeExact(send, recv, count, dataType, op, root, comm, stream), "ncclReduce");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void allGather(long send, long recv, long sendCount, int dataType, long comm, long stream) {
        try {
            check((int) ncclAllGather.invokeExact(send, recv, sendCount, dataType, comm, stream), "ncclAllGather");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void reduceScatter(long send, long recv, long recvCount, int dataType, int op, long comm, long stream) {
        try {
            check((int) ncclReduceScatter.invokeExact(send, recv, recvCount, dataType, op, comm, stream), "ncclReduceScatter");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void send(long buffer, long count, int dataType, int peer, long comm, long stream) {
        try {
            check((int) ncclSend.invokeExact(buffer, count, dataType, peer, comm, stream), "ncclSend");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    void recv(long buffer, long count, int dataType, int peer, long comm, long stream) {
        try {
            check((int) ncclRecv.invokeExact(buffer, count, dataType, peer, comm, stream), "ncclRecv");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * A send and a receive that progress together, as one NCCL group. Issued separately, two ranks
     * that each send to the other before receiving would both wait in their send forever.
     */
    void sendRecv(long send, int toPeer, long recv, int fromPeer, long count, int dataType, long comm, long stream) {
        try {
            check((int) ncclGroupStart.invokeExact(), "ncclGroupStart");
            int result;
            try {
                check((int) ncclSend.invokeExact(send, count, dataType, toPeer, comm, stream), "ncclSend");
                check((int) ncclRecv.invokeExact(recv, count, dataType, fromPeer, comm, stream), "ncclRecv");
            } finally {
                // The group has to be closed whatever happened inside it, or the thread stays in group mode.
                result = (int) ncclGroupEnd.invokeExact();
            }
            check(result, "ncclGroupEnd");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private void check(int result, String call) throws Throwable {
        if (result != NCCL_SUCCESS) {
            MemorySegment message = (MemorySegment) ncclGetErrorString.invokeExact(result);
            String text = message.equals(MemorySegment.NULL) ? "unknown error" : FFMSupport.readCString(message);
            throw new TornadoRuntimeException("[ERROR] " + call + " failed: " + text + " (ncclResult_t " + result + ")");
        }
    }
}
