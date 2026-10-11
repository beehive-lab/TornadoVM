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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.api.types.vectors.Half4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Vector-width loads from native arrays: {@link FloatArray#getFloat4}, {@link HalfFloatArray#getHalf4} and
 * {@link HalfFloatArray#getHalf2}, each lane weighted differently so that a lane swap or a wrong stride shows.
 *
 * <p>How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestNativeArrayVectorLoads
 * </code>
 */
public class TestNativeArrayVectorLoads extends TornadoTestBase {

    private static final int N = 1000;
    private static final int LOCAL = 64;

    private static void float4Kernel(KernelContext context, FloatArray input, FloatArray output, int n) {
        int i = context.globalIdx;
        if (i < n) {
            Float4 v = input.getFloat4(i * 4);
            output.set(i, v.getX() + 2.0f * v.getY() + 3.0f * v.getZ() + 4.0f * v.getW());
        }
    }

    private static void half4Kernel(KernelContext context, HalfFloatArray input, FloatArray output, int n) {
        int i = context.globalIdx;
        if (i < n) {
            Half4 v = input.getHalf4(i * 4);
            output.set(i, v.getX().getFloat32() + 2.0f * v.getY().getFloat32() + 3.0f * v.getZ().getFloat32() + 4.0f * v.getW().getFloat32());
        }
    }

    private static void half2Kernel(KernelContext context, HalfFloatArray input, FloatArray output, int n) {
        int i = context.globalIdx;
        if (i < n) {
            Half2 v = input.getHalf2(i * 2);
            output.set(i, v.getX().getFloat32() + 2.0f * v.getY().getFloat32());
        }
    }

    private static GridScheduler scheduler(String name) {
        WorkerGrid1D worker = new WorkerGrid1D(((N + LOCAL - 1) / LOCAL) * LOCAL);
        worker.setLocalWork(LOCAL, 1, 1);
        return new GridScheduler(name + ".t0", worker);
    }

    private static void run(TaskGraph taskGraph, String name) throws TornadoExecutionPlanException {
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler(name)).execute();
        }
    }

    private static float value(int i) {
        return ((i * 7) % 23 - 11) * 0.125f;
    }

    @Test
    public void testFloat4() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(N * 4);
        for (int i = 0; i < input.getSize(); i++) {
            input.set(i, value(i));
        }
        FloatArray output = new FloatArray(N);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestNativeArrayVectorLoads::float4Kernel, new KernelContext(), input, output, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, "s0");
        for (int i = 0; i < N; i++) {
            float expected = input.get(4 * i) + 2.0f * input.get(4 * i + 1) + 3.0f * input.get(4 * i + 2) + 4.0f * input.get(4 * i + 3);
            assertEquals("element " + i, expected, output.get(i), 0.0f);
        }
    }

    @Test
    public void testHalf4() throws TornadoExecutionPlanException {
        HalfFloatArray input = new HalfFloatArray(N * 4);
        for (int i = 0; i < input.getSize(); i++) {
            input.set(i, new HalfFloat(value(i)));
        }
        FloatArray output = new FloatArray(N);
        TaskGraph taskGraph = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestNativeArrayVectorLoads::half4Kernel, new KernelContext(), input, output, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, "s1");
        for (int i = 0; i < N; i++) {
            float expected = input.get(4 * i).getFloat32() + 2.0f * input.get(4 * i + 1).getFloat32() + 3.0f * input.get(4 * i + 2).getFloat32() + 4.0f * input.get(4 * i + 3).getFloat32();
            assertEquals("element " + i, expected, output.get(i), 0.0f);
        }
    }

    @Test
    public void testHalf2() throws TornadoExecutionPlanException {
        HalfFloatArray input = new HalfFloatArray(N * 2);
        for (int i = 0; i < input.getSize(); i++) {
            input.set(i, new HalfFloat(value(i)));
        }
        FloatArray output = new FloatArray(N);
        TaskGraph taskGraph = new TaskGraph("s2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestNativeArrayVectorLoads::half2Kernel, new KernelContext(), input, output, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, "s2");
        for (int i = 0; i < N; i++) {
            float expected = input.get(2 * i).getFloat32() + 2.0f * input.get(2 * i + 1).getFloat32();
            assertEquals("element " + i, expected, output.get(i), 0.0f);
        }
    }
}
