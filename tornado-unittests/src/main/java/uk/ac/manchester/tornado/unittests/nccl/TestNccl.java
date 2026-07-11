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
package uk.ac.manchester.tornado.unittests.nccl;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.TornadoNccl;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Exhaustive tests for the {@link TornadoNccl} multi-GPU collectives API,
 * combined with TornadoVM JIT task graphs. All collective references are written
 * for a general rank count; on a single-GPU host the communicator has one rank
 * (device 0), which still exercises the full comm / stream / collective path and
 * the TornadoVM integration. On a multi-GPU host, extend {@code DEVICES}.
 *
 * Skipped unless the default device is the CUDA backend and libtornado-nccl
 * (plus libnccl) is present.
 */
public class TestNccl extends TornadoTestBase {

    /** Device ids, one per rank. Single GPU here; add ids on a multi-GPU host. */
    private static final int[] DEVICES = { 0 };
    private static final int RANKS = DEVICES.length;

    private static final Random random = new Random(42);

    @Before
    public void ncclMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "NCCL collectives require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-nccl");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-nccl is not available: " + e.getMessage());
        }
    }

    private static FloatArray[] randomRanks(int count) {
        FloatArray[] a = new FloatArray[RANKS];
        for (int r = 0; r < RANKS; r++) {
            a[r] = new FloatArray(count);
            for (int i = 0; i < count; i++) {
                a[r].set(i, random.nextFloat() - 0.5f);
            }
        }
        return a;
    }

    private static FloatArray[] emptyRanks(int count) {
        FloatArray[] a = new FloatArray[RANKS];
        for (int r = 0; r < RANKS; r++) {
            a[r] = new FloatArray(count);
        }
        return a;
    }

    // ---- collective references (general over RANKS) ----

    @Test
    public void testAllReduceSum() {
        int count = 1024;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allReduce(send, recv, NcclRedOp.SUM);
        }
        for (int i = 0; i < count; i++) {
            float expected = 0.0f;
            for (int r = 0; r < RANKS; r++) {
                expected += send[r].get(i);
            }
            for (int r = 0; r < RANKS; r++) {
                assertEquals(expected, recv[r].get(i), 1e-4f * Math.max(1.0f, Math.abs(expected)));
            }
        }
    }

    @Test
    public void testAllReduceMax() {
        int count = 512;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allReduce(send, recv, NcclRedOp.MAX);
        }
        for (int i = 0; i < count; i++) {
            float expected = Float.NEGATIVE_INFINITY;
            for (int r = 0; r < RANKS; r++) {
                expected = Math.max(expected, send[r].get(i));
            }
            for (int r = 0; r < RANKS; r++) {
                assertEquals(expected, recv[r].get(i), 1e-6f);
            }
        }
    }

    @Test
    public void testAllReduceMin() {
        int count = 512;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allReduce(send, recv, NcclRedOp.MIN);
        }
        for (int i = 0; i < count; i++) {
            float expected = Float.POSITIVE_INFINITY;
            for (int r = 0; r < RANKS; r++) {
                expected = Math.min(expected, send[r].get(i));
            }
            for (int r = 0; r < RANKS; r++) {
                assertEquals(expected, recv[r].get(i), 1e-6f);
            }
        }
    }

    @Test
    public void testAllReduceProd() {
        int count = 256;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allReduce(send, recv, NcclRedOp.PROD);
        }
        for (int i = 0; i < count; i++) {
            float expected = 1.0f;
            for (int r = 0; r < RANKS; r++) {
                expected *= send[r].get(i);
            }
            for (int r = 0; r < RANKS; r++) {
                assertEquals(expected, recv[r].get(i), 1e-4f * Math.max(1.0f, Math.abs(expected)));
            }
        }
    }

    @Test
    public void testBroadcast() {
        int count = 1024;
        int root = RANKS - 1;
        FloatArray[] buffers = randomRanks(count);
        float[] rootCopy = new float[count];
        for (int i = 0; i < count; i++) {
            rootCopy[i] = buffers[root].get(i);
        }
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.broadcast(buffers, root);
        }
        for (int r = 0; r < RANKS; r++) {
            for (int i = 0; i < count; i++) {
                assertEquals(rootCopy[i], buffers[r].get(i), 1e-6f);
            }
        }
    }

    @Test
    public void testReduce() {
        int count = 1024;
        int root = 0;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.reduce(send, recv, root, NcclRedOp.SUM);
        }
        for (int i = 0; i < count; i++) {
            float expected = 0.0f;
            for (int r = 0; r < RANKS; r++) {
                expected += send[r].get(i);
            }
            assertEquals(expected, recv[root].get(i), 1e-4f * Math.max(1.0f, Math.abs(expected)));
        }
    }

    @Test
    public void testAllGather() {
        int count = 128;
        FloatArray[] send = randomRanks(count);
        FloatArray[] recv = new FloatArray[RANKS];
        for (int r = 0; r < RANKS; r++) {
            recv[r] = new FloatArray(RANKS * count);
        }
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allGather(send, recv, count);
        }
        for (int r = 0; r < RANKS; r++) {
            for (int src = 0; src < RANKS; src++) {
                for (int i = 0; i < count; i++) {
                    assertEquals(send[src].get(i), recv[r].get(src * count + i), 1e-6f);
                }
            }
        }
    }

    @Test
    public void testReduceScatter() {
        int count = 128;
        // send[r] holds nRanks blocks of `count`; rank r receives the sum of block r.
        FloatArray[] send = new FloatArray[RANKS];
        for (int r = 0; r < RANKS; r++) {
            send[r] = new FloatArray(RANKS * count);
            for (int i = 0; i < RANKS * count; i++) {
                send[r].set(i, random.nextFloat() - 0.5f);
            }
        }
        FloatArray[] recv = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.reduceScatter(send, recv, count, NcclRedOp.SUM);
        }
        for (int dst = 0; dst < RANKS; dst++) {
            for (int i = 0; i < count; i++) {
                float expected = 0.0f;
                for (int r = 0; r < RANKS; r++) {
                    expected += send[r].get(dst * count + i);
                }
                assertEquals(expected, recv[dst].get(i), 1e-4f * Math.max(1.0f, Math.abs(expected)));
            }
        }
    }

    // ---- combined with TornadoVM JIT task graphs ----

    /** Each rank squares its input on the GPU (JIT), then allReduce SUM gives the global sum of squares. */
    public static void square(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * in.get(i));
        }
    }

    @Test
    public void testCombinedSumOfSquares() throws TornadoExecutionPlanException {
        int count = 1024;
        FloatArray[] x = randomRanks(count);
        FloatArray[] sq = emptyRanks(count);

        // Per-rank TornadoVM task graph produces the squared contribution on-device.
        for (int r = 0; r < RANKS; r++) {
            TaskGraph g = new TaskGraph("sq" + r) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x[r]) //
                    .task("square", TestNccl::square, x[r], sq[r]) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sq[r]);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
                plan.execute();
            }
        }

        FloatArray[] result = emptyRanks(count);
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            comm.allReduce(sq, result, NcclRedOp.SUM);
        }

        for (int i = 0; i < count; i++) {
            float expected = 0.0f;
            for (int r = 0; r < RANKS; r++) {
                expected += x[r].get(i) * x[r].get(i);
            }
            assertEquals(expected, result[0].get(i), 1e-3f * Math.max(1.0f, Math.abs(expected)));
        }
    }

    /** Reusing one communicator across many collectives stays correct. */
    @Test
    public void testRepeatedCollectives() {
        int count = 256;
        try (TornadoNccl comm = new TornadoNccl(DEVICES)) {
            for (int it = 0; it < 20; it++) {
                FloatArray[] send = randomRanks(count);
                FloatArray[] recv = emptyRanks(count);
                comm.allReduce(send, recv, NcclRedOp.SUM);
                for (int i = 0; i < count; i++) {
                    float expected = 0.0f;
                    for (int r = 0; r < RANKS; r++) {
                        expected += send[r].get(i);
                    }
                    assertEquals(expected, recv[0].get(i), 1e-4f * Math.max(1.0f, Math.abs(expected)));
                }
            }
        }
    }
}
