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
package uk.ac.manchester.tornado.nccl;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;

/**
 * NCCL collectives as TornadoVM library tasks. Each factory describes the call one rank makes; the
 * task graph of every rank of the communicator adds the same collective, and the plans of all ranks
 * execute concurrently:
 *
 * <pre>{@code
 * TaskGraph graph = new TaskGraph("rank" + r)
 *         .task("grad", Kernels::gradient, new KernelContext(), weights, grads)
 *         .libraryTask("sum", Nccl::allReduceInPlace, communicator, grads, NcclRedOp.SUM)
 *         .transferToHost(DataTransferMode.EVERY_EXECUTION, grads);
 * }</pre>
 *
 * <p>
 * Buffers stay on the device: NCCL reads and writes the TornadoVM buffers directly, on the stream
 * of the plan, so a collective is ordered with the kernels and transfers around it. Supported
 * element types: {@link FloatArray}, {@link DoubleArray}, {@link HalfFloatArray},
 * {@link BFloat16Array}, {@link IntArray}, {@link LongArray}, {@link Int8Array} and
 * {@link ByteArray}.
 * </p>
 */
public final class Nccl {

    public static final String LIBRARY_NAME = "nvidia/nccl";

    private Nccl() {
    }

    /**
     * {@code recv = op(send of every rank)}, element-wise; every rank gets the result. {@code send}
     * and {@code recv} have the same type and size.
     */
    public static LibraryTaskDescriptor allReduce(NcclCommunicator communicator, TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op) {
        requireSameShape(send, recv, 1, "allReduce");
        return describe("allReduce", new Object[] { communicator.handle(), send, recv, op.code(), dataType(send), (long) send.getSize() }, //
                Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    /** {@code buffer = op(buffer of every rank)}, element-wise, in place. */
    public static LibraryTaskDescriptor allReduceInPlace(NcclCommunicator communicator, TornadoNativeArray buffer, NcclRedOp op) {
        return describe("allReduceInPlace", new Object[] { communicator.handle(), buffer, op.code(), dataType(buffer), (long) buffer.getSize() }, //
                Access.READ_ONLY, Access.READ_WRITE, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    /** Copies {@code buffer} of rank {@code root} into {@code buffer} of every other rank, in place. */
    public static LibraryTaskDescriptor broadcast(NcclCommunicator communicator, TornadoNativeArray buffer, int root) {
        requireRank(communicator, root, "broadcast");
        return describe("broadcast", new Object[] { communicator.handle(), buffer, root, dataType(buffer), (long) buffer.getSize() }, //
                Access.READ_ONLY, Access.READ_WRITE, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    /**
     * {@code recv = op(send of every rank)} on rank {@code root} only; {@code recv} of the other
     * ranks is left as it is.
     */
    public static LibraryTaskDescriptor reduce(NcclCommunicator communicator, TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op, int root) {
        requireSameShape(send, recv, 1, "reduce");
        requireRank(communicator, root, "reduce");
        return describe("reduce", new Object[] { communicator.handle(), send, recv, op.code(), root, dataType(send), (long) send.getSize() }, //
                Access.READ_ONLY, Access.READ_ONLY, Access.READ_WRITE, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    /**
     * Every rank receives the {@code send} buffers of all ranks, concatenated in rank order:
     * {@code recv} has {@code size()} times the elements of {@code send}.
     */
    public static LibraryTaskDescriptor allGather(NcclCommunicator communicator, TornadoNativeArray send, TornadoNativeArray recv) {
        requireSameShape(recv, send, communicator.size(), "allGather");
        return describe("allGather", new Object[] { communicator.handle(), send, recv, dataType(send), (long) send.getSize() }, //
                Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    /**
     * Reduces the {@code send} buffers of all ranks element-wise and scatters the result: rank
     * {@code r} receives block {@code r}. {@code send} has {@code size()} times the elements of
     * {@code recv}.
     */
    public static LibraryTaskDescriptor reduceScatter(NcclCommunicator communicator, TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op) {
        requireSameShape(send, recv, communicator.size(), "reduceScatter");
        return describe("reduceScatter", new Object[] { communicator.handle(), send, recv, op.code(), dataType(recv), (long) recv.getSize() }, //
                Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY);
    }

    private static LibraryTaskDescriptor describe(String function, Object[] parameters, Access... access) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(parameters) //
                .withAccess(access);
    }

    /** The {@code ncclDataType_t} of an array's elements. */
    static int dataType(TornadoNativeArray array) {
        if (array instanceof FloatArray) {
            return NcclNativeLib.NCCL_FLOAT32;
        } else if (array instanceof DoubleArray) {
            return NcclNativeLib.NCCL_FLOAT64;
        } else if (array instanceof HalfFloatArray) {
            return NcclNativeLib.NCCL_FLOAT16;
        } else if (array instanceof BFloat16Array) {
            return NcclNativeLib.NCCL_BFLOAT16;
        } else if (array instanceof IntArray) {
            return NcclNativeLib.NCCL_INT32;
        } else if (array instanceof LongArray) {
            return NcclNativeLib.NCCL_INT64;
        } else if (array instanceof Int8Array || array instanceof ByteArray) {
            // Java bytes are signed, so MAX and MIN must compare them as int8.
            return NcclNativeLib.NCCL_INT8;
        }
        throw new TornadoRuntimeException("[ERROR] NCCL does not support " + array.getClass().getSimpleName());
    }

    /** Checks that {@code larger} has the type of {@code smaller} and {@code factor} times its elements. */
    private static void requireSameShape(TornadoNativeArray larger, TornadoNativeArray smaller, int factor, String function) {
        if (larger.getClass() != smaller.getClass()) {
            String types = larger.getClass().getSimpleName() + " and " + smaller.getClass().getSimpleName();
            throw new TornadoRuntimeException("[ERROR] NCCL " + function + ": buffers must have the same type (" + types + ")");
        }
        if ((long) larger.getSize() != (long) smaller.getSize() * factor) {
            throw new TornadoRuntimeException("[ERROR] NCCL " + function + ": expected " + ((long) smaller.getSize() * factor) + " elements, got " + larger.getSize());
        }
    }

    private static void requireRank(NcclCommunicator communicator, int rank, String function) {
        if (rank < 0 || rank >= communicator.size()) {
            throw new TornadoRuntimeException("[ERROR] NCCL " + function + ": root " + rank + " is not a rank of a " + communicator.size() + "-rank communicator");
        }
    }
}
