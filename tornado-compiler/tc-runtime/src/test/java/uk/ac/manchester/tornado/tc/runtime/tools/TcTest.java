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
package uk.ac.manchester.tornado.tc.runtime.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/** {@code tc emit}: every kernel of a directory, CUDA target options, compiler-style diagnostics. */
public class TcTest {

    /** The frontend's test kernels, compiled against tornado-api, and tornado-api itself. */
    private static final Path KERNELS = Path.of("../tc-frontend/target/test-classes");
    private static final String PACKAGE = "uk/ac/manchester/tornado/tc/frontend/kernels/";
    private static final String API = "../../tornado-api/target/classes";

    private record Result(int status, String out, String err) {
    }

    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = Tc.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** A directory holding only the given test kernel classes, as javac would leave them. */
    private static Path classes(Path dir, String... simpleNames) throws IOException {
        for (var name : simpleNames) {
            var target = dir.resolve(PACKAGE + name + ".class");
            Files.createDirectories(target.getParent());
            Files.copy(KERNELS.resolve(PACKAGE + name + ".class"), target);
        }
        return dir;
    }

    private static List<String> kernelNames(String simpleName) throws IOException {
        return Tc.kernels(ClassFile.parse(Files.readAllBytes(KERNELS.resolve(PACKAGE + simpleName + ".class")))).stream().map(Tc.Kernel::name).toList();
    }

    @Test
    public void deviceLaunchedChildrenAreNotKernelsOfTheirOwn() throws IOException {
        var names = kernelNames("LaunchKernels");
        assertTrue(names.contains("parent"), names.toString());
        assertFalse(names.contains("ping") || names.contains("pong") || names.contains("scale"), names.toString());
    }

    @Test
    public void kernelsAreListedInSourceOrder() throws IOException {
        var names = kernelNames("BasicKernels");
        assertEquals(List.of("add", "compute", "blockSum"), names.subList(0, 3));
    }

    @Test
    public void kernelParametersArePrimitivesArraysOrApiTypes() {
        assertTrue(Tc.kernelParameters("(Luk/ac/manchester/tornado/api/KernelContext;[FI)V"));
        assertFalse(Tc.kernelParameters("()V"));
        assertFalse(Tc.kernelParameters("(Ljava/lang/String;)V"));
        assertFalse(Tc.kernelParameters("([Ljava/lang/Object;)V"));
    }

    @Test
    public void allCompilesEveryKernelOfTheDirectoryAndResolvesDeviceKernels(@TempDir Path dir) throws IOException {
        classes(dir, "LaunchKernels");
        var r = run("emit", "--all", "--cp", dir + File.pathSeparator + API);
        assertTrue(r.out().contains("// ===== uk.ac.manchester.tornado.tc.frontend.kernels.LaunchKernels.parent ====="), r.out());
        // The DeviceKernel field is resolved through the loaded class: the parent launches its child.
        assertTrue(r.out().contains("<<<"), r.out() + r.err());
    }

    @Test
    public void computeCapabilityAndPrecisionsReachTheTarget() {
        var caps = Tc.target(TargetCaps.Dialect.CUDA, 90, false, null);
        assertEquals(90, caps.computeCapability());
        assertFalse(caps.fp16());
        assertTrue(caps.fp64());
        assertTrue(caps.deviceLaunch());
        assertEquals(TargetCaps.METAL_APPLE, Tc.target(TargetCaps.Dialect.METAL, 89, null, null));
    }

    @Test
    public void refusalsNameTheSourceLineLikeACompiler(@TempDir Path dir) throws IOException {
        classes(dir, "TileKernels");
        var r = run("emit", "--target", "opencl", "--cp", dir + File.pathSeparator + API, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "gemm");
        assertEquals(1, r.status());
        assertTrue(r.err().matches("(?s)TileKernels\\.java:\\d+: error: TC-E4010 .*"), r.err());
    }

    @Test
    public void deviceFunctionIsOneCudaFunctionForCudfTransform(@TempDir Path dir) throws IOException {
        classes(dir, "UdfKernels");
        var cp = dir + File.pathSeparator + API;
        var r = run("emit", "--device-fn", "--cp", cp, "uk.ac.manchester.tornado.tc.frontend.kernels.UdfKernels", "bucket");
        assertEquals(0, r.status(), r.err());
        assertTrue(r.out().startsWith("__device__ void UdfKernels_bucket(int *out, long long "), r.out());
        var refused = run("emit", "--device-fn", "--cp", cp, "uk.ac.manchester.tornado.tc.frontend.kernels.UdfKernels", "sum");
        assertEquals(1, refused.status());
        assertTrue(refused.err().contains("TC-E3501"), refused.err());
        assertEquals(2, run("emit", "--device-fn", "--all", "--cp", cp).status());
    }

    /**
     * The offsets the runtime's FieldLayout gives these API classes (read from it with the SDK): the
     * class-only layout tc emit uses must agree, or kernels would read object fields at the wrong place.
     */
    @Test
    public void typeLayoutsMatchTheRuntimesFieldLayout() throws Exception {
        try (var loader = new URLClassLoader(new URL[] { Path.of(API).toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            assertEquals(Map.of("COLUMNS", 16, "ROWS", 20, "numElements", 24, "storage", 28), names(TypeLayouts.offsets(loader.loadClass("uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat"), true)));
            assertEquals(Map.of("numElements", 16, "storage", 20), names(TypeLayouts.offsets(loader.loadClass("uk.ac.manchester.tornado.api.types.collections.VectorFloat4"), true)));
            assertEquals(Map.of("storage", 16), names(TypeLayouts.offsets(loader.loadClass("uk.ac.manchester.tornado.api.types.vectors.Float4"), true)));
        }
    }

    private static Map<String, Integer> names(Map<java.lang.reflect.Field, Integer> offsets) {
        var byName = new HashMap<String, Integer>();
        offsets.forEach((field, offset) -> byName.put(field.getName(), offset));
        return byName;
    }

    /** Compiles {@code source} (class {@code name}, default package) into {@code dir} against tornado-api, as javac -g -parameters. */
    private static void javac(Path dir, String name, String source) throws IOException {
        var file = dir.resolve(name + ".java");
        Files.writeString(file, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var options = new java.util.ArrayList<>(List.of("-g", "-parameters", "-cp", API, "-d", dir.toString(), file.toString()));
        if (Runtime.version().feature() == 21) {
            options.addAll(0, List.of("--release", "21", "--enable-preview"));
        }
        assertEquals(0, compiler.run(null, null, null, options.toArray(String[]::new)), "javac failed");
    }

    private static final String MATRIX_KERNEL = String.join("\n", //
            "import uk.ac.manchester.tornado.api.annotations.Parallel;", //
            "import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;", //
            "class Matrices {", //
            "    static void scale(Matrix2DFloat m, float factor) {", //
            "        for (@Parallel int i = 0; i < m.getNumRows(); i++) {", //
            "            for (@Parallel int j = 0; j < m.getNumColumns(); j++) {", //
            "                m.set(i, j, factor * m.get(i, j));", //
            "            }", //
            "        }", //
            "    }", //
            "}", "");

    @Test
    public void objectParametersCompileWithTheirLayoutAndTheAssumptionIsSaid(@TempDir Path dir) throws IOException {
        javac(dir, "Matrices", MATRIX_KERNEL);
        // Matrix2DFloat: refused without its layout (TC-E3101), compiled once its size is assumed.
        var r = run("emit", "--cp", dir + File.pathSeparator + API, "--all");
        assertEquals(0, r.status(), r.err());
        assertTrue(r.err().matches("(?s)Matrices\\.java:5: note: compiled for m=256: .*--arg name=value.*"), r.err());
        assertTrue(r.out().contains("// Matrices.java:5: note: compiled for m=256"), r.out());
    }

    @Test
    public void anObjectSizeCanBeGiven(@TempDir Path dir) throws IOException {
        javac(dir, "Matrices", MATRIX_KERNEL);
        var r = run("emit", "--arg", "m=64", "--cp", dir + File.pathSeparator + API, "--all");
        assertEquals(0, r.status(), r.err());
        assertFalse(r.err().contains("note:"), r.err());
    }

    private static final String AXPY_KERNEL = String.join("\n", //
            "import uk.ac.manchester.tornado.api.annotations.Parallel;", //
            "import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;", //
            "import uk.ac.manchester.tornado.api.types.arrays.FloatArray;", //
            "class Axpy {", //
            "    static void axpy(FloatArray x, FloatArray y, float a, DoubleArray d) {", //
            "        for (@Parallel int i = 0; i < y.getSize(); i++) {", //
            "            y.set(i, a * x.get(i) + y.get(i));", //
            "            d.set(i, d.get(i) * d.get(i) + 1.0);", //
            "        }", //
            "    }", //
            "    static void saxpy(FloatArray x, FloatArray y, float a) {", //
            "        for (@Parallel int i = 0; i < y.getSize(); i++) {", //
            "            y.set(i, a * x.get(i) + y.get(i));", //
            "        }", //
            "    }", //
            "    static void fused(FloatArray x, FloatArray y, float a) {", //
            "        for (@Parallel int i = 0; i < y.getSize(); i++) {", //
            "            y.set(i, Math.fma(a, x.get(i), y.get(i)));", //
            "        }", //
            "    }", //
            "}", "");

    /** A call to fma/fmaf in printed source; a substring test would also match an identifier such as fmaxValue. */
    private static final Pattern FMA_CALL = Pattern.compile("\\bfmaf?\\s*\\(");

    /**
     * --strict-fp: the printed CUDA is strict by itself, whatever flags the compiler that takes it uses
     * (NVRTC contracts a * b + c by default and has no pragma against it); OpenCL says so in a pragma.
     */
    @Test
    public void strictFloatingPointKeepsMultipliesOutOfFusedMultiplyAdds(@TempDir Path dir) throws IOException {
        javac(dir, "Axpy", AXPY_KERNEL);
        var cp = dir + File.pathSeparator + API;
        var plain = run("emit", "--cp", cp, "Axpy", "axpy");
        assertEquals(0, plain.status(), plain.err());
        assertFalse(plain.out().contains("__fmul_rn") || plain.out().contains("__dmul_rn"), plain.out());

        var strict = run("emit", "--strict-fp", "--cp", cp, "Axpy", "axpy");
        assertEquals(0, strict.status(), strict.err());
        assertTrue(strict.out().contains("__fmul_rn("), strict.out());
        assertTrue(strict.out().contains("__dmul_rn("), strict.out());
        assertFalse(FMA_CALL.matcher(strict.out()).find(), strict.out());
        assertFalse(strict.err().contains("note:"), strict.err());

        // Elsewhere the guarantee is a pragma the build can override, and tc says so rather than succeeding quietly.
        var opencl = run("emit", "--target", "opencl", "--strict-fp", "--cp", cp, "Axpy", "axpy");
        assertEquals(0, opencl.status(), opencl.err());
        assertTrue(opencl.out().contains("#pragma OPENCL FP_CONTRACT OFF"), opencl.out());
        assertTrue(opencl.err().contains("-cl-fast-relaxed-math"), opencl.err());
        var hip = run("emit", "--target", "hip", "--strict-fp", "--cp", cp, "Axpy", "axpy");
        assertEquals(0, hip.status(), hip.err());
        assertTrue(hip.out().contains("#pragma clang fp contract(off)"), hip.out());
        assertTrue(hip.err().contains("-ffast-math"), hip.err());
        var metal = run("emit", "--target", "metal", "--strict-fp", "--cp", cp, "Axpy", "saxpy");
        assertEquals(0, metal.status(), metal.err());
        assertTrue(metal.out().contains("#pragma clang fp contract(off)"), metal.out());
        assertTrue(metal.err().contains("-ffast-math"), metal.err());
        for (var target : List.of("hip", "metal", "opencl")) {
            var relaxed = run("emit", "--target", target, "--cp", cp, "Axpy", "saxpy");
            assertEquals(0, relaxed.status(), relaxed.err());
            assertFalse(relaxed.out().contains("contract(off)") || relaxed.out().contains("FP_CONTRACT OFF"), target + ": " + relaxed.out());
            assertFalse(relaxed.err().contains("note:"), relaxed.err());
        }
    }

    /** Java's Math.fma is an exact fused operation: --strict-fp must not unfuse it, on any target. */
    @Test
    public void strictFloatingPointKeepsMathFma(@TempDir Path dir) throws IOException {
        javac(dir, "Axpy", AXPY_KERNEL);
        var cp = dir + File.pathSeparator + API;
        for (var target : List.of("cuda", "opencl", "hip", "metal")) {
            var r = run("emit", "--target", target, "--strict-fp", "--cp", cp, "Axpy", "fused");
            assertEquals(0, r.status(), target + ": " + r.err());
            assertTrue(FMA_CALL.matcher(r.out()).find(), target + ": " + r.out());
            assertFalse(r.out().contains("__fmul_rn"), target + ": " + r.out());
        }
    }

    @Test
    public void givenValuesSpecialiseTheKernel() {
        var r = run("emit", "--arg", "arg0=4096", "--cp", KERNELS + File.pathSeparator + API, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "escapeTime");
        assertEquals(0, r.status(), r.err());
        assertFalse(r.err().contains("note:"), r.err());
        assertTrue(r.out().contains("4096"), r.out());
    }

    @Test
    public void classPathAliasesStillWork() {
        for (var option : List.of("--cp", "-cp", "--classpath")) {
            var r = run("emit", option, KERNELS + File.pathSeparator + API, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "escapeTime");
            assertEquals(0, r.status(), option + ": " + r.err());
        }
    }

    @Test
    public void aBadArgValueIsAUsageError(@TempDir Path dir) throws IOException {
        javac(dir, "Matrices", MATRIX_KERNEL);
        // An int for which text, a fraction, or a negative size is given: named, not thrown.
        for (var arg : List.of("arg0=abc", "arg0=1.5")) {
            var r = run("emit", "--arg", arg, "--cp", KERNELS + File.pathSeparator + API, "uk.ac.manchester.tornado.tc.frontend.kernels.ParallelKernels", "escapeTime");
            assertEquals(2, r.status(), arg + ": " + r.err());
            assertTrue(r.err().contains("--arg " + arg + ": arg0 takes an int"), r.err());
        }
        var r = run("emit", "--arg", "m=-4", "--cp", dir + File.pathSeparator + API, "--all");
        assertEquals(2, r.status(), r.err());
        assertTrue(r.err().contains("--arg m=-4: m takes a size: a non-negative whole number"), r.err());
    }

    @Test
    public void versionExitsCleanly() {
        var r = run("--version");
        assertEquals(0, r.status());
        assertTrue(r.out().startsWith("tc "), r.out());
    }

    @Test
    public void usageErrors() {
        assertEquals(2, run("emit").status());
        assertEquals(2, run("emit", "--all", "Extra", "arg").status());
        assertEquals(2, run("emit", "--fp16", "maybe", "--all").status());
    }
}
