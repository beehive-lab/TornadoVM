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
package uk.ac.manchester.tornado.unittests.prebuilt;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A graph whose task list mixes a JIT-compiled task with a {@code prebuiltTask} has to produce the
 * same result whichever order the two are declared in. A prebuilt task that silently writes nothing
 * because a JIT task was declared before it is the worst shape of failure: no exception, no warning,
 * and a zero-filled output that a caller cannot distinguish from a legitimate result.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.prebuilt.TestPrebuiltTaskOrdering
 * </code>
 */
public class TestPrebuiltTaskOrdering extends TornadoTestBase {

    private static final int SIZE = 8;

    private static TornadoDevice defaultDevice;
    private static TornadoVMBackendType backendType;
    private static boolean coops;

    @BeforeClass
    public static void init() {
        backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(0);
        defaultDevice = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        coops = TornadoNativeArray.ARRAY_HEADER == 16;
    }

    private static String addKernelPath() {
        String basePath = System.getenv("TORNADOVM_HOME") + "/examples/generated/";
        String fileStem = coops ? "add" : "add_uncompressed";
        String extension = switch (backendType) {
            case OPENCL -> ".cl";
            case METAL -> ".metal";
            case CUDA -> ".cu";
            default -> throw new TornadoRuntimeException("Backend not supported");
        };
        return basePath + fileStem + extension;
    }

    /** Writes into a buffer the prebuilt kernel does not read. */
    public static void bias(IntArray unrelated) {
        for (@Parallel int i = 0; i < unrelated.getSize(); i++) {
            unrelated.set(i, unrelated.get(i) + 100);
        }
    }

    /** Consumes the prebuilt kernel's output. */
    public static void doubleIt(IntArray c, IntArray d) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            d.set(i, c.get(i) * 2);
        }
    }

    private static AccessorParameters addAccessors(IntArray a, IntArray b, IntArray c) {
        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_ONLY);
        accessorParameters.set(1, b, Access.READ_ONLY);
        accessorParameters.set(2, c, Access.WRITE_ONLY);
        return accessorParameters;
    }

    /** The prebuilt task alone: the control. */
    @Test
    public void testPrebuiltAlone() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(SIZE);
        IntArray b = new IntArray(SIZE);
        IntArray c = new IntArray(SIZE);
        a.init(1);
        b.init(2);
        c.init(0);

        TaskGraph taskGraph = new TaskGraph("solo") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .prebuiltTask("p", "add", addKernelPath(), addAccessors(a, b, c)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        GridScheduler gridScheduler = new GridScheduler("solo.p", new WorkerGrid1D(SIZE));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).withDevice(defaultDevice).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(3, c.get(i));
        }
    }

    /** Prebuilt first, then a JIT task that consumes its output: known to work. */
    @Test
    public void testPrebuiltThenJit() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(SIZE);
        IntArray b = new IntArray(SIZE);
        IntArray c = new IntArray(SIZE);
        IntArray d = new IntArray(SIZE);
        a.init(1);
        b.init(2);
        c.init(0);
        d.init(0);

        TaskGraph taskGraph = new TaskGraph("post") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .prebuiltTask("p", "add", addKernelPath(), addAccessors(a, b, c)) //
                .task("j", TestPrebuiltTaskOrdering::doubleIt, c, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, d);

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        GridScheduler gridScheduler = new GridScheduler("post.p", worker);
        gridScheduler.addWorkerGrid("post.j", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).withDevice(defaultDevice).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(3, c.get(i));
            assertEquals(6, d.get(i));
        }
    }

    /** A JIT task on an unrelated buffer declared first, then the prebuilt task. */
    @Test
    public void testJitOnUnrelatedBufferThenPrebuilt() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(SIZE);
        IntArray b = new IntArray(SIZE);
        IntArray c = new IntArray(SIZE);
        IntArray unrelated = new IntArray(SIZE);
        a.init(1);
        b.init(2);
        c.init(0);
        unrelated.init(1);

        TaskGraph taskGraph = new TaskGraph("preIndep") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, unrelated) //
                .task("j", TestPrebuiltTaskOrdering::bias, unrelated) //
                .prebuiltTask("p", "add", addKernelPath(), addAccessors(a, b, c)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, unrelated);

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        GridScheduler gridScheduler = new GridScheduler("preIndep.p", worker);
        gridScheduler.addWorkerGrid("preIndep.j", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).withDevice(defaultDevice).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals("the JIT task ran", 101, unrelated.get(i));
            assertEquals("the prebuilt task ran", 3, c.get(i));
        }
    }

    /** A JIT task that writes one of the prebuilt kernel's inputs, then the prebuilt task. */
    @Test
    public void testJitWritingPrebuiltInputThenPrebuilt() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(SIZE);
        IntArray b = new IntArray(SIZE);
        IntArray c = new IntArray(SIZE);
        a.init(1);
        b.init(1);
        c.init(0);

        TaskGraph taskGraph = new TaskGraph("pre") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("j", TestPrebuiltTaskOrdering::bias, b) //
                .prebuiltTask("p", "add", addKernelPath(), addAccessors(a, b, c)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, b);

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        GridScheduler gridScheduler = new GridScheduler("pre.p", worker);
        gridScheduler.addWorkerGrid("pre.j", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).withDevice(defaultDevice).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals("the JIT task ran", 101, b.get(i));
            assertEquals("the prebuilt task saw the JIT task's result", 102, c.get(i));
        }
    }
}
