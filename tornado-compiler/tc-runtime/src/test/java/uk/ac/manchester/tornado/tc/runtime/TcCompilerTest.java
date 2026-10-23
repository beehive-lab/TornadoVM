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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

public class TcCompilerTest {

    private static final String KERNEL = """
            kernel @K.scale(%a: array<f32>, %f: f32) -> void {
            ^entry:
              %i = tornado.thread_id.global<dim = 0>() : i32
              %x = array.load %a[%i] : f32
              %y = fmul %x, %f : f32
              array.store %a[%i], %y
              ret
            }
            """;

    @Test
    public void compilesThroughEveryStage() {
        var c = TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, TcOptions.DEFAULTS);
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertEquals("K_scale", c.entryName());
        assertEquals("input,verify.pre-abi,parallel,optimize,legality,abi,optimize.post-abi,structure,emit", String.join(",", c.stages().stream().map(TcCompiler.Stage::name).toList()));
        assertTrue(c.source().contains("extern \"C\" __global__ void K_scale("), c.source());
    }

    /** verify=end still verifies, once, before structuring. */
    @Test
    public void verifyAtTheEndRunsOnce() {
        var options = new TcOptions(java.util.Set.of(), java.nio.file.Path.of("tc-dumps"), false, java.util.Optional.empty(), false);
        var c = TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, options);
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertEquals("input,verify.pre-abi,parallel,optimize,legality,abi,optimize.post-abi,verify.end,structure,emit", String.join(",", c.stages().stream().map(TcCompiler.Stage::name).toList()));
    }

    /** From Java, inlining and local collections are pipeline steps (dumped, timed and verified like the rest). */
    @Test
    public void javaKernelsInlineAsAPipelineStep() {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.Scalars", "helpers", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.succeeded(), c.diagnostics().toString());
        var names = c.stages().stream().map(TcCompiler.Stage::name).toList();
        assertTrue(names.indexOf("inline") > names.indexOf("verify.pre-abi") && names.indexOf("local-collections") > names.indexOf("inline"), names.toString());
    }

    /** Legality refuses what the device's capabilities rule out, before any code is generated. */
    @Test
    public void legalityChecksTheTargetCapabilities() {
        var noDoubles = new TargetCaps(TargetCaps.Dialect.CUDA, 89, false, true, 16, 1024, 48 * 1024, 32, true, 0);
        var doubles = TcCompiler.compile(IrParser.parseFunction(KERNEL.replace("f32", "f64")), noDoubles, TcOptions.DEFAULTS);
        assertFalse(doubles.succeeded());
        assertEquals("TC-E4012", doubles.diagnostics().get(0).code());
        assertTrue(TcCompiler.compile(IrParser.parseFunction(KERNEL), noDoubles, TcOptions.DEFAULTS).succeeded());

        // Local memory over the reported size is left to the driver, which knows the real limit and
        // refuses with its reason (LocalMemoryFail on Metal).
        var smallShared = new TargetCaps(TargetCaps.Dialect.CUDA, 89, true, true, 16, 1024, 512, 32, true, 0);
        var withShared = IrParser.parseFunction(KERNEL.replace("^entry:\n", "^entry:\n  %buf = alloca.shared f32[256] : ptr<shared>\n"));
        assertTrue(uk.ac.manchester.tornado.tc.tornado.Legality.run(withShared, smallShared).isEmpty());
    }

    private static TargetCaps cuda(int cc, int nvrtc) {
        return new TargetCaps(TargetCaps.Dialect.CUDA, cc, true, true, 16, 1024, 48 * 1024, 32, true, nvrtc);
    }

    private static List<String> legality(String intrinsic, TargetCaps target) {
        var f = IrParser.parseFunction("kernel @k() -> void {\n^entry:\n  " + intrinsic + "()\n  ret\n}\n");
        return uk.ac.manchester.tornado.tc.tornado.Legality.run(f, target).stream().map(d -> d.code()).toList();
    }

    /** TC-5: tensor cores and cp.async need sm_80; FP8 MMA sm_89 and an NVRTC that emits PTX ISA 8.4. */
    @Test
    public void legalityChecksTensorCoreSupport() {
        assertEquals(List.of("TC-E4017"), legality("tornado.mma.m16n8k16", cuda(75, 12060)));
        assertEquals(List.of("TC-E4017"), legality("tornado.async_copy.commit", cuda(75, 12060)));
        assertEquals(List.of(), legality("tornado.mma.m16n8k16", cuda(80, 12060)));
        assertEquals(List.of("TC-E4018"), legality("tornado.mma.m16n8k32_e4m3", cuda(80, 12060)));
        assertEquals(List.of("TC-E4018"), legality("tornado.mma.m16n8k32_e5m2", cuda(86, 12060)));
        assertEquals(List.of("TC-E4018"), legality("tornado.mma.m16n8k32_e4m3", cuda(89, 12003)));
        assertEquals(List.of(), legality("tornado.mma.m16n8k32_e4m3", cuda(89, 12004)));
        assertEquals(List.of(), legality("tornado.mma.m16n8k32_e5m2", cuda(90, 0)));
        assertEquals(List.of(), legality("tornado.mma.m16n8k32_e4m3", TargetCaps.CUDA_SM89));
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
            assertEquals(java.util.List.of("K_scale.06-abi.tcir", "K_scale.09-emit.cu"), files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    /** A recompilation (another specialisation) of a kernel keeps the dumps of the earlier ones. */
    @Test
    public void recompilationDumpsBesideEarlierOnes() throws Exception {
        var dir = Files.createTempDirectory("tc-dump");
        var options = new TcOptions(java.util.Set.of("emit"), dir, true, java.util.Optional.empty(), false);
        TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, options);
        TcCompiler.compile(IrParser.parseFunction(KERNEL), TargetCaps.CUDA_SM89, options);
        try (var files = Files.list(dir)) {
            assertEquals(java.util.List.of("K_scale.09-emit.cu", "K_scale~2.09-emit.cu"), files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    public void inlinesHelperMethodsAndFoldsThem() {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.Scalars", "helpers", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertTrue(c.source().contains("return v3 + 4.0f;"), c.source());
    }

    @Test
    public void emitsFloatLoopExitsWithoutSwappingForever() {
        // Regression: two arm-swapping rules (direct comparison, no else after a jump) undid each
        // other on a float test whose else arm leaves the loop: StackOverflowError on juliaset.
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "escapeTime", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertTrue(c.source().contains("while ("), c.source());
    }

    @Test
    public void vectorTypesAreValues() {
        // Float4 is a register vector: the parameter is loaded from the lanes the runtime copies,
        // Float4.mult inlines to make_float4, setY replaces one lane. Only lanes are read, so the
        // canonicaliser takes them straight from the lanes made (y is the 0 setY wrote).
        // The helpers' bytecode comes from the TornadoVM API classes.
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes:../../tornado-api/target/classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "vectorValue", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.succeeded(), c.diagnostics().toString());
        // With math optimisations (the default) the products fuse into fmaf on CUDA.
        assertTrue(c.source().contains("fmaf(*((float *) v2), 2.0f, fmaf(*((float *) v), 2.0f, 0.0f))") && !c.source().contains("float4"), c.source());
        assertTrue(c.source().contains("unsigned char *v"), c.source());
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
            assertTrue(c.succeeded(), c.diagnostics().toString());
            var src = c.source();
            var ping = "uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_ping_dp";
            var pong = "uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_pong_dp";
            assertTrue(src.contains(ping + "<<<_dp_grid, _dp_block, 0, cudaStreamTailLaunch>>>"), src);
            assertTrue(src.contains(pong + "<<<_dp_grid, _dp_block>>>"), src);
            // One definition each, declared before any definition (the launches form a cycle).
            assertEquals(1, src.split("void " + ping + "\\(long long \\*_kernel_context[^;]*\\)\\n\\{", -1).length - 1, src);
            assertTrue(src.indexOf("void " + pong + "(") < src.indexOf(")\n{\n", src.indexOf("__global__")), src);
            assertEquals("LaunchKernels_parent", c.entryName());
        }
    }

    /**
     * A launched kernel stays a separate {@code __global__} kernel even when the launch sits in an
     * inlined helper and the same method is also called directly: the call is inlined, the launch is not.
     */
    @Test
    public void launchedKernelsAreNeverInlined() throws Exception {
        var urls = new java.net.URL[] { java.nio.file.Path.of("../tc-frontend/target/test-classes").toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.LaunchKernels", "dispatch", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(c.succeeded(), c.diagnostics().toString());
            var src = c.source();
            var scale = "uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_scale_dp";
            assertTrue(src.contains(scale + "<<<_dp_grid, _dp_block>>>"), src);
            // One definition of the child (its forward declaration aside).
            assertEquals(1, src.split("void " + scale + "\\([^;{]*\\)\\n\\{", -1).length - 1, src);
            // The parent holds one launch and the inlined direct call (* 3.0f), with no device function call.
            var parent = src.substring(src.indexOf("void LaunchKernels_dispatch("));
            assertEquals(1, parent.split(scale + "<<<", -1).length - 1, src);
            assertTrue(parent.contains("3.0f"), src);
            assertFalse(parent.contains(scale + "(_kernel_context"), src);
            // The child is a full kernel with its own thread index.
            var child = src.substring(src.indexOf("void " + scale + "("), src.indexOf("void LaunchKernels_dispatch("));
            assertTrue(child.contains("3.0f") && child.contains("threadIdx.x"), src);
        }
    }

    /** Compile-time operands (DeviceKernel, varargs) reach their call through a merge that carries one value. */
    @Test
    public void launchArgumentsMayBeChosenByATernary() throws Exception {
        var urls = new java.net.URL[] { java.nio.file.Path.of("../tc-frontend/target/test-classes").toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.LaunchKernels", "chosenArgument", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(c.succeeded(), c.diagnostics().toString());
            assertTrue(c.source().contains("uk_ac_manchester_tornado_tc_frontend_kernels_LaunchKernels_scale_dp<<<"), c.source());
        }
    }

    /** Every IR stage dump of every test kernel parses back and prints identically (the text is the IR). */
    @Test
    public void everyStageDumpRoundTrips() throws Exception {
        var classes = java.nio.file.Path.of("../tc-frontend/target/test-classes");
        var urls = new java.net.URL[] { classes.toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()); var files = Files.walk(classes.resolve("uk/ac/manchester/tornado/tc/frontend/kernels"))) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var options = TcOptions.DEFAULTS.withReport(true, Files.createTempDirectory("tc-roundtrip"));
            int stages = 0;
            for (var f : files.filter(p -> p.toString().endsWith(".class") && !p.toString().contains("$")).toList()) {
                var cls = "uk.ac.manchester.tornado.tc.frontend.kernels." + f.getFileName().toString().replace(".class", "");
                for (var m : Class.forName(cls, false, loader).getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                        continue;
                    }
                    for (var st : TcCompiler.compileJava(source, cls, m.getName(), TargetCaps.CUDA_SM89, options, java.util.List.of()).stages()) {
                        if (st.kind() == TcCompiler.Kind.IR && !st.text().isEmpty()) {
                            stages++;
                            assertEquals(st.text(), uk.ac.manchester.tornado.tc.ir.IrPrinter.print(IrParser.parseFunction(st.text())), cls + "." + m.getName() + " @" + st.name());
                        }
                    }
                }
            }
            assertTrue(stages > 200, "stages checked: " + stages);
        }
    }

    /** TileContext kernels become CUDA Tile C++: typed tiles, auto views, a loop-carried mma accumulator. */
    @Test
    public void tileKernelsAreCudaTile() throws Exception {
        var urls = new java.net.URL[] { java.nio.file.Path.of("../tc-frontend/target/test-classes").toUri().toURL(), java.nio.file.Path.of("../../tornado-api/target/classes").toUri().toURL() };
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
            var gemm = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "gemm", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(gemm.succeeded(), gemm.diagnostics().toString());
            var g = gemm.source();
            assertTrue(g.startsWith("#include \"cuda_tile.h\"") && g.contains("#include <cuda_fp16.h>") && g.contains("__tile_global__ void TileKernels_gemm("), g);
            assertTrue(g.contains("ct::tile<float, ct::shape<32, 32>> acc;") && g.contains("acc = ct::mma(") && g.contains("auto av = ct::partition_view{"), g);
            assertTrue(g.contains("reinterpret_cast<__half *>(a_data)") && g.contains("ct::full<ct::tile<float, ct::shape<32, 32>>>(0.0f)"), g);
            var sum = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "positiveRowSum", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            assertTrue(sum.succeeded(), sum.diagnostics().toString());
            var r = sum.source();
            assertTrue(r.contains("ct::tile<bool, ct::shape<1, 64>>") && r.contains(" > (float) 0.0") && r.contains("ct::select(") && r.contains("ct::sum(") && r.contains("1_ic)"), r);
            assertTrue(r.contains("ct::tile<float, ct::shape<1, 1>>"), r);
        }
    }

    private static TcCompiler.Compilation review(String kernel, java.util.List<uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact> facts) {
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes:../../tornado-api/target/classes");
        return TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.RegressionKernels", kernel, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, facts);
    }

    /**
     * TC-12: an accumulator fragment passed to a helper that indexes it as float[] (jitLLM's
     * addTile). Inlined, the helper's array loads had the fragment vector as their array (TC-E2107);
     * they are now the lane reads the frontend emits in the kernel itself.
     */
    @Test
    public void fragmentReadThroughAHelperBecomesLaneReads() {
        var c = review("fragmentThroughHelper", java.util.List.of());
        assertTrue(c.succeeded(), c.diagnostics().toString());
        for (var lane : java.util.List.of(".x", ".y", ".z", ".w")) {
            assertTrue(c.source().contains(lane + ";") || c.source().contains(lane + ")"), lane + " in\n" + c.source());
        }
        var written = review("fragmentWrittenInHelper", java.util.List.of());
        assertFalse(written.succeeded());
        assertEquals("TC-E1010", written.diagnostics().get(0).code(), written.diagnostics().toString());
    }

    /** Sibling @Parallel loops share dimension x (they ran once per thread of each other). */
    @Test
    public void siblingParallelLoopsShareADimension() {
        var c = review("siblings", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(100), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(50)));
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertFalse(c.source().contains("threadIdx.y"), c.source());
        assertEquals(1, c.domain().size());
        assertEquals(100, c.domain().get(0).upper());
    }

    /** A reduction into out[1] is refused, not lowered into the partials. */
    /** A work-group reduction is an IR tree the dialect prints like any loop, sized from TargetCaps. */
    @Test
    public void reductionsAreIrTreesSizedByTheTarget() {
        var facts = java.util.List.<uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact>of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(4096),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Reduce(17));
        var sum = review("sum", facts);
        assertTrue(sum.succeeded(), sum.diagnostics().toString());
        var src = sum.source();
        assertTrue(src.contains("__shared__ float reduce_scratch[1024];") && src.contains("__syncthreads();") && !src.contains("tc_reduce_"), src);
        var max = review("maxOf", facts);
        assertTrue(max.succeeded() && max.source().contains("java_fmaxf("), max.diagnostics().toString());
    }

    @Test
    public void reductionMustAccumulateIntoElementZero() {
        var c = review("reduceAtOne", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1024), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Reduce(2)));
        assertFalse(c.succeeded());
        assertEquals("TC-E3204", c.diagnostics().get(0).code());
    }

    /** A matrix allocated and only written in the kernel is dropped (Graal's escape analysis does the same); a read is refused. */
    @Test
    public void writeOnlyAllocationsAreDropped() {
        var c = review("writeOnlyMatrix", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(4)));
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertFalse(c.source().contains("10"), c.source());
        var read = review("readMatrix", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1)));
        assertFalse(read.succeeded());
        assertEquals("TC-E1011", read.diagnostics().get(0).code());
    }

    /** A helper that changes its vector argument is refused (the change was lost). */
    @Test
    public void helperChangingAVectorArgumentIsRefused() {
        var c = review("helperSetter", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1)));
        assertFalse(c.succeeded());
        assertEquals("TC-E1012", c.diagnostics().get(0).code());
    }

    /** An array chosen at run time is lowered, and its length folds when all candidates agree. */
    @Test
    public void arraysThroughMergesAreLoweredAndTheirLengthsFold() {
        var L = new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(10);
        var same = review("lengthThroughMerge", java.util.List.of(L, L, new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(same.succeeded(), same.diagnostics().toString());
        assertTrue(same.source().contains("= 10;"), same.source());
        var different = review("lengthThroughMerge", java.util.List.of(L, new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(20),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(1), new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(different.succeeded(), different.diagnostics().toString());
    }

    /** In a batch, values see the global index and array indices stay chunk-local, per use. */
    @Test
    public void batchIndexIsGlobalAsValueAndLocalAsIndex() {
        var c = review("batchShared", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(100),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.IndexOffset(1000)));
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertTrue(c.source().contains("i_global = i + 1000;") && c.source().contains("k = (int) ((unsigned int) i_global + (unsigned int) 1);")
                && c.source().contains("out_data[(int) ((unsigned int) (int) ((unsigned int) k - (unsigned int) 1) - (unsigned int) 1000)] = k;"), c.source());
    }

    /** Not reproduced as a failure: a vector setter after a branch that defined another reference compiles. */
    @Test
    public void vectorSetterAfterABranchCompiles() {
        var c = review("setterAfterBranch", java.util.List.of(new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Length(2),
                new uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact.Unknown()));
        assertTrue(c.succeeded(), c.diagnostics().toString());
        assertTrue(c.source().contains("= 5.0f;"), c.source());
    }
}
