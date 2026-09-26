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
package uk.ac.manchester.tornado.examples.tornadoinsight;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * RecursionExample: one kernel with three constructs a GPU cannot run, and the same kernel fixed.
 *
 * <p>Open this file in IntelliJ with the TornadoInsight plugin installed. The static checker flags the
 * {@code String}, the {@code throw} and the recursive call in {@link #broken}. Fix them, then select
 * {@link #fixed} in the TornadoInsight window to run it on the GPU, without needing the {@code main} below.
 *
 * <p>Run from the command line (only {@link #fixed} is executed):
 * <pre>
 *   tornado --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.tornadoinsight.RecursionExample 1024
 * </pre>
 */
public class RecursionExample {

    // ---------------------------------------------------------------- the broken kernel

    /** Three faults for TornadoInsight to find: a data type, an exception and recursion. */
    public static void broken(FloatArray in, FloatArray out, int n) {
        for (@Parallel int i = 0; i < n; i++) {
            String s = "not on a GPU";          // a data type
            if (in.get(i) < 0) {
                throw new RuntimeException();   // an exception
            }
            out.set(i, recurse(in.get(i)));     // recursion
        }
    }

    /**
     * Recursive: counts how many times x can be halved before it drops below 1, plus the remainder.
     * GPU kernels cannot recurse, so TornadoVM cannot compile this into a kernel.
     */
    static float recurse(float x) {
        if (x < 1.0f) {
            return x;
        }
        return recurse(x / 2.0f) + 1.0f;
    }

    // ---------------------------------------------------------------- the fixed kernel

    /** The same calculation as {@link #recurse}, written as a loop. TornadoVM inlines it into the kernel. */
    static float halvings(float x) {
        float count = 0.0f;
        // Same as x >= 1.0f (0.99999994f is the largest float below 1), written with > because
        // the Metal backend has no isgreaterequal helper
        while (x > 0.99999994f) {
            x /= 2.0f;
            count += 1.0f;
        }
        return x + count;
    }

    /** No String, no throw (negative inputs map to 0), no recursion. */
    public static void fixed(FloatArray in, FloatArray out, int n) {
        for (@Parallel int i = 0; i < n; i++) {
            float x = in.get(i);
            out.set(i, x < 0 ? 0.0f : halvings(x));
        }
    }

    // ---------------------------------------------------------------- run the fixed kernel

    public static void main(String[] args) throws TornadoExecutionPlanException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1024;

        FloatArray in = new FloatArray(n);
        FloatArray out = new FloatArray(n);
        FloatArray expected = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, i - 100.0f);   // includes negative values on purpose
        }

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in)
                .task("t0", RecursionExample::fixed, in, out, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Reference: the same method, run as plain Java
        fixed(in, expected, n);
        for (int i = 0; i < n; i++) {
            if (Math.abs(out.get(i) - expected.get(i)) > 1e-5f) {
                System.out.println("Result is wrong at index " + i + ": " + out.get(i) + " vs " + expected.get(i));
                return;
            }
        }
        System.out.println("Result is correct for " + n + " elements.");
    }
}