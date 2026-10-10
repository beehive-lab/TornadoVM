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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;

import org.junit.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

public class TcCompilerTest {

    private static final String KERNEL = """
            kernel @K.scale(%a: array<f32>, %f: f32) -> void {
            ^entry:
              %i = tornado.thread_id.global.x() : i32
              %x = array.load %a[%i] : f32
              %y = fmul %x, %f : f32
              array.store %a[%i], %y
              ret
            }
            """;

    @Test
    public void compilesThroughEveryStage() {
        var c = TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, TcOptions.DEFAULTS);
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertEquals("K_scale", c.entryName());
        assertEquals("input,verify.pre-abi,parallel,optimize,abi,verify.post-abi,optimize.post-abi,structure,emit", String.join(",", c.stages().stream().map(TcCompiler.Stage::name).toList()));
        assertTrue(c.source(), c.source().contains("extern \"C\" __global__ void K_scale("));
    }

    @Test
    public void failureBecomesDiagnosticsNotExceptions() {
        var broken = KERNEL.replace("%y = fmul %x, %f : f32", "%y = fmul %x, %i : f32");
        var c = TcCompiler.compile(IrParser.parseFunction(broken), TargetCaps.CUDA_SM89, TcOptions.DEFAULTS);
        assertFalse(c.succeeded());
        assertEquals("TC-E2107", c.diagnostics().get(0).code());
        assertTrue(c.source().isEmpty());
    }

    @Test
    public void dumpsRequestedStages() throws Exception {
        var dir = Files.createTempDirectory("tc-dump");
        var options = new TcOptions(java.util.Set.of("abi", "emit"), dir, true, java.util.Optional.empty(), false);
        TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, options);
        try (var files = Files.list(dir)) {
            assertEquals(java.util.List.of("K_scale.05-abi.tcir", "K_scale.09-emit.cu"), files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    public void inlinesHelperMethodsAndFoldsThem() {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.Scalars", "helpers", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertTrue(c.source(), c.source().contains("return v3 + 4.0f;"));
    }

    @Test
    public void emitsFloatLoopExitsWithoutSwappingForever() {
        // Regression: two arm-swapping rules (direct comparison, no else after a jump) undid each
        // other on a float test whose else arm leaves the loop: StackOverflowError on juliaset.
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "escapeTime", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertTrue(c.source(), c.source().contains("while ("));
    }

    @Test
    public void rejectsRecursion() {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.Scalars", "recursive", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertFalse(c.succeeded());
        assertEquals("TC-E1009", c.diagnostics().get(0).code());
    }
}
