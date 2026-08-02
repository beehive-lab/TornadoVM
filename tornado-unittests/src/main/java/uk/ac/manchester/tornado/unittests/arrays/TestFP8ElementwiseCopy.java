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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Every existing FP8 test in the suite ({@code TestMatrixMultiplicationMMAFP8}) exercises
 * {@link FP8Array} only through the MMA fragment-load path (M16N8K32 GEMM), which is
 * compute-capability-gated to Ada/Hopper (>= 8.9) and self-skips as "Unsupported" on this
 * machine's Ampere RTX 3070 (8.6). This checks whether plain {@code FP8Array} element access
 * (raw byte get/set, a plain elementwise device-to-device copy -- no {@code mma*} call involved)
 * needs the same gating, or works generally since it's just moving bytes. Host-side value
 * verification uses {@code getE4M3}/{@code setE4M3} (pure-Java fp8<->float32 conversion, not
 * itself device code).
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestFP8ElementwiseCopy
 * </code>
 */
public class TestFP8ElementwiseCopy extends TornadoTestBase {

    private static void fp8Copy(FP8Array in, FP8Array out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i));
        }
    }

    @Test
    public void testFP8ElementwiseCopyOutsideMMA() throws TornadoExecutionPlanException {
        final int size = 64;
        FP8Array in = new FP8Array(size);
        FP8Array out = new FP8Array(size);

        Random r = new Random(31);
        float[] expected = new float[size];
        for (int i = 0; i < size; i++) {
            float value = (r.nextFloat() - 0.5f) * 8.0f; // within E4M3's representable range
            in.setE4M3(i, value);
            expected[i] = in.getE4M3(i); // round-tripped through fp8 already, this is the true expected value
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestFP8ElementwiseCopy::fp8Copy, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals("mismatch at index " + i, expected[i], out.getE4M3(i), 0.001f);
        }
    }

}
