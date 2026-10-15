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
package uk.ac.manchester.tornado.tc.selftest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;

/**
 * Backend checks: kernels written in TC's IR ({@code /selftest/*.tcir}) are compiled for the device's
 * target and run through TornadoVM's prebuilt-task path. No Java bytecode is involved, so a failure
 * here and not in the {@code core} checks points at the backend (lowering, emission, the device
 * compiler or the kernel ABI) rather than at the bytecode frontend.
 */
final class BackendChecks {

    private static final int N = 1 << 16;
    private static final int BLOCK = 256;

    private BackendChecks() {
    }

    static List<Check> all() {
        return List.of(Check.of("backend", "ir-vector-add", "IR -> device: grid-stride loop over FloatArrays", BackendChecks::vectorAdd),
                Check.of("backend", "ir-branches", "IR -> device: if/else merging through a block argument", BackendChecks::branches),
                Check.of("backend", "ir-local-memory", "IR -> device: shared-memory tree reduction with barriers", BackendChecks::blockSum));
    }

    /** Compiles {@code /selftest/<name>.tcir} for {@code caps} and writes the source next to a temporary file. */
    private record Compiled(String entry, Path file) {
    }

    private static Compiled compile(String name, TargetCaps caps) throws IOException {
        var c = TcCompiler.compile(IrParser.parseFunction(resource("/selftest/" + name + ".tcir")), caps, TcOptions.DEFAULTS);
        if (!c.succeeded()) {
            throw new AssertionError("TC refused the kernel: " + c.diagnostics());
        }
        var extension = switch (caps.dialect()) {
            case CUDA -> ".cu";
            case OPENCL -> ".cl";
            case METAL -> ".metal";
        };
        var file = Files.createTempFile("tornado-selftest-" + name + "-", extension);
        file.toFile().deleteOnExit();
        Files.writeString(file, c.source());
        return new Compiled(c.entryName(), file);
    }

    private static void run(TornadoDevice device, String name, Compiled kernel, int threads, List<Object> args, List<Access> access, Object[] inputs, Object[] outputs)
            throws Exception {
        var accessors = new AccessorParameters(args.size());
        for (int i = 0; i < args.size(); i++) {
            accessors.set(i, args.get(i), access.get(i));
        }
        var graph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputs) //
                .prebuiltTask("t", kernel.entry(), kernel.file().toAbsolutePath().toString(), accessors) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputs);
        Checks.run(device, graph, Checks.grid1d(threads, BLOCK), false);
    }

    private static String vectorAdd(TornadoDevice device, TargetCaps caps) throws Exception {
        var kernel = compile("vector-add", caps);
        var a = new FloatArray(N);
        var b = new FloatArray(N);
        var c = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i * 0.5f);
            b.set(i, 1.0f - i);
        }
        // Half as many threads as elements: the grid-stride loop runs twice per thread.
        run(device, "irVectorAdd", kernel, N / 2, List.of(a, b, c), List.of(Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY), new Object[] { a, b }, new Object[] { c });
        for (int i = 0; i < N; i++) {
            Checks.same(a.get(i) + b.get(i), c.get(i), "element " + i);
        }
        return N + " elements, 2 per thread";
    }

    private static String branches(TornadoDevice device, TargetCaps caps) throws Exception {
        var kernel = compile("branches", caps);
        var in = new FloatArray(N);
        var out = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            in.set(i, (i % 7) - 3.25f);
        }
        run(device, "irBranches", kernel, N, List.of(in, out), List.of(Access.READ_ONLY, Access.WRITE_ONLY), new Object[] { in }, new Object[] { out });
        for (int i = 0; i < N; i++) {
            float x = in.get(i);
            Checks.close(x > 0 ? (float) Math.sqrt(x) * 2 : -x, out.get(i), 1e-6f, "element " + i);
        }
        return N + " elements";
    }

    private static String blockSum(TornadoDevice device, TargetCaps caps) throws Exception {
        var kernel = compile("local-memory", caps);
        var in = new FloatArray(N);
        var out = new FloatArray(N / BLOCK);
        for (int i = 0; i < N; i++) {
            in.set(i, (i % 13) * 0.25f);
        }
        run(device, "irLocalMemory", kernel, N, List.of(in, out), List.of(Access.READ_ONLY, Access.WRITE_ONLY), new Object[] { in }, new Object[] { out });
        for (int g = 0; g < N / BLOCK; g++) {
            float expected = 0;
            for (int i = g * BLOCK; i < (g + 1) * BLOCK; i++) {
                expected += in.get(i);
            }
            Checks.same(expected, out.get(g), "work-group " + g);
        }
        return (N / BLOCK) + " work-groups of " + BLOCK;
    }

    private static String resource(String path) {
        try (var in = BackendChecks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
