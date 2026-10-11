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
package uk.ac.manchester.tornado.unittests.multithreaded;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Sixteen kernels with 2 to 9 parameters, first compiled at the same moment from sixteen threads.
 * Their sketches are built concurrently, and each must keep its own argument accesses: when they
 * went through a shared static field, a kernel could get another kernel's (a 6-parameter task
 * handed a 9-parameter access array failed in compileTask with an ArrayIndexOutOfBoundsException).
 * The race needs the sketches to overlap, so a broken build can pass this test by luck; a fixed
 * one does not fail it by luck.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 * $ tornado-test -V uk.ac.manchester.tornado.unittests.multithreaded.TestConcurrentSketches
 * </code>
 */
public class TestConcurrentSketches extends TornadoTestBase {

    private static final int N = 1024;
    private static final String[] NAMES = { "k2a", "k3a", "k4a", "k5a", "k6a", "k7a", "k8a", "k9a", "k2b", "k3b", "k4b", "k5b", "k6b", "k7b", "k8b", "k9b" };
    private static final int[] ARITY = { 2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int[] FACTOR = { 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2 };

    private static void k2a(IntArray a0, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i)));
        }
    }

    private static void k3a(IntArray a0, IntArray a1, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i)));
        }
    }

    private static void k4a(IntArray a0, IntArray a1, IntArray a2, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i)));
        }
    }

    private static void k5a(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i)));
        }
    }

    private static void k6a(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i)));
        }
    }

    private static void k7a(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i)));
        }
    }

    private static void k8a(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray a6, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i) + a6.get(i)));
        }
    }

    private static void k9a(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray a6, IntArray a7, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i) + a6.get(i) + a7.get(i)));
        }
    }

    private static void k2b(IntArray a0, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i)) * 2);
        }
    }

    private static void k3b(IntArray a0, IntArray a1, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i)) * 2);
        }
    }

    private static void k4b(IntArray a0, IntArray a1, IntArray a2, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i)) * 2);
        }
    }

    private static void k5b(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i)) * 2);
        }
    }

    private static void k6b(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i)) * 2);
        }
    }

    private static void k7b(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i)) * 2);
        }
    }

    private static void k8b(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray a6, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i) + a6.get(i)) * 2);
        }
    }

    private static void k9b(IntArray a0, IntArray a1, IntArray a2, IntArray a3, IntArray a4, IntArray a5, IntArray a6, IntArray a7, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (a0.get(i) + a1.get(i) + a2.get(i) + a3.get(i) + a4.get(i) + a5.get(i) + a6.get(i) + a7.get(i)) * 2);
        }
    }

    private static void build(TaskGraph graph, String name, IntArray[] in, IntArray out) {
        switch (name) {
            case "k2a" -> graph.task("t", TestConcurrentSketches::k2a, in[0], out);
            case "k3a" -> graph.task("t", TestConcurrentSketches::k3a, in[0], in[1], out);
            case "k4a" -> graph.task("t", TestConcurrentSketches::k4a, in[0], in[1], in[2], out);
            case "k5a" -> graph.task("t", TestConcurrentSketches::k5a, in[0], in[1], in[2], in[3], out);
            case "k6a" -> graph.task("t", TestConcurrentSketches::k6a, in[0], in[1], in[2], in[3], in[4], out);
            case "k7a" -> graph.task("t", TestConcurrentSketches::k7a, in[0], in[1], in[2], in[3], in[4], in[5], out);
            case "k8a" -> graph.task("t", TestConcurrentSketches::k8a, in[0], in[1], in[2], in[3], in[4], in[5], in[6], out);
            case "k9a" -> graph.task("t", TestConcurrentSketches::k9a, in[0], in[1], in[2], in[3], in[4], in[5], in[6], in[7], out);
            case "k2b" -> graph.task("t", TestConcurrentSketches::k2b, in[0], out);
            case "k3b" -> graph.task("t", TestConcurrentSketches::k3b, in[0], in[1], out);
            case "k4b" -> graph.task("t", TestConcurrentSketches::k4b, in[0], in[1], in[2], out);
            case "k5b" -> graph.task("t", TestConcurrentSketches::k5b, in[0], in[1], in[2], in[3], out);
            case "k6b" -> graph.task("t", TestConcurrentSketches::k6b, in[0], in[1], in[2], in[3], in[4], out);
            case "k7b" -> graph.task("t", TestConcurrentSketches::k7b, in[0], in[1], in[2], in[3], in[4], in[5], out);
            case "k8b" -> graph.task("t", TestConcurrentSketches::k8b, in[0], in[1], in[2], in[3], in[4], in[5], in[6], out);
            case "k9b" -> graph.task("t", TestConcurrentSketches::k9b, in[0], in[1], in[2], in[3], in[4], in[5], in[6], in[7], out);
            default -> throw new IllegalArgumentException(name);
        }
    }

    @Test
    public void testSixteenKernelsSketchedAtOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(NAMES.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IntArray>> results = new ArrayList<>();
        for (int k = 0; k < NAMES.length; k++) {
            final int kernel = k;
            results.add(pool.submit(() -> {
                IntArray[] in = new IntArray[ARITY[kernel] - 1];
                for (int a = 0; a < in.length; a++) {
                    in[a] = new IntArray(N);
                    in[a].init(a + 1);
                }
                IntArray out = new IntArray(N);
                TaskGraph graph = new TaskGraph("s" + kernel).transferToDevice(DataTransferMode.EVERY_EXECUTION, (Object[]) in);
                build(graph, NAMES[kernel], in, out);
                graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out);
                start.await();
                try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                    plan.execute();
                }
                return out;
            }));
        }
        start.countDown();
        try {
            for (int k = 0; k < NAMES.length; k++) {
                IntArray out = results.get(k).get();
                int params = ARITY[k] - 1;
                int expected = FACTOR[k] * params * (params + 1) / 2;
                for (int i = 0; i < N; i++) {
                    assertEquals(NAMES[k] + " element " + i, expected, out.get(i));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
