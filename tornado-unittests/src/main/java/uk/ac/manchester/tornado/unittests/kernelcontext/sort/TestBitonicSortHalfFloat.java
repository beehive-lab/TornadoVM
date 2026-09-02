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
package uk.ac.manchester.tornado.unittests.kernelcontext.sort;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link TestBitonicSort}'s local-memory compare-exchange network, keyed on {@link HalfFloat}
 * values instead of {@code int} -- fp16 comparisons/tie behavior are untested territory elsewhere
 * in the suite. Comparisons happen through {@code HalfFloat.getFloat32()} (the fp16->fp32
 * conversion is exactly monotonic, so ordering by the fp32 view matches ordering by the fp16 bit
 * pattern); the compare-exchange itself works over a local {@code float[]}, since
 * {@code KernelContext} has no {@code HalfFloat}-typed local-array allocator.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.sort.TestBitonicSortHalfFloat
 * </code>
 */
public class TestBitonicSortHalfFloat extends TornadoTestBase {

    private static final int SIZE = 256; // power of 2, fits one work-group

    private static void bitonicSortHalfFloat(KernelContext ctx, HalfFloatArray data) {
        float[] shared = ctx.allocateFloatLocalArray(SIZE);
        int tid = ctx.localIdx;
        shared[tid] = data.get(ctx.globalIdx).getFloat32();
        ctx.localBarrier();

        for (int k = 2; k <= SIZE; k <<= 1) {
            for (int j = k >> 1; j > 0; j >>= 1) {
                int partner = tid ^ j;
                if (partner > tid) {
                    boolean ascending = (tid & k) == 0;
                    float a = shared[tid];
                    float b = shared[partner];
                    if (ascending == (a > b)) {
                        shared[tid] = b;
                        shared[partner] = a;
                    }
                }
                ctx.localBarrier();
            }
        }

        data.set(ctx.globalIdx, new HalfFloat(shared[tid]));
    }

    @Test
    public void testBitonicSortHalfFloatSingleBlock() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        Random r = new Random(43);
        HalfFloatArray data = new HalfFloatArray(SIZE);
        float[] expected = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            HalfFloat value = new HalfFloat(r.nextFloat() * 1000.0f);
            data.set(i, value);
            expected[i] = value.getFloat32();
        }
        Arrays.sort(expected);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestBitonicSortHalfFloat::bitonicSortHalfFloat, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("mismatch at index " + i, expected[i], data.get(i).getFloat32(), 0.001f);
        }
        for (int i = 0; i < SIZE - 1; i++) {
            assertTrue("not sorted at index " + i, data.get(i).getFloat32() <= data.get(i + 1).getFloat32());
        }
    }

}
