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
package uk.ac.manchester.tornado.unittests.memory;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.memory.HostMemoryType;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Arrays created in pinned (page-locked) and mapped (zero-copy) host memory with
 * {@code XArray.allocate(n, HostMemoryType)}. On the CUDA backend PINNED arrays are transferred without
 * being registered first and MAPPED arrays are used by kernels in place; on other backends the arrays
 * are ordinary host memory. Results must be the same everywhere.
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.memory.TestHostMemoryTypes
 * </pre>
 */
public class TestHostMemoryTypes extends TornadoTestBase {

    private static final int SIZE = 1 << 16;

    private static void saxpy(FloatArray x, FloatArray y, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, 2.0f * x.get(i) + y.get(i));
        }
    }

    private static void addInt(IntArray a, IntArray b, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) + b.get(i));
        }
    }

    private static void scaleDouble(DoubleArray a, DoubleArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) * 0.5);
        }
    }

    private static void addHalf(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, HalfFloat.add(a.get(i), b.get(i)));
        }
    }

    private static void increment(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1.0f);
        }
    }

    private static FloatArray floats(HostMemoryType type, float offset) {
        FloatArray array = FloatArray.allocate(SIZE, type);
        for (int i = 0; i < SIZE; i++) {
            array.set(i, i + offset);
        }
        return array;
    }

    private static void checkSaxpy(FloatArray x, FloatArray y, FloatArray out) {
        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, 2.0f * x.get(i) + y.get(i), out.get(i), 1e-3f);
        }
    }

    private void runSaxpy(HostMemoryType xType, HostMemoryType yType, HostMemoryType outType) throws TornadoExecutionPlanException {
        FloatArray x = floats(xType, 0);
        FloatArray y = floats(yType, 3);
        FloatArray out = FloatArray.allocate(SIZE, outType);
        assertEquals(SIZE, out.getSize());
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t0", TestHostMemoryTypes::saxpy, x, y, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }
        checkSaxpy(x, y, out);
    }

    @Test
    public void testPinnedFloat() throws TornadoExecutionPlanException {
        runSaxpy(HostMemoryType.PINNED, HostMemoryType.PINNED, HostMemoryType.PINNED);
    }

    @Test
    public void testMappedFloat() throws TornadoExecutionPlanException {
        runSaxpy(HostMemoryType.MAPPED, HostMemoryType.MAPPED, HostMemoryType.MAPPED);
    }

    @Test
    public void testPageableFactory() throws TornadoExecutionPlanException {
        runSaxpy(HostMemoryType.PAGEABLE, HostMemoryType.PAGEABLE, HostMemoryType.PAGEABLE);
    }

    /** One argument of each kind in the same task. */
    @Test
    public void testMixedHostMemoryTypes() throws TornadoExecutionPlanException {
        runSaxpy(HostMemoryType.PAGEABLE, HostMemoryType.PINNED, HostMemoryType.MAPPED);
        runSaxpy(HostMemoryType.MAPPED, HostMemoryType.PAGEABLE, HostMemoryType.PINNED);
    }

    @Test
    public void testIntDoubleHalf() throws TornadoExecutionPlanException {
        for (HostMemoryType type : HostMemoryType.values()) {
            IntArray a = IntArray.allocate(SIZE, type);
            IntArray b = IntArray.allocate(SIZE, type);
            IntArray c = IntArray.allocate(SIZE, type);
            DoubleArray d = DoubleArray.allocate(SIZE, type);
            DoubleArray e = DoubleArray.allocate(SIZE, type);
            HalfFloatArray h1 = HalfFloatArray.allocate(SIZE, type);
            HalfFloatArray h2 = HalfFloatArray.allocate(SIZE, type);
            HalfFloatArray h3 = HalfFloatArray.allocate(SIZE, type);
            for (int i = 0; i < SIZE; i++) {
                a.set(i, i);
                b.set(i, 7 * i);
                d.set(i, i * 3.0);
                h1.set(i, new HalfFloat(i % 64));
                h2.set(i, new HalfFloat(1.5f));
            }
            TaskGraph taskGraph = new TaskGraph("types") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, d, h1, h2) //
                    .task("int", TestHostMemoryTypes::addInt, a, b, c) //
                    .task("double", TestHostMemoryTypes::scaleDouble, d, e) //
                    .task("half", TestHostMemoryTypes::addHalf, h1, h2, h3) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c, e, h3);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
            for (int i = 0; i < SIZE; i++) {
                assertEquals(type + " int " + i, 8 * i, c.get(i));
                assertEquals(type + " double " + i, i * 1.5, e.get(i), 1e-9);
                assertEquals(type + " half " + i, (i % 64) + 1.5f, h3.get(i).getFloat32(), 1e-2f);
            }
        }
    }

    /**
     * A mapped array is shared with the device, not copied: host writes made between executions are
     * seen by the kernel even though the array is only transferred on the first execution.
     */
    @Test
    public void testMappedIsCoherentAcrossExecutions() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray a = floats(HostMemoryType.MAPPED, 0);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHostMemoryTypes::increment, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(i + 1.0f, a.get(i), 0.0f);
                a.set(i, 100.0f);                 // not transferred again
            }
            plan.execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(101.0f, a.get(i), 0.0f);
        }
    }

    /** Two tasks exchange data through a mapped array; the result is repeated across executions. */
    @Test
    public void testMappedChainRepeated() throws TornadoExecutionPlanException {
        FloatArray x = floats(HostMemoryType.MAPPED, 0);
        FloatArray tmp = FloatArray.allocate(SIZE, HostMemoryType.MAPPED);
        FloatArray out = FloatArray.allocate(SIZE, HostMemoryType.PINNED);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t0", TestHostMemoryTypes::saxpy, x, x, tmp) //
                .task("t1", TestHostMemoryTypes::saxpy, tmp, x, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int iteration = 0; iteration < 3; iteration++) {
                for (int i = 0; i < SIZE; i++) {
                    x.set(i, i + iteration);
                }
                plan.execute();
                for (int i = 0; i < SIZE; i++) {
                    float v = i + iteration;
                    assertEquals("iteration " + iteration + " element " + i, 2.0f * (3.0f * v) + v, out.get(i), 1e-2f);
                }
            }
        }
    }

    @Test
    public void testMappedAndPinnedUnderCUDAGraph() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray x = floats(HostMemoryType.MAPPED, 0);
        FloatArray y = floats(HostMemoryType.PINNED, 1);
        FloatArray out = FloatArray.allocate(SIZE, HostMemoryType.MAPPED);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t0", TestHostMemoryTypes::saxpy, x, y, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAGraph();
            for (int iteration = 0; iteration < 4; iteration++) {
                for (int i = 0; i < SIZE; i++) {
                    x.set(i, i * iteration);
                    y.set(i, iteration);
                }
                plan.execute();
                checkSaxpy(x, y, out);
            }
        }
    }

    /** Page-locked arrays are freed when unreachable: allocating far more than fits at once must work. */
    @Test
    public void testManyShortLivedPinnedArrays() throws TornadoExecutionPlanException {
        for (int round = 0; round < 64; round++) {
            HostMemoryType type = round % 2 == 0 ? HostMemoryType.PINNED : HostMemoryType.MAPPED;
            FloatArray a = FloatArray.allocate(16 << 20, type);   // 64 MB each, 4 GB in total
            a.set(0, round);
            a.set(a.getSize() - 1, round);
            if (round % 16 == 0) {
                TaskGraph taskGraph = new TaskGraph("s" + round) //
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                        .task("t0", TestHostMemoryTypes::increment, a) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
                try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                    plan.execute();
                }
                assertEquals(round + 1.0f, a.get(0), 0.0f);
            }
            if (round % 8 == 7) {
                System.gc();
            }
        }
    }
}
