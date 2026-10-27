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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Parallel compilation (-Dtornado.parallel.compile, on by default): a plan starts compiling every
 * graph's kernels on background threads before its first execution (or in withPreCompilation), and
 * each task's in-order compilation takes the result. Each test checks that every kernel still does
 * what it does on the JVM: with several graphs run one by one, with pre-compilation, next to a
 * graph the prefetch leaves out (a @Reduce graph is rewritten on its first run), and with strict
 * floating point (the prefetched compilation must be the strict one). Compilations are recorded as
 * {@code tornado.Compile} JFR events.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestParallelCompilation
 * </code>
 */
public class TestParallelCompilation extends TornadoTestBase {

    private static final int N = 4096;

    public static void scale(FloatArray a, FloatArray out, float k) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * k);
        }
    }

    public static void shift(IntArray a, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, (a.get(i) << 3) ^ (a.get(i) >>> 2));
        }
    }

    public static void square(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * a.get(i) + 1.0f);
        }
    }

    public static void sum(IntArray a, @Reduce IntArray result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            result.set(0, result.get(0) + a.get(i));
        }
    }

    public static void edges(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * 3.0f + 0.1f);
        }
    }

    private final FloatArray f = new FloatArray(N);
    private final IntArray ints = new IntArray(N);
    private final FloatArray scaled = new FloatArray(N);
    private final FloatArray squared = new FloatArray(N);
    private final IntArray shifted = new IntArray(N);
    private final IntArray total = new IntArray(1);

    private ImmutableTaskGraph[] graphs(boolean withReduce) {
        for (int i = 0; i < N; i++) {
            f.set(i, i * 0.25f - 7);
            ints.set(i, i * 31 - 1000);
        }
        var g0 = new TaskGraph("g0").transferToDevice(DataTransferMode.EVERY_EXECUTION, f).task("t", TestParallelCompilation::scale, f, scaled, 1.5f)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, scaled);
        var g1 = new TaskGraph("g1").transferToDevice(DataTransferMode.EVERY_EXECUTION, ints).task("t", TestParallelCompilation::shift, ints, shifted)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, shifted);
        var g2 = new TaskGraph("g2").transferToDevice(DataTransferMode.EVERY_EXECUTION, f).task("t", TestParallelCompilation::square, f, squared)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, squared);
        if (!withReduce) {
            return new ImmutableTaskGraph[] { g0.snapshot(), g1.snapshot(), g2.snapshot() };
        }
        var g3 = new TaskGraph("g3").transferToDevice(DataTransferMode.EVERY_EXECUTION, ints).task("t", TestParallelCompilation::sum, ints, total)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, total);
        return new ImmutableTaskGraph[] { g0.snapshot(), g3.snapshot(), g1.snapshot(), g2.snapshot() };
    }

    private void check(boolean withReduce) {
        for (int i = 0; i < N; i++) {
            assertEquals(f.get(i) * 1.5f, scaled.get(i), 0.0f, "scale " + i);
            assertEquals((ints.get(i) << 3) ^ (ints.get(i) >>> 2), shifted.get(i), "shift " + i);
            assertEquals(f.get(i) * f.get(i) + 1.0f, squared.get(i), 0.0f, "square " + i);
        }
        if (withReduce) {
            int expected = 0;
            for (int i = 0; i < N; i++) {
                expected += ints.get(i);
            }
            assertEquals(expected, total.get(0), "sum");
        }
    }

    @Test
    public void graphsRunOneByOneOnTheFirstExecution() throws TornadoExecutionPlanException {
        var graphs = graphs(false);
        try (var plan = new TornadoExecutionPlan(graphs)) {
            for (int g = 0; g < graphs.length; g++) {
                plan.withGraph(g).execute();
            }
        }
        check(false);
    }

    @Test
    public void graphsWithPreCompilation() throws TornadoExecutionPlanException {
        var graphs = graphs(false);
        try (var plan = new TornadoExecutionPlan(graphs)) {
            plan.withPreCompilation();
            plan.withAllGraphs().execute();
        }
        check(false);
    }

    @Test
    public void aReduceGraphNextToOthers() throws TornadoExecutionPlanException {
        total.init(0);
        var graphs = graphs(true);
        try (var plan = new TornadoExecutionPlan(graphs)) {
            plan.execute();
        }
        check(true);
    }

    @Test
    public void strictFloatingPointReachesThePrefetchedCompilation() throws TornadoExecutionPlanException {
        var a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i * 0.37f - 500);
        }
        var out = new FloatArray(N);
        var graph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, a).task("t", TestParallelCompilation::edges, a, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().execute();
        }
        for (int i = 0; i < N; i++) {
            // Strict: the multiply and the add round separately, as on the JVM (no fma).
            assertEquals(Float.floatToRawIntBits(a.get(i) * 3.0f + 0.1f), Float.floatToRawIntBits(out.get(i)), "index " + i);
        }
    }

    public static void cube(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * a.get(i) * a.get(i));
        }
    }

    /**
     * Each kernel's TC compilation is a {@code tornado.Compile} JFR event (phase {@code tc}); with
     * parallel compilation, the plan's kernels are also compiled in the background first.
     */
    @Test
    public void compilationsAreJfrEvents() throws Exception {
        var a = new FloatArray(N);
        a.init(2.0f);
        var cubed = new FloatArray(N);
        var squares = new FloatArray(N);
        var g0 = new TaskGraph("jfr0").transferToDevice(DataTransferMode.EVERY_EXECUTION, a).task("t", TestParallelCompilation::cube, a, cubed)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cubed);
        var g1 = new TaskGraph("jfr1").transferToDevice(DataTransferMode.EVERY_EXECUTION, a).task("t", TestParallelCompilation::square, a, squares)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, squares);
        var file = Files.createTempFile("tornado-compile", ".jfr");
        Set<String> inOrder = new HashSet<>();
        Set<String> background = new HashSet<>();
        try (var recording = new Recording()) {
            recording.enable("tornado.Compile");
            recording.start();
            try (var plan = new TornadoExecutionPlan(g0.snapshot(), g1.snapshot())) {
                plan.withAllGraphs().execute();
            }
            recording.stop();
            recording.dump(file);
            for (var event : RecordingFile.readAllEvents(file)) {
                if (event.getEventType().getName().equals("tornado.Compile") && "tc".equals(event.getString("phase"))) {
                    (event.getBoolean("background") ? background : inOrder).add(event.getString("kernel"));
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
        assertEquals(8.0f, cubed.get(0), 0.0f);
        assertEquals(5.0f, squares.get(0), 0.0f);
        assertTrue(inOrder.containsAll(Set.of("TestParallelCompilation.cube", "TestParallelCompilation.square")), "in-order TC compilations: " + inOrder);
        if (!"false".equals(System.getProperty("tornado.parallel.compile"))) {
            assertTrue(background.containsAll(Set.of("TestParallelCompilation.cube", "TestParallelCompilation.square")), "background TC compilations: " + background);
        }
    }
}
