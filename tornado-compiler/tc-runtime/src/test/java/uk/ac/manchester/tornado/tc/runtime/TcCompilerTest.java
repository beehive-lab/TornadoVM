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
    public void vectorTypesAreValues() {
        // Float4 is a register vector: the parameter is loaded from the lanes the runtime copies,
        // Float4.mult inlines to make_float4, setY replaces one lane. Only lanes are read, so the
        // canonicaliser takes them straight from the lanes made (y is the 0 setY wrote).
        // The helpers' bytecode comes from the TornadoVM API classes.
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes:../../tornado-api/target/classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "vectorValue", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertTrue(c.source(), c.source().contains("*((float *) v) * 2.0f + 0.0f + *((float *) v2) * 2.0f") && !c.source().contains("float4"));
        assertTrue(c.source(), c.source().contains("unsigned char *v"));
    }

    @Test
    public void rejectsRecursion() {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.Scalars", "recursive", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertFalse(c.succeeded());
        assertEquals("TC-E1009", c.diagnostics().get(0).code());
    }

    /** KernelContext.launch: children resolved through the host classes, compiled once each, in one module. */
    @Test
    public void deviceLaunchesLinkChildKernelsIntoOneModule() throws Exception {
        var urls = new java.net.URL[] { java.nio.file.Path.of("../tc-frontend/target/test-classes").toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.LaunchKernels", "parent", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(c.diagnostics().toString(), c.succeeded());
            var src = c.source();
            var ping = "uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_ping_dp";
            var pong = "uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_pong_dp";
            assertTrue(src, src.contains(ping + "<<<_dp_grid, _dp_block, 0, cudaStreamTailLaunch>>>"));
            assertTrue(src, src.contains(pong + "<<<_dp_grid, _dp_block>>>"));
            // One definition each, declared before any definition (the launches form a cycle).
            assertEquals(src, 1, src.split("void " + ping + "\\(long long \\*_kernel_context[^;]*\\)\\n\\{", -1).length - 1);
            assertTrue(src, src.indexOf("void " + pong + "(") < src.indexOf(")\n{\n", src.indexOf("__global__")));
            assertEquals("LaunchKernels_parent", c.entryName());
        }
    }

    /** TileContext kernels become CUDA Tile C++: typed tiles, auto views, a loop-carried mma accumulator. */
    @Test
    public void tileKernelsAreCudaTile() throws Exception {
        var urls = new java.net.URL[] { java.nio.file.Path.of("../tc-frontend/target/test-classes").toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var gemm = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "gemm", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(gemm.diagnostics().toString(), gemm.succeeded());
            var g = gemm.source();
            assertTrue(g, g.startsWith("#include \"cuda_tile.h\"") && g.contains("#include <cuda_fp16.h>") && g.contains("__tile_global__ void TileKernels_gemm("));
            assertTrue(g, g.contains("ct::tile<float, ct::shape<32, 32>> acc;") && g.contains("acc = ct::mma(") && g.contains("auto av = ct::partition_view{"));
            assertTrue(g, g.contains("reinterpret_cast<__half *>(a_data)") && g.contains("ct::full<ct::tile<float, ct::shape<32, 32>>>(0.0f)"));
            var sum = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "positiveRowSum", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(sum.diagnostics().toString(), sum.succeeded());
            var r = sum.source();
            assertTrue(r, r.contains("ct::tile<bool, ct::shape<1, 64>>") && r.contains(" > (float) 0.0") && r.contains("ct::select(") && r.contains("ct::sum(") && r.contains("1_ic)"));
            assertTrue(r, r.contains("ct::tile<float, ct::shape<1, 1>>"));
        }
    }

    private static TcCompiler.Compilation review(String kernel, java.util.List<uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact> facts) {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes:../../tornado-api/target/classes");
        return TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.ReviewKernels", kernel, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, facts);
    }

    /** Design review TP-4: sibling @Parallel loops share dimension x (they ran once per thread of each other). */
    @Test
    public void siblingParallelLoopsShareADimension() {
        var c = review("siblings", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(100), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(50)));
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertFalse(c.source(), c.source().contains("threadIdx.y"));
        assertEquals(1, c.domain().size());
        assertEquals(100, c.domain().get(0).upper());
    }

    /** Design review TP-2: a reduction into out[1] is refused, not lowered into the partials. */
    @Test
    public void reductionMustAccumulateIntoElementZero() {
        var c = review("reduceAtOne", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1024), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Reduce(2)));
        assertFalse(c.succeeded());
        assertEquals("TC-E3204", c.diagnostics().get(0).code());
    }

    /** Design review FE-4: a helper that changes its vector argument is refused (the change was lost). */
    @Test
    public void helperChangingAVectorArgumentIsRefused() {
        var c = review("helperSetter", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1)));
        assertFalse(c.succeeded());
        assertEquals("TC-E1012", c.diagnostics().get(0).code());
    }

    /** Design review TP-5: an array chosen at run time is lowered, and its length folds when all candidates agree. */
    @Test
    public void arraysThroughMergesAreLoweredAndTheirLengthsFold() {
        var L = new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(10);
        var same = review("lengthThroughMerge", java.util.List.of(L, L, new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(same.diagnostics().toString(), same.succeeded());
        assertTrue(same.source(), same.source().contains("= 10;"));
        var different = review("lengthThroughMerge", java.util.List.of(L, new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(20),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(different.diagnostics().toString(), different.succeeded());
    }

    /** Design review TP-3: in a batch, values see the global index and array indices stay chunk-local, per use. */
    @Test
    public void batchIndexIsGlobalAsValueAndLocalAsIndex() {
        var c = review("batchShared", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(100),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.IndexOffset(1000)));
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertTrue(c.source(), c.source().contains("i_global = i + 1000;") && c.source().contains("k = i_global + 1;") && c.source().contains("out_data[k - 1 - 1000] = k;"));
    }

    /** Design review FE-5 (not reproduced): a vector setter after a branch that defined another reference compiles. */
    @Test
    public void vectorSetterAfterABranchCompiles() {
        var c = review("setterAfterBranch", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(2),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(c.diagnostics().toString(), c.succeeded());
        assertTrue(c.source(), c.source().contains("= 5.0f;"));
    }
}
