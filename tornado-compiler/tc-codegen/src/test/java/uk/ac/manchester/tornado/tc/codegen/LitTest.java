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
package uk.ac.manchester.tornado.tc.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uk.ac.manchester.tornado.tc.check.ExternalTool;
import uk.ac.manchester.tornado.tc.check.Lit;
import uk.ac.manchester.tornado.tc.codegen.tools.EmitTool;

/**
 * Runs every {@code .tcir} file under {@code src/test/lit}. When {@code nvcc} is available, CUDA tests
 * also compile the emitted CUDA to PTX for sm_89; when {@code utils/clbuild/clbuild} is built, OpenCL
 * tests are built with clBuildProgram on the GPUs; when {@code utils/metalc/metalc} is built (on a Mac),
 * Metal tests are compiled by the Metal framework on the GPU. A RUN line naming one of these tools is
 * skipped on a machine without it.
 */
public class LitTest {

    private static final Path ROOT = Path.of("src", "test", "lit");
    private static final Lit LIT;

    static {
        var tools = new HashMap<String, Lit.Tool>();
        tools.put("tc-emit", (args, stdin) -> {
            var result = EmitTool.run(args, stdin);
            return new Lit.ToolResult(result.exitCode(), result.stdout(), result.stderr());
        });
        var features = new HashSet<String>();
        // An nvcc that cannot target sm_89 (older than CUDA 11.8) counts as absent, like no nvcc at all.
        ExternalTool.find("nvcc", "/usr/local/cuda/bin").filter(LitTest::supportsSm89).ifPresent(nvcc -> {
            features.add("nvcc");
            tools.put("nvcc-ptx", ExternalTool.of(".cu", List.of(nvcc.toString(), "--ptx", "-arch=sm_89", "-o", "/dev/null", "%in")));
        });
        // utils/clbuild (gcc -O2 -o clbuild clbuild.c -lOpenCL): clBuildProgram on the machine's GPUs.
        var clbuild = Path.of("..", "utils", "clbuild", "clbuild");
        if (java.nio.file.Files.isExecutable(clbuild)) {
            features.add("opencl");
            tools.put("clbuild", ExternalTool.of(".cl", List.of(clbuild.toAbsolutePath().toString(), "%in")));
        }
        // utils/mslcheck: Metal source type-checked by clang against a mock metal_stdlib (no Mac needed).
        var mslcheck = Path.of("..", "utils", "mslcheck", "mslcheck");
        var clang = ExternalTool.find("clang++", System.getProperty("user.home") + "/.local/share/tc-llvm/bin");
        if (java.nio.file.Files.isExecutable(mslcheck) && clang.isPresent()) {
            features.add("mslcheck");
            tools.put("mslcheck", ExternalTool.of(".metal", List.of("env", "CLANG=" + clang.get(), mslcheck.toAbsolutePath().toString(), "%in")));
        }
        // utils/metalc (see metalc.m for the build line): newLibraryWithSource on the Mac's GPU, as the driver compiles.
        var metalc = Path.of("..", "utils", "metalc", "metalc");
        if (java.nio.file.Files.isExecutable(metalc)) {
            features.add("metal");
            tools.put("metalc", ExternalTool.of(".metal", List.of(metalc.toAbsolutePath().toString(), "%in")));
        }
        var absent = new HashSet<>(List.of("nvcc-ptx", "clbuild", "mslcheck", "metalc"));
        absent.removeAll(tools.keySet());
        LIT = new Lit(tools, features, absent);
    }

    /** Whether {@code nvcc} lists compute_89 among its GPU architectures. */
    private static boolean supportsSm89(Path nvcc) {
        try {
            var process = new ProcessBuilder(nvcc.toString(), "--list-gpu-arch").redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return process.waitFor() == 0 && output.contains("compute_89");
        } catch (java.io.IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static List<Object[]> files() {
        return Lit.discover(ROOT, ".tcir");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    public void run(String name, Path file) {
        var outcome = LIT.run(file);
        assumeFalse(outcome.unsupported(), outcome.log());
        assertTrue(outcome.passed(), name + "\n" + outcome.log());
    }
}
