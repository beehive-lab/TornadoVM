/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz.gen;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.fuzz.DeviceSelector;

/**
 * Phase 2 de-risking spike. Generates ONE trivial KernelContext kernel as Java
 * source, compiles it in-process, and runs it on the CUDA backend against a host
 * reference. This validates the load-bearing assumption of the generative phase:
 * that TornadoVM's Graal bytecode reader can JIT a dynamically-compiled class.
 *
 * The generated class exposes a {@code task()} factory that returns a method
 * reference to its own static kernel, so the lambda's defining classloader is the
 * child loader that can resolve the kernel (Graal reads the lambda's INVOKESTATIC
 * via that loader's constant pool).
 *
 * Run: tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.gen.Phase2Spike
 */
public final class Phase2Spike {

    private static final String FQCN = "uk.ac.manchester.tornado.fuzz.generated.G1";

    private static final String SOURCE = """
            package uk.ac.manchester.tornado.fuzz.generated;

            import uk.ac.manchester.tornado.api.KernelContext;
            import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
            import uk.ac.manchester.tornado.api.types.arrays.IntArray;

            public final class G1 {
                public static void fuzzedKernel(KernelContext c, IntArray a, IntArray b, IntArray out) {
                    int i = c.globalIdx;
                    out.set(i, (a.get(i) * 3) ^ (b.get(i) + 7));
                }

                public static Task4<KernelContext, IntArray, IntArray, IntArray> task() {
                    return G1::fuzzedKernel;
                }
            }
            """;

    private Phase2Spike() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        TornadoDevice device = DeviceSelector.cudaDevice();
        System.out.println("CUDA device: " + device.getDeviceName());

        InProcessCompiler compiler = new InProcessCompiler();
        Class<?> generated = compiler.compileAndLoad(FQCN, SOURCE);
        System.out.println("Compiled + loaded: " + generated.getName() + " via " + generated.getClassLoader());

        Task4<KernelContext, IntArray, IntArray, IntArray> task = (Task4<KernelContext, IntArray, IntArray, IntArray>) generated.getMethod("task").invoke(null);

        int size = 1024;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray out = new IntArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, i);
            b.set(i, size - i);
        }

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(64, 1, 1);
        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("fuzz.t0", worker);

        TaskGraph tg = new TaskGraph("fuzz") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", task, context, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(scheduler).withDevice(device).execute();
        }

        int bad = -1;
        for (int i = 0; i < size; i++) {
            int expected = (a.get(i) * 3) ^ (b.get(i) + 7);
            if (out.get(i) != expected) {
                bad = i;
                break;
            }
        }
        if (bad < 0) {
            System.out.println("SPIKE PASS: dynamically-compiled kernel JIT'd and executed correctly on CUDA.");
        } else {
            System.out.println("SPIKE MISMATCH at index " + bad + ": expected=" + ((a.get(bad) * 3) ^ (b.get(bad) + 7)) + " actual=" + out.get(bad));
        }
    }
}
