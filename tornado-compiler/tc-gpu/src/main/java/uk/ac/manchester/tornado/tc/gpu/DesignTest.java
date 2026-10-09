/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.gpu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.tc.debug.HtmlReport;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;

/**
 * Gate C3 design test: three kernels written in TC-SSA are compiled by TC alone (verify, ABI
 * lowering, structuring, CUDA emission) and executed on a device through TornadoVM's prebuilt-task
 * path, then checked against Java references. Proves the IR shape, the ABI contract and the runtime
 * hand-off end to end before the frontend exists.
 *
 * <pre>tornado -cp &lt;tc jars&gt; uk.ac.manchester.tornado.tc.gpu.DesignTest [backend:device] [--quiet]</pre>
 */
public final class DesignTest {

    private static final int N = 1 << 20;
    private static final int BLOCK = 256;

    private record Compiled(String entry, Path file, long nanos) {
    }

    private final TornadoDevice device;
    private final boolean quiet;
    private final Path out;
    private int failures;

    private DesignTest(TornadoDevice device, boolean quiet, Path out) {
        this.device = device;
        this.quiet = quiet;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        var backend = 1;
        var index = 0;
        var quiet = false;
        for (var arg : args) {
            if (arg.equals("--quiet")) {
                quiet = true;
            } else if (arg.contains(":")) {
                backend = Integer.parseInt(arg.substring(0, arg.indexOf(':')));
                index = Integer.parseInt(arg.substring(arg.indexOf(':') + 1));
            }
        }
        var device = TornadoExecutionPlan.getDevice(backend, index);
        var out = Files.createDirectories(Path.of(System.getProperty("tc.dumpDir", "tc-c3-out")));
        var test = new DesignTest(device, quiet, out);
        System.out.println("C3 design test on " + device.getPhysicalDevice().getDeviceName() + " (" + backend + ":" + index + "), dumps in " + out.toAbsolutePath());
        test.vectorAdd();
        test.branchy();
        test.blockReduce();
        System.out.println(test.failures == 0 ? "C3 RESULT: PASS (3/3 kernels)" : "C3 RESULT: FAIL (" + test.failures + " kernel(s))");
        System.exit(test.failures == 0 ? 0 : 1);
    }

    /** TC pipeline through {@link TcCompiler}; every stage is dumped and an HTML report is written. */
    private Compiled compile(String name) {
        var function = IrParser.parseFunction(resource("/c3/" + name + ".tcir"));
        var c = TcCompiler.compile(function, TargetCaps.CUDA_SM89, TcOptions.fromSystemProperties().withReport(true, out));
        write(name + ".report.html", HtmlReport.render(c));
        if (!c.succeeded()) {
            throw new IllegalStateException(name + ": " + c.diagnostics());
        }
        for (var stage : c.stages()) {
            switch (stage.name()) {
                case "abi" -> write(name + ".post-abi.tcir", stage.text());
                case "structure" -> write(name + ".tcs", stage.text());
                default -> {
                }
            }
        }
        var cu = write(name + ".cu", c.source());
        if (!quiet) {
            System.out.println("---- " + name + ".cu ----\n" + c.source());
        }
        return new Compiled(c.entryName(), cu, c.nanos());
    }

    private void run(String name, Compiled kernel, int globalSize, AccessorParameters accessors, Object[] inputs, Object[] outputs, Runnable check) {
        var graph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputs) //
                .prebuiltTask("t0", kernel.entry(), kernel.file().toAbsolutePath().toString(), accessors) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputs);
        var grid = new WorkerGrid1D(globalSize);
        grid.setLocalWork(BLOCK, 1, 1);
        var scheduler = new GridScheduler(name + ".t0", grid);
        long start = System.nanoTime();
        try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(scheduler).withDevice(device).execute();
        } catch (Exception e) {
            report(name, kernel, false, "execution failed: " + e);
            return;
        }
        long runNanos = System.nanoTime() - start;
        try {
            check.run();
            report(name, kernel, true, String.format("first run (NVRTC + launch) %.1f ms", runNanos / 1e6));
        } catch (AssertionError e) {
            report(name, kernel, false, e.getMessage());
        }
    }

    private void vectorAdd() {
        var kernel = compile("vector-add");
        var a = new FloatArray(N);
        var b = new FloatArray(N);
        var c = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i * 0.5f);
            b.set(i, 1.0f - i);
        }
        var acc = accessors(List.of(a, b, c), List.of(Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY));
        // Half the threads, so the grid-stride loop runs two iterations per thread.
        run("vadd", kernel, N / 2, acc, new Object[] { a, b }, new Object[] { c }, () -> check(N, i -> expect(c.get(i), a.get(i) + b.get(i), i)));
    }

    private void branchy() {
        var kernel = compile("branchy");
        var in = new FloatArray(N);
        var out = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            in.set(i, (i % 7) - 3.25f);
        }
        var acc = accessors(List.of(in, out), List.of(Access.READ_ONLY, Access.WRITE_ONLY));
        run("branchy", kernel, N, acc, new Object[] { in }, new Object[] { out }, () -> check(N, i -> {
            float x = in.get(i);
            float expected = x > 0 ? (float) Math.sqrt(x) * 2 : -x;
            expect(out.get(i), expected, i);
        }));
    }

    private void blockReduce() {
        var kernel = compile("block-reduce");
        var in = new FloatArray(N);
        var out = new FloatArray(N / BLOCK);
        for (int i = 0; i < N; i++) {
            in.set(i, (i % 13) * 0.25f);
        }
        var acc = accessors(List.of(in, out), List.of(Access.READ_ONLY, Access.WRITE_ONLY));
        run("reduce", kernel, N, acc, new Object[] { in }, new Object[] { out }, () -> check(N / BLOCK, g -> {
            float expected = 0;
            for (int i = g * BLOCK; i < (g + 1) * BLOCK; i++) {
                expected += in.get(i);
            }
            if (Math.abs(out.get(g) - expected) > 1e-3f * Math.max(1f, Math.abs(expected))) {
                throw new AssertionError("group " + g + ": expected " + expected + " got " + out.get(g));
            }
        }));
    }

    // ---------------------------------------------------------------- helpers

    private static AccessorParameters accessors(List<Object> objects, List<Access> access) {
        var acc = new AccessorParameters(objects.size());
        for (int i = 0; i < objects.size(); i++) {
            acc.set(i, objects.get(i), access.get(i));
        }
        return acc;
    }

    private static void check(int count, Consumer<Integer> element) {
        for (int i = 0; i < count; i++) {
            element.accept(i);
        }
    }

    private static void expect(float actual, float expected, int index) {
        if (Float.compare(actual, expected) != 0 && Math.abs(actual - expected) > 1e-6f * Math.max(1f, Math.abs(expected))) {
            throw new AssertionError("element " + index + ": expected " + expected + " got " + actual);
        }
    }

    private void report(String name, Compiled kernel, boolean pass, String detail) {
        if (!pass) {
            failures++;
        }
        System.out.printf("%-8s %s  TC compile %.2f ms  %s%n", name, pass ? "PASS" : "FAIL", kernel.nanos() / 1e6, detail);
    }

    private Path write(String name, String text) {
        try {
            return Files.writeString(out.resolve(name), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (var in = DesignTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
