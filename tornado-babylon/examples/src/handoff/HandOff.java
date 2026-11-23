/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package handoff;

import static hat.KernelContext.GIX;

import java.lang.invoke.MethodHandles;

import hat.Accelerator;
import hat.ComputeContext;
import hat.NDRange;
import hat.backend.Backend;
import hat.buffer.F32Array;
import jdk.incubator.code.Reflect;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.babylon.hat.HatTasks;

/**
 * Two kernels that hand two buffers to each other, step after step: {@code forward} reads {@code a}
 * and writes {@code b}, {@code backward} reads {@code b} and writes {@code a}. The host looks at
 * nothing until the end, so nothing has to leave the device in between. Run as a HAT compute method
 * (one dispatch per kernel per step) and, for reference, as one TornadoVM task graph of the two
 * kernels; both are checked against Java. (Not for {@code HAT=MC}: the program reads and refills the
 * buffers between compute calls without telling HAT.)
 *
 * <pre>
 * ./run.sh -d 1:0 build/examples handoff.HandOff [elements] [steps]
 * </pre>
 */
public class HandOff {

    @Reflect
    public static void forward(F32Array a, F32Array b) {
        b.array(GIX(), a.array(GIX()) + 1f);
    }

    @Reflect
    public static void backward(F32Array a, F32Array b) {
        a.array(GIX(), b.array(GIX()) + 2f);
    }

    @Reflect
    public static void steps(ComputeContext cc, F32Array a, F32Array b, int length, int steps) {
        for (int step = 0; step < steps; step++) {
            cc.dispatchKernel(NDRange.of1D(length), () -> forward(a, b));
            cc.dispatchKernel(NDRange.of1D(length), () -> backward(a, b));
        }
    }

    private static void fill(F32Array a, int n) {
        for (int i = 0; i < n; i++) {
            a.array(i, i % 1000);
        }
    }

    private static boolean check(F32Array a, int n, int steps) {
        for (int i = 0; i < n; i += Math.max(1, n / 4096)) {
            float expected = i % 1000 + 3f * steps; // every step counts: a lost or repeated one shows
            if (a.array(i) != expected) {
                System.out.println("WRONG: a[" + i + "] = " + a.array(i) + ", Java gives " + expected);
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1 << 22;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
        var a = F32Array.create(accelerator, n);
        var b = F32Array.create(accelerator, n);
        boolean ok = true;

        double best = Double.MAX_VALUE;
        for (int round = 0; round < 6; round++) {
            fill(a, n);
            long start = System.nanoTime();
            accelerator.compute((Accelerator.@Reflect Compute) cc -> steps(cc, a, b, n, steps));
            double ms = (System.nanoTime() - start) / 1e6;
            if (round > 0) {
                best = Math.min(best, ms);
            }
            ok &= check(a, n, steps);
        }
        System.out.printf("HAT compute, 2 dispatches per step:   %8.3f ms per step%n", best / steps);

        var hat = HatTasks.on(accelerator);
        var graph = new TaskGraph("handoff").transferToDevice(DataTransferMode.EVERY_EXECUTION, hat.of(a));
        for (int step = 0; step < steps; step++) {
            hat.task(graph, "forward" + step, HandOff::forward, a, b);
            hat.task(graph, "backward" + step, HandOff::backward, a, b);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(a));
        var grid = new GridScheduler();
        for (int step = 0; step < steps; step++) {
            grid.addWorkerGrid("handoff.forward" + step, new WorkerGrid1D(n));
            grid.addWorkerGrid("handoff.backward" + step, new WorkerGrid1D(n));
        }
        String[] d = System.getProperty("hat.t.device", "0:0").split(":");
        try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice(TornadoExecutionPlan.getDevice(Integer.parseInt(d[0]), Integer.parseInt(d[1]))).withGridScheduler(grid);
            best = Double.MAX_VALUE;
            for (int round = 0; round < 6; round++) {
                fill(a, n);
                long start = System.nanoTime();
                plan.execute();
                double ms = (System.nanoTime() - start) / 1e6;
                if (round > 0) {
                    best = Math.min(best, ms);
                }
                ok &= check(a, n, steps);
            }
        }
        System.out.printf("one TornadoVM task graph of the steps: %7.3f ms per step%n", best / steps);
        System.out.println(ok ? "check: both equal Java's" : "check: FAIL");
        System.exit(ok ? 0 : 1);
    }
}
