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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

/**
 * Several NCCL operations of one communicator issued together, as one NCCL group, by a single
 * library task ({@link Nccl#group(NcclGroup)}). NCCL then progresses them together, which is
 * what an exchange with several peers needs (each rank sending to and receiving from all the others
 * would deadlock as separate tasks) and lets several collectives share one launch.
 *
 * <pre>{@code
 * NcclGroup halo = NcclGroup.on(communicator)
 *         .send(toLeft, left).recv(fromLeft, left)
 *         .send(toRight, right).recv(fromRight, right);
 * taskGraph.libraryTask("halo", Nccl::group, halo);
 * }</pre>
 *
 * <p>
 * The operations are checked as they are added, like the single-operation factories of
 * {@link Nccl}. A buffer may appear only once in a group. The group is read when the task is added
 * to a task graph; changing it afterwards does not change that task.
 * </p>
 */
public final class NcclGroup {

    /** The operations a group can hold, with the number of buffers each takes. */
    public enum Kind {
        ALL_REDUCE(2), //
        ALL_REDUCE_IN_PLACE(1), //
        BROADCAST(1), //
        REDUCE(2), //
        ALL_GATHER(2), //
        REDUCE_SCATTER(2), //
        SEND(1), //
        RECV(1);

        private final int buffers;

        Kind(int buffers) {
            this.buffers = buffers;
        }

        /** Number of buffers the operation takes. */
        public int buffers() {
            return buffers;
        }
    }

    /**
     * One operation: {@code a} is the reduction op, the root or the peer, {@code b} the root of a
     * reduce; {@code count} is the element count NCCL is given for it.
     */
    record Operation(Kind kind, int a, int b, int dataType, long count, TornadoNativeArray[] buffers, Access[] access) {
    }

    private final NcclCommunicator communicator;
    private final List<Operation> operations = new ArrayList<>();
    private final Map<TornadoNativeArray, Boolean> buffers = new IdentityHashMap<>();

    private NcclGroup(NcclCommunicator communicator) {
        this.communicator = communicator;
    }

    /** An empty group on {@code communicator}. */
    public static NcclGroup on(NcclCommunicator communicator) {
        if (communicator == null) {
            throw new TornadoRuntimeException("[ERROR] An NCCL group needs a communicator");
        }
        return new NcclGroup(communicator);
    }

    public NcclCommunicator communicator() {
        return communicator;
    }

    List<Operation> operations() {
        return Collections.unmodifiableList(operations);
    }

    private NcclGroup add(Kind kind, int a, int b, int dataType, long count, TornadoNativeArray[] arrays, Access[] access) {
        for (TornadoNativeArray array : arrays) {
            if (buffers.containsKey(array)) {
                throw new TornadoRuntimeException("[ERROR] NCCL group: a buffer may appear only once in a group (" + kind + ")");
            }
        }
        for (TornadoNativeArray array : arrays) {
            buffers.put(array, Boolean.TRUE);
        }
        operations.add(new Operation(kind, a, b, dataType, count, arrays, access));
        return this;
    }

    /** {@code recv = op(send of every rank)}; see {@link Nccl#allReduce}. */
    public NcclGroup allReduce(TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op) {
        Nccl.requireSameShape(send, recv, 1, "allReduce");
        return add(Kind.ALL_REDUCE, op.code(), 0, Nccl.dataType(send), send.getSize(), new TornadoNativeArray[] { send, recv }, new Access[] { Access.READ_ONLY, Access.WRITE_ONLY });
    }

    /** {@code buffer = op(buffer of every rank)}, in place; see {@link Nccl#allReduceInPlace}. */
    public NcclGroup allReduceInPlace(TornadoNativeArray buffer, NcclRedOp op) {
        return add(Kind.ALL_REDUCE_IN_PLACE, op.code(), 0, Nccl.dataType(buffer), buffer.getSize(), new TornadoNativeArray[] { buffer }, new Access[] { Access.READ_WRITE });
    }

    /** {@code buffer} of rank {@code root} to every rank; see {@link Nccl#broadcast}. */
    public NcclGroup broadcast(TornadoNativeArray buffer, int root) {
        Nccl.requireRank(communicator, root, "broadcast");
        return add(Kind.BROADCAST, root, 0, Nccl.dataType(buffer), buffer.getSize(), new TornadoNativeArray[] { buffer }, new Access[] { Access.READ_WRITE });
    }

    /** {@code recv = op(send of every rank)} on rank {@code root}; see {@link Nccl#reduce}. */
    public NcclGroup reduce(TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op, int root) {
        Nccl.requireSameShape(send, recv, 1, "reduce");
        Nccl.requireRank(communicator, root, "reduce");
        return add(Kind.REDUCE, op.code(), root, Nccl.dataType(send), send.getSize(), new TornadoNativeArray[] { send, recv }, new Access[] { Access.READ_ONLY, Access.READ_WRITE });
    }

    /** Every rank receives all {@code send} buffers in rank order; see {@link Nccl#allGather}. */
    public NcclGroup allGather(TornadoNativeArray send, TornadoNativeArray recv) {
        Nccl.requireSameShape(recv, send, communicator.size(), "allGather");
        return add(Kind.ALL_GATHER, 0, 0, Nccl.dataType(send), send.getSize(), new TornadoNativeArray[] { send, recv }, new Access[] { Access.READ_ONLY, Access.WRITE_ONLY });
    }

    /** Reduce, then rank {@code r} keeps block {@code r}; see {@link Nccl#reduceScatter}. */
    public NcclGroup reduceScatter(TornadoNativeArray send, TornadoNativeArray recv, NcclRedOp op) {
        Nccl.requireSameShape(send, recv, communicator.size(), "reduceScatter");
        return add(Kind.REDUCE_SCATTER, op.code(), 0, Nccl.dataType(recv), recv.getSize(), new TornadoNativeArray[] { send, recv }, new Access[] { Access.READ_ONLY, Access.WRITE_ONLY });
    }

    /** Sends {@code buffer} to rank {@code peer}; see {@link Nccl#send}. */
    public NcclGroup send(TornadoNativeArray buffer, int peer) {
        Nccl.requireRank(communicator, peer, "send");
        return add(Kind.SEND, peer, 0, Nccl.dataType(buffer), buffer.getSize(), new TornadoNativeArray[] { buffer }, new Access[] { Access.READ_ONLY });
    }

    /** Receives {@code buffer} from rank {@code peer}; see {@link Nccl#recv}. */
    public NcclGroup recv(TornadoNativeArray buffer, int peer) {
        Nccl.requireRank(communicator, peer, "recv");
        return add(Kind.RECV, peer, 0, Nccl.dataType(buffer), buffer.getSize(), new TornadoNativeArray[] { buffer }, new Access[] { Access.WRITE_ONLY });
    }

    /** Number of operations in the group. */
    public int size() {
        return operations.size();
    }
}
