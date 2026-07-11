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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * A NCCL communicator over a set of CUDA devices (one rank per device), exposing
 * FP32 collective operations on TornadoVM {@link FloatArray} buffers - one array
 * per rank.
 *
 * <p>This is a <b>multi-GPU</b> API and is complementary to the single-device
 * hybrid library-task providers (cuBLAS, cuDNN, ...): a typical use runs a
 * TornadoVM {@code TaskGraph} on each device to produce that rank's contribution,
 * then combines them with a collective:</p>
 *
 * <pre>
 * try (TornadoNccl comm = new TornadoNccl(0, 1, 2, 3)) {   // 4 GPUs
 *     // ... each rank's TaskGraph fills partial[r] on device r ...
 *     comm.allReduce(partial, result, NcclRedOp.SUM);      // result[r] = sum over ranks
 * }
 * </pre>
 *
 * <p>Collectives are host-staged in this first version (the per-rank arrays are
 * copied to/from device buffers around the NCCL call); sharing TornadoVM's own
 * device buffers zero-copy is a planned optimization. Requires the CUDA backend
 * and NCCL; construction throws if NCCL is unavailable.</p>
 */
public final class TornadoNccl implements AutoCloseable {

    private final int nRanks;
    private long handle;

    /**
     * Creates a communicator with one rank per given CUDA device id (e.g.
     * {@code new TornadoNccl(0, 1)} for the first two GPUs). On a single-GPU
     * host, {@code new TornadoNccl(0)} creates a one-rank communicator.
     */
    public TornadoNccl(int... deviceIds) {
        if (deviceIds == null || deviceIds.length == 0) {
            throw new TornadoRuntimeException("[ERROR] TornadoNccl requires at least one device id");
        }
        NcclNativeLib.load();
        this.nRanks = deviceIds.length;
        this.handle = NcclNativeLib.create(deviceIds);
        if (handle == 0) {
            throw new TornadoRuntimeException("[ERROR] ncclCommInitAll failed for " + nRanks + " ranks (are the device ids valid and distinct?)");
        }
    }

    public int numRanks() {
        return nRanks;
    }

    private void requireRanks(FloatArray[] arrays, String name) {
        if (arrays == null || arrays.length != nRanks) {
            throw new TornadoRuntimeException("[ERROR] " + name + " must have one FloatArray per rank (" + nRanks + ")");
        }
    }

    private static float[] flatten(FloatArray[] arrays, int elemsPerRank) {
        float[] flat = new float[arrays.length * elemsPerRank];
        for (int r = 0; r < arrays.length; r++) {
            if (arrays[r].getSize() != elemsPerRank) {
                throw new TornadoRuntimeException("[ERROR] rank " + r + " buffer has " + arrays[r].getSize() + " elements, expected " + elemsPerRank);
            }
            for (int i = 0; i < elemsPerRank; i++) {
                flat[r * elemsPerRank + i] = arrays[r].get(i);
            }
        }
        return flat;
    }

    private static void unflatten(float[] flat, FloatArray[] arrays, int elemsPerRank) {
        for (int r = 0; r < arrays.length; r++) {
            for (int i = 0; i < elemsPerRank; i++) {
                arrays[r].set(i, flat[r * elemsPerRank + i]);
            }
        }
    }

    /**
     * {@code recv[r][i] = op(send[0][i], ..., send[nRanks-1][i])} for every rank
     * {@code r} and element {@code i}. All arrays have the same length.
     */
    public void allReduce(FloatArray[] send, FloatArray[] recv, NcclRedOp op) {
        requireRanks(send, "send");
        requireRanks(recv, "recv");
        int count = send[0].getSize();
        float[] s = flatten(send, count);
        float[] d = new float[nRanks * count];
        NcclNativeLib.check(NcclNativeLib.allReduce(handle, s, d, count, op.nativeOp()), "allReduce");
        unflatten(d, recv, count);
    }

    /**
     * Copies the {@code root} rank's buffer to every rank (in place). All buffers
     * have the same length.
     */
    public void broadcast(FloatArray[] buffers, int root) {
        requireRanks(buffers, "buffers");
        if (root < 0 || root >= nRanks) {
            throw new TornadoRuntimeException("[ERROR] broadcast root out of range: " + root);
        }
        int count = buffers[0].getSize();
        float[] b = flatten(buffers, count);
        NcclNativeLib.check(NcclNativeLib.broadcast(handle, b, count, root), "broadcast");
        unflatten(b, buffers, count);
    }

    /**
     * Reduces across ranks into the {@code root} rank's {@code recv} buffer; the
     * other ranks' {@code recv} contents are undefined. All arrays have the same
     * length.
     */
    public void reduce(FloatArray[] send, FloatArray[] recv, int root, NcclRedOp op) {
        requireRanks(send, "send");
        requireRanks(recv, "recv");
        if (root < 0 || root >= nRanks) {
            throw new TornadoRuntimeException("[ERROR] reduce root out of range: " + root);
        }
        int count = send[0].getSize();
        float[] s = flatten(send, count);
        float[] d = new float[nRanks * count];
        NcclNativeLib.check(NcclNativeLib.reduce(handle, s, d, count, root, op.nativeOp()), "reduce");
        unflatten(d, recv, count);
    }

    /**
     * Every rank receives the concatenation of all ranks' {@code send} buffers.
     * Each {@code send[r]} has {@code count} elements; each {@code recv[r]} must
     * have {@code nRanks * count} elements.
     */
    public void allGather(FloatArray[] send, FloatArray[] recv, int count) {
        requireRanks(send, "send");
        requireRanks(recv, "recv");
        float[] s = flatten(send, count);
        float[] d = new float[nRanks * (nRanks * count)];
        NcclNativeLib.check(NcclNativeLib.allGather(handle, s, d, count), "allGather");
        unflatten(d, recv, nRanks * count);
    }

    /**
     * Reduces the ranks' contributions and scatters the result: rank {@code r}
     * receives the reduction of block {@code r}. Each {@code send[r]} must have
     * {@code nRanks * count} elements; each {@code recv[r]} has {@code count}.
     */
    public void reduceScatter(FloatArray[] send, FloatArray[] recv, int count, NcclRedOp op) {
        requireRanks(send, "send");
        requireRanks(recv, "recv");
        float[] s = flatten(send, nRanks * count);
        float[] d = new float[nRanks * count];
        NcclNativeLib.check(NcclNativeLib.reduceScatter(handle, s, d, count, op.nativeOp()), "reduceScatter");
        unflatten(d, recv, count);
    }

    @Override
    public void close() {
        if (handle != 0) {
            NcclNativeLib.destroy(handle);
            handle = 0;
        }
    }
}
