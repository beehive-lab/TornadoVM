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
package hattasks;

import static hat.KernelContext.GIX;
import static hat.KernelContext.GIY;

import java.lang.invoke.MethodHandles;

import hat.Accelerator;
import hat.backend.Backend;
import hat.buffer.F32Array;
import hat.buffer.S32Array;
import jdk.incubator.code.Reflect;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.babylon.hat.HatTasks;

/**
 * Checks of HAT kernels in TornadoVM task graphs ({@link HatTasks}): buffers of two HAT types shared
 * by tasks, a 2D kernel, host updates between executions, one buffer in two plans, CUDA graphs, and
 * clear errors on misuse. Each check prints PASS or FAIL; the exit status is the number of failures.
 *
 * <pre>
 * ./run.sh -d 1:0 build/examples hattasks.HatTasksChecks
 * </pre>
 */
public class HatTasksChecks {

    @Reflect
    public static void scale(S32Array in, F32Array out, float factor) {
        out.array(GIX(), in.array(GIX()) * factor);
    }

    @Reflect
    public static void addOne(F32Array data) {
        data.array(GIX(), data.array(GIX()) + 1f);
    }

    /** A 2D kernel: out[y][x] = x + 1000 y, over a width x height grid. */
    @Reflect
    public static void coordinates(F32Array out, int width) {
        out.array(GIY() * width + GIX(), GIX() + 1000f * GIY());
    }

    /** A plain TornadoVM kernel (no Babylon, no HAT): compiled from bytecode as usual. */
    public static void fill(uk.ac.manchester.tornado.api.KernelContext context, uk.ac.manchester.tornado.api.types.arrays.FloatArray a) {
        a.set(context.globalIdx, 7f);
    }

    public static void notReflected(F32Array data) {
        data.array(GIX(), 0f);
    }

    private static int failures;

    private static void check(String name, Runnable body) {
        try {
            body.run();
            System.out.printf("%-34s PASS%n", name);
        } catch (Throwable t) {
            failures++;
            System.out.printf("%-34s FAIL  %s%n", name, t.getMessage() == null ? t : t.getMessage().lines().findFirst().orElse(""));
        }
    }

    private static void expect(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
        String[] d = System.getProperty("hat.t.device", "0:0").split(":");
        TornadoDevice device = TornadoExecutionPlan.getDevice(Integer.parseInt(d[0]), Integer.parseInt(d[1]));
        boolean cuda = device.getTornadoVMBackend() == uk.ac.manchester.tornado.api.enums.TornadoVMBackendType.CUDA;
        var hat = HatTasks.on(accelerator);
        int n = 1024;

        check("two tasks, two buffer types", () -> {
            var in = S32Array.create(accelerator, n);
            var out = F32Array.create(accelerator, n);
            for (int i = 0; i < n; i++) {
                in.array(i, i);
            }
            var graph = new TaskGraph("two").transferToDevice(DataTransferMode.FIRST_EXECUTION, hat.of(in));
            hat.task(graph, "scale", HatTasksChecks::scale, in, out, 0.5f);
            hat.task(graph, "addOne", HatTasksChecks::addOne, out);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(out));
            var grid = new GridScheduler("two.scale", new WorkerGrid1D(n));
            grid.addWorkerGrid("two.addOne", new WorkerGrid1D(n));
            try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withDevice(device).withGridScheduler(grid).execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < n; i++) {
                expect(out.array(i) == i * 0.5f + 1f, "out[" + i + "] = " + out.array(i));
            }
        });

        check("2D kernel, 2D worker grid", () -> {
            int w = 64;
            int h = 32;
            var out = F32Array.create(accelerator, w * h);
            var graph = new TaskGraph("grid2d");
            hat.task(graph, "coordinates", HatTasksChecks::coordinates, out, w);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(out));
            try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withDevice(device).withGridScheduler(new GridScheduler("grid2d.coordinates", new WorkerGrid2D(w, h))).execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    expect(out.array(y * w + x) == x + 1000f * y, "out[" + y + "][" + x + "] = " + out.array(y * w + x));
                }
            }
        });

        check("host updates between executions", () -> {
            var data = F32Array.create(accelerator, n);
            var graph = new TaskGraph("updates").transferToDevice(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            hat.task(graph, "addOne", HatTasksChecks::addOne, data);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withDevice(device).withGridScheduler(new GridScheduler("updates.addOne", new WorkerGrid1D(n)));
                for (int round = 0; round < 3; round++) {
                    for (int i = 0; i < n; i++) {
                        data.array(i, 10f * round); // the HAT buffer, written by the host
                    }
                    plan.execute();
                    for (int i = 0; i < n; i++) {
                        expect(data.array(i) == 10f * round + 1f, "round " + round + ": data[" + i + "] = " + data.array(i));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        check("one buffer in two plans", () -> {
            var data = F32Array.create(accelerator, n);
            var first = new TaskGraph("planA").transferToDevice(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            hat.task(first, "addOne", HatTasksChecks::addOne, data);
            first.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            var second = new TaskGraph("planB").transferToDevice(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            hat.task(second, "addOne", HatTasksChecks::addOne, data);
            second.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(data));
            try (var a = new TornadoExecutionPlan(first.snapshot()); var b = new TornadoExecutionPlan(second.snapshot())) {
                a.withDevice(device).withGridScheduler(new GridScheduler("planA.addOne", new WorkerGrid1D(n)));
                b.withDevice(device).withGridScheduler(new GridScheduler("planB.addOne", new WorkerGrid1D(n)));
                a.execute();
                b.execute();
                a.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < n; i++) {
                expect(data.array(i) == 3f, "data[" + i + "] = " + data.array(i));
            }
        });

        if (cuda) {
            check("CUDA graph, 10 executions", () -> {
                var data = F32Array.create(accelerator, n);
                var graph = new TaskGraph("graphed").transferToDevice(DataTransferMode.FIRST_EXECUTION, hat.of(data));
                hat.task(graph, "addOne", HatTasksChecks::addOne, data);
                graph.transferToHost(DataTransferMode.UNDER_DEMAND, hat.of(data));
                try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                    plan.withDevice(device).withGridScheduler(new GridScheduler("graphed.addOne", new WorkerGrid1D(n))).withCUDAGraph();
                    uk.ac.manchester.tornado.api.TornadoExecutionResult result = null;
                    for (int r = 0; r < 10; r++) {
                        result = plan.execute();
                    }
                    result.transferToHost(hat.of(data));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < n; i++) {
                    expect(data.array(i) == 10f, "data[" + i + "] = " + data.array(i));
                }
            });
        }

        check("a TornadoVM task next to a HAT task", () -> {
            var plain = new uk.ac.manchester.tornado.api.types.arrays.FloatArray(n);
            var data = F32Array.create(accelerator, n);
            var graph = new TaskGraph("mixed").task("fill", HatTasksChecks::fill, new uk.ac.manchester.tornado.api.KernelContext(), plain);
            hat.task(graph, "addOne", HatTasksChecks::addOne, data);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, plain, hat.of(data));
            var grid = new GridScheduler("mixed.fill", new WorkerGrid1D(n));
            grid.addWorkerGrid("mixed.addOne", new WorkerGrid1D(n));
            try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withDevice(device).withGridScheduler(grid).execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < n; i++) {
                expect(plain.get(i) == 7f && data.array(i) == 1f, "i = " + i + ": " + plain.get(i) + ", " + data.array(i));
            }
        });

        check("same buffer, same TornadoVM object", () -> {
            var data = F32Array.create(accelerator, 16);
            expect(hat.of(data) == hat.of(data), "hat.of returned two objects for one buffer");
        });

        check("a buffer of another Accelerator", () -> {
            var other = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
            var foreign = F32Array.create(other, 16);
            try {
                hat.of(foreign);
                throw new AssertionError("no error");
            } catch (IllegalArgumentException expected) {
                expect(expected.getMessage().contains("not allocated by the Accelerator"), "message: " + expected.getMessage());
            }
        });

        check("a lambda, not a method reference", () -> {
            var data = F32Array.create(accelerator, 16);
            try {
                hat.task(new TaskGraph("bad"), "t", (F32Array x) -> addOne(x), data);
                throw new AssertionError("no error");
            } catch (IllegalArgumentException expected) {
                expect(expected.getMessage().contains("not as a lambda"), "message: " + expected.getMessage());
            }
        });

        check("a kernel without @Reflect", () -> {
            var data = F32Array.create(accelerator, 16);
            try {
                hat.task(new TaskGraph("plain"), "t", HatTasksChecks::notReflected, data);
                throw new AssertionError("no error");
            } catch (IllegalArgumentException expected) {
                expect(expected.getMessage().contains("annotate it @Reflect"), "message: " + expected.getMessage());
            }
        });

        System.out.println(failures == 0 ? "HatTasksChecks: all passed" : "HatTasksChecks: " + failures + " failed");
        System.exit(failures);
    }
}
