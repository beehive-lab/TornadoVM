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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Bindings to NVIDIA NCCL and the one CUDA runtime call the provider needs. Communicators, streams
 * and device pointers are opaque handles carried as {@code long}; device pointers are raw
 * {@code CUdeviceptr} values of TornadoVM-managed buffers, and every collective is enqueued on the
 * TornadoVM execution stream of its plan, so it is ordered with the kernels and transfers around it.
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

    private static final SymbolLookup LIBNCCL = FFMSupport.loadLibrary("libnccl.so.2", "libnccl.so");

    /**
     * The CUDA runtime is only used to ask which device the current context belongs to: NCCL
     * addresses devices by CUDA runtime ordinal, while TornadoVM addresses them by its own index.
     */
    private static final SymbolLookup LIBCUDART = FFMSupport.loadLibrary("libcudart.so.13", "libcudart.so.12", "libcudart.so");

    private static final MethodHandle NCCL_GET_VERSION;
    private static final MethodHandle NCCL_GET_ERROR_STRING;
    private static final MethodHandle NCCL_COMM_INIT_ALL;
    private static final MethodHandle NCCL_COMM_DESTROY;
    private static final MethodHandle NCCL_ALL_REDUCE;
    private static final MethodHandle NCCL_BROADCAST;
    private static final MethodHandle NCCL_REDUCE;
    private static final MethodHandle NCCL_ALL_GATHER;
    private static final MethodHandle NCCL_REDUCE_SCATTER;
    private static final MethodHandle NCCL_SEND;
    private static final MethodHandle NCCL_RECV;
    private static final MethodHandle NCCL_GROUP_START;
    private static final MethodHandle NCCL_GROUP_END;
    private static final MethodHandle CUDA_GET_DEVICE;

    static {
        if (LIBNCCL == null || LIBCUDART == null) {
            NCCL_GET_VERSION = null;
            NCCL_GET_ERROR_STRING = null;
            NCCL_COMM_INIT_ALL = null;
            NCCL_COMM_DESTROY = null;
            NCCL_ALL_REDUCE = null;
            NCCL_BROADCAST = null;
            NCCL_REDUCE = null;
            NCCL_ALL_GATHER = null;
            NCCL_REDUCE_SCATTER = null;
            NCCL_SEND = null;
            NCCL_RECV = null;
            NCCL_GROUP_START = null;
            NCCL_GROUP_END = null;
            CUDA_GET_DEVICE = null;
        } else {
            NCCL_GET_VERSION = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_POINTER), "ncclGetVersion");
            NCCL_GET_ERROR_STRING = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_POINTER, C_INT), "ncclGetErrorString");
            NCCL_COMM_INIT_ALL = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER), "ncclCommInitAll");
            NCCL_COMM_DESTROY = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG), "ncclCommDestroy");
            // (sendbuff, recvbuff, count, datatype, op, comm, stream)
            NCCL_ALL_REDUCE = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclAllReduce");
            // (sendbuff, recvbuff, count, datatype, root, comm, stream)
            NCCL_BROADCAST = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclBroadcast");
            // (sendbuff, recvbuff, count, datatype, op, root, comm, stream)
            NCCL_REDUCE = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_INT, C_LONG, C_LONG), "ncclReduce");
            // (sendbuff, recvbuff, sendcount, datatype, comm, stream)
            NCCL_ALL_GATHER = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_LONG, C_LONG), "ncclAllGather");
            // (sendbuff, recvbuff, recvcount, datatype, op, comm, stream)
            NCCL_REDUCE_SCATTER = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclReduceScatter");
            // (buff, count, datatype, peer, comm, stream)
            NCCL_SEND = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclSend");
            NCCL_RECV = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_INT, C_LONG, C_LONG), "ncclRecv");
            NCCL_GROUP_START = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT), "ncclGroupStart");
            NCCL_GROUP_END = FFMSupport.downcall(LIBNCCL, FunctionDescriptor.of(C_INT), "ncclGroupEnd");
            CUDA_GET_DEVICE = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_POINTER), "cudaGetDevice");
        }
    }

    private NcclNativeLib() {
    }

    /** Whether NCCL and the CUDA runtime can both be reached on this host. */
    public static boolean isAvailable() {
        return NCCL_COMM_INIT_ALL != null && CUDA_GET_DEVICE != null;
    }

    /** Fails with a clear message when NCCL cannot be reached. */
    public static void load() {
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
    public static int version() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            check((int) NCCL_GET_VERSION.invokeExact(version), "ncclGetVersion");
            return version.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** CUDA runtime ordinal of the device whose context is current on the calling thread. */
    public static int currentDeviceOrdinal() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment device = FFMSupport.allocateInt(arena);
            int result = (int) CUDA_GET_DEVICE.invokeExact(device);
            if (result != CUDA_SUCCESS) {
                throw new TornadoRuntimeException("[ERROR] cudaGetDevice failed with status " + result);
            }
            return device.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Creates one communicator per device ordinal, in the same order; rank {@code i} runs on {@code ordinals[i]}. */
    public static long[] commInitAll(int[] ordinals) {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment comms = FFMSupport.allocateArray(arena, C_LONG, ordinals.length);
            MemorySegment devices = FFMSupport.allocateArray(arena, C_INT, ordinals.length);
            for (int i = 0; i < ordinals.length; i++) {
                devices.setAtIndex(C_INT, i, ordinals[i]);
            }
            check((int) NCCL_COMM_INIT_ALL.invokeExact(comms, ordinals.length, devices), "ncclCommInitAll");
            long[] result = new long[ordinals.length];
            for (int i = 0; i < ordinals.length; i++) {
                result[i] = comms.getAtIndex(C_LONG, i);
            }
            return result;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void commDestroy(long comm) {
        try {
            check((int) NCCL_COMM_DESTROY.invokeExact(comm), "ncclCommDestroy");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void allReduce(long send, long recv, long count, int dataType, int op, long comm, long stream) {
        try {
            check((int) NCCL_ALL_REDUCE.invokeExact(send, recv, count, dataType, op, comm, stream), "ncclAllReduce");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void broadcast(long send, long recv, long count, int dataType, int root, long comm, long stream) {
        try {
            check((int) NCCL_BROADCAST.invokeExact(send, recv, count, dataType, root, comm, stream), "ncclBroadcast");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void reduce(long send, long recv, long count, int dataType, int op, int root, long comm, long stream) {
        try {
            check((int) NCCL_REDUCE.invokeExact(send, recv, count, dataType, op, root, comm, stream), "ncclReduce");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void allGather(long send, long recv, long sendCount, int dataType, long comm, long stream) {
        try {
            check((int) NCCL_ALL_GATHER.invokeExact(send, recv, sendCount, dataType, comm, stream), "ncclAllGather");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void reduceScatter(long send, long recv, long recvCount, int dataType, int op, long comm, long stream) {
        try {
            check((int) NCCL_REDUCE_SCATTER.invokeExact(send, recv, recvCount, dataType, op, comm, stream), "ncclReduceScatter");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void send(long buffer, long count, int dataType, int peer, long comm, long stream) {
        try {
            check((int) NCCL_SEND.invokeExact(buffer, count, dataType, peer, comm, stream), "ncclSend");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void recv(long buffer, long count, int dataType, int peer, long comm, long stream) {
        try {
            check((int) NCCL_RECV.invokeExact(buffer, count, dataType, peer, comm, stream), "ncclRecv");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * A send and a receive that progress together, as one NCCL group. Issued separately, two ranks
     * that each send to the other before receiving would both wait in their send forever.
     */
    static void sendRecv(long send, int toPeer, long recv, int fromPeer, long count, int dataType, long comm, long stream) {
        try {
            check((int) NCCL_GROUP_START.invokeExact(), "ncclGroupStart");
            int result;
            try {
                check((int) NCCL_SEND.invokeExact(send, count, dataType, toPeer, comm, stream), "ncclSend");
                check((int) NCCL_RECV.invokeExact(recv, count, dataType, fromPeer, comm, stream), "ncclRecv");
            } finally {
                // The group has to be closed whatever happened inside it, or the thread stays in group mode.
                result = (int) NCCL_GROUP_END.invokeExact();
            }
            check(result, "ncclGroupEnd");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void check(int result, String call) throws Throwable {
        if (result != NCCL_SUCCESS) {
            MemorySegment message = (MemorySegment) NCCL_GET_ERROR_STRING.invokeExact(result);
            String text = message.equals(MemorySegment.NULL) ? "unknown error" : FFMSupport.readCString(message);
            throw new TornadoRuntimeException("[ERROR] " + call + " failed: " + text + " (ncclResult_t " + result + ")");
        }
    }
}
