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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * Device-function output ({@link TcCompiler#compileDeviceFunction}): a scalar Java method as the CUDA
 * {@code __device__} function {@code cudf::transform} calls, {@code void f(R *out, A0 in0, ...)}.
 *
 * <p>
 * Without a GPU: the signature, the result store, that the function is the first thing with a
 * parenthesis (cudf's JIT renames the token before the source's first {@code (}), and the refusals.
 * With {@code nvcc} and a GPU: each function is renamed as cudf renames it, called from a kernel the
 * way cudf's transform kernel calls it, run on the GPU, and compared with the Java method, bit for bit
 * for integer and strict floating-point functions.
 */
public class DeviceFunctionTest {

    private static final String KERNELS = "uk.ac.manchester.tornado.tc.frontend.kernels.UdfKernels";
    private static final Path CLASSES = Path.of("../tc-frontend/target/test-classes");

    private static TcCompiler.Compilation compile(String method, TcOptions options) {
        return compile(method, options, TargetCaps.CUDA_SM89);
    }

    private static TcCompiler.Compilation compile(String method, TcOptions options, TargetCaps target) {
        return TcCompiler.compileDeviceFunction(Frontend.BytecodeSource.classpath(CLASSES.toString()), KERNELS, method, null, target, options);
    }

    private static String source(String method) {
        var c = compile(method, TcOptions.DEFAULTS);
        assertTrue(c.succeeded(), c.diagnostics().toString());
        return c.source();
    }

    /** The source as cudf's {@code parse_single_function_cuda} leaves it: comments removed, the first function renamed. */
    static String cudfRename(String source, String newName, String[] renamed) {
        var text = source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        int stop = text.indexOf('(');
        int end = stop - 1;
        while (end > 0 && Character.isWhitespace(text.charAt(end))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        start++;
        renamed[0] = text.substring(start, end + 1);
        return text.substring(0, start) + newName + text.substring(end + 1);
    }

    @Test
    public void collatzIsADeviceFunction() {
        var c = compile("collatz", TcOptions.DEFAULTS);
        assertTrue(c.succeeded(), c.diagnostics().toString());
        var src = c.source();
        assertTrue(src.startsWith("__device__ void UdfKernels_collatz(int *out, long long "), src);
        assertTrue(src.contains("*out = "), src);
        assertFalse(src.contains("__global__") || src.contains("_kernel_context") || src.contains("return;"), src);
        var renamed = new String[1];
        cudfRename(src, "GENERIC_TRANSFORM_OP", renamed);
        assertEquals(c.entryName(), renamed[0]);
    }

    @Test
    public void signaturesAreAsNarrowAsJava() {
        assertTrue(source("mixed").startsWith("__device__ void UdfKernels_mixed(long long *out, int "), source("mixed"));
        assertTrue(source("mixed").contains(", short ") && source("mixed").contains(", signed char "), source("mixed"));
        assertTrue(source("narrow").startsWith("__device__ void UdfKernels_narrow(short *out, int "), source("narrow"));
        assertTrue(source("narrow").contains("*out = (short) ("), source("narrow"));
        assertTrue(source("isEven").startsWith("__device__ void UdfKernels_isEven(bool *out, long long "), source("isEven"));
    }

    @Test
    public void helpersAreLambdasInsideTheFunction() {
        for (var method : List.of("mixed", "minimum")) {
            var src = source(method);
            var renamed = new String[1];
            cudfRename(src, "GENERIC_TRANSFORM_OP", renamed);
            assertEquals("UdfKernels_" + method, renamed[0], src);
            assertTrue(src.contains(" = [&]("), src);
        }
    }

    /** A device function stands in for a CPU UDF: strict by default, so cudf's own compiler flags cannot contract it. */
    @Test
    public void strictFloatingPointByDefault() {
        for (var options : List.of(TcOptions.DEFAULTS, TcOptions.DEFAULTS.withStrictFloatingPoint(true))) {
            var strict = compile("axpy", options);
            assertTrue(strict.succeeded(), strict.diagnostics().toString());
            assertTrue(strict.source().contains("__dmul_rn("), strict.source());
            assertFalse(strict.source().contains("fma("), strict.source());
        }
        var contracting = compile("axpy", TcOptions.DEFAULTS.withDeviceFunction(true).withFusedMultiplyAdd(true));
        assertTrue(contracting.succeeded(), contracting.diagnostics().toString());
        assertFalse(contracting.source().contains("__dmul_rn("), contracting.source());
    }

    @Test
    public void privateArraysAreAllowed() {
        assertTrue(source("table").contains("[4]"), source("table"));
    }

    private static void refused(String method, String code, TargetCaps target) {
        var c = TcCompiler.compileDeviceFunction(Frontend.BytecodeSource.classpath(CLASSES.toString()), KERNELS, method, null, target, TcOptions.DEFAULTS);
        assertFalse(c.succeeded(), method + " compiled:\n" + c.source());
        assertEquals(code, c.diagnostics().get(0).code(), c.diagnostics().toString());
    }

    @Test
    public void refusesWhatIsNotAPerRowFunction() {
        refused("sum", "TC-E3501", TargetCaps.CUDA_SM89);
        refused("nothing", "TC-E3501", TargetCaps.CUDA_SM89);
        refused("instance", "TC-E3501", TargetCaps.CUDA_SM89);
        refused("atomic", "TC-E3502", TargetCaps.CUDA_SM89);
        refused("parallel", "TC-E3502", TargetCaps.CUDA_SM89);
        refused("collatz", "TC-E3504", TargetCaps.OPENCL_GENERIC);
        refused("scribble", "TC-E3502", TargetCaps.CUDA_SM89);
        refused("mergedScribble", "TC-E3502", TargetCaps.CUDA_SM89);
    }

    /** The row pointer is const, and so is a local that may hold one, so a store the IR check missed would not compile. */
    @Test
    public void stringRowsAreConst() {
        assertTrue(source("fnv1a").contains("const unsigned char *s = (const unsigned char *) s_sv.data();"), source("fnv1a"));
        var src = source("pickFirst");
        assertTrue(src.contains("const unsigned char *"), src);
        assertFalse(src.lines().anyMatch(l -> l.strip().startsWith("unsigned char *")), src);
    }

    /** Facts about scalar parameters are kept; a fact about a string row is refused, not dropped. */
    @Test
    public void factsAreMergedWithStringRows() {
        var source = Frontend.BytecodeSource.classpath(CLASSES.toString());
        var salted = TcCompiler.compileDeviceFunction(source, KERNELS, "stringBucket", null, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS,
                List.of(new ValueSpecialisation.Fact.Unknown(), new ValueSpecialisation.Fact.Scalar(42)));
        assertTrue(salted.succeeded(), salted.diagnostics().toString());
        assertFalse(salted.source().equals(source("stringBucket")), "the salt was not specialised:\n" + salted.source());
        var wrong = TcCompiler.compileDeviceFunction(source, KERNELS, "stringBucket", null, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS,
                List.of(new ValueSpecialisation.Fact.Length(8), new ValueSpecialisation.Fact.Unknown()));
        assertFalse(wrong.succeeded(), wrong.source());
        assertEquals("TC-E3501", wrong.diagnostics().get(0).code(), wrong.diagnostics().toString());
    }

    /** A byte[] parameter is a string row: a cudf::string_view, read through data() with its size_bytes() as length. */
    @Test
    public void stringRowsAreStringViews() {
        var src = source("murmur3");
        assertTrue(src.startsWith("__device__ void UdfKernels_murmur3(int *out, cudf::string_view "), src);
        assertTrue(src.contains(".data();") && src.contains(".size_bytes();"), src);
        assertFalse(src.contains("+ 16") || src.contains("16LL"), "a string row has no array header:\n" + src);
        var renamed = new String[1];
        cudfRename(src, "GENERIC_TRANSFORM_OP", renamed);
        assertEquals("UdfKernels_murmur3", renamed[0], src);
        assertTrue(source("stringBucket").startsWith("__device__ void UdfKernels_stringBucket(int *out, cudf::string_view "), source("stringBucket"));
        assertTrue(source("stringBucket").contains(", int "), source("stringBucket"));
    }

    // ---- on the GPU

    private static final List<Long> LONGS = List.of(1L, 2L, 3L, 7L, 27L, 97L, 871L, 6171L, 77031L, 837799L, 8400511L, 63728127L, 99999999L, 100000000L);

    private static List<Object[]> rows(int n, IntFunction<Object[]> row) {
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < n; i++) {
            rows.add(row.apply(i));
        }
        return rows;
    }

    /** Row {@code i} of a strings column: {@code i % 41} bytes of any value. */
    private static byte[] bytes(int i) {
        var b = new byte[i % 41];
        for (int k = 0; k < b.length; k++) {
            b[k] = (byte) mixLong(i * 64 + k);
        }
        return b;
    }

    private static long mixLong(int i) {
        long z = 0x9E3779B97F4A7C15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        return z ^ (z >>> 27);
    }

    @Test
    public void runsOnTheGpuAsInJava(@TempDir Path dir) throws Exception {
        var toolkit = CudaProbe.find(dir, "");
        // Skipped without nvcc or a GPU, which is every machine without CUDA, and when the two cannot be
        // made to talk to each other. Set TC_REQUIRE_GPU=1 where all three are expected, so that losing
        // one fails the build instead of skipping the check quietly.
        if (System.getenv("TC_REQUIRE_GPU") != null) {
            assertTrue(toolkit.usable(), "TC_REQUIRE_GPU is set, but nothing here can build and run a CUDA kernel: " + toolkit.why());
        }
        assumeTrue(toolkit.nvcc().isPresent(), "no nvcc");
        assumeTrue(toolkit.cc().isPresent(), "no GPU");
        assumeTrue(toolkit.usable(), "nothing this nvcc builds reaches this GPU: " + toolkit.why());
        try (var loader = new URLClassLoader(new URL[] { CLASSES.toUri().toURL() }, getClass().getClassLoader())) {
            var kernels = loader.loadClass(KERNELS);
            var gpu = new Gpu(dir, toolkit.nvccPath(), toolkit.architecture());
            check(gpu, kernels, "collatz", TcOptions.DEFAULTS, rows(LONGS.size() + 200, i -> new Object[] { i < LONGS.size() ? LONGS.get(i) : (long) (i * 7919 + 1) }), 0);
            check(gpu, kernels, "bucket", TcOptions.DEFAULTS, rows(1000, i -> new Object[] { mixLong(i) }), 0);
            check(gpu, kernels, "mixed", TcOptions.DEFAULTS,
                    rows(1000, i -> new Object[] { (int) mixLong(i), mixLong(i + 7), (short) mixLong(i + 3), (byte) mixLong(i + 5) }), 0);
            check(gpu, kernels, "narrow", TcOptions.DEFAULTS, rows(500, i -> new Object[] { (int) mixLong(i) }), 0);
            check(gpu, kernels, "isEven", TcOptions.DEFAULTS, rows(100, i -> new Object[] { mixLong(i) }), 0);
            check(gpu, kernels, "table", TcOptions.DEFAULTS, rows(100, i -> new Object[] { (int) mixLong(i) }), 0);
            check(gpu, kernels, "minimum", TcOptions.DEFAULTS,
                    rows(8, i -> new Object[][] { { 1.0, 2.0 }, { -0.0, 0.0 }, { 0.0, -0.0 }, { Double.NaN, 1.0 }, { 1.0, Double.NaN }, { -1e300, 1e-300 }, { 3.5, 3.5 },
                            { Double.NEGATIVE_INFINITY, 0.0 } }[i]), 0);
            // a * x + y: bit-exact only when the product is not contracted into an fma, which a device function's default ensures.
            check(gpu, kernels, "axpy", TcOptions.DEFAULTS,
                    rows(1000, i -> new Object[] { Double.longBitsToDouble(0x3FF0000000000000L | (mixLong(i) >>> 12)), Double.longBitsToDouble(0x3FF0000000000000L | (mixLong(i + 1) >>> 12)),
                            -Double.longBitsToDouble(0x3FF0000000000000L | (mixLong(i + 2) >>> 12)) }), 0);
            // String rows (cudf::string_view): every length 0..40, so every Murmur3 tail; bytes of every value.
            check(gpu, kernels, "fnv1a", TcOptions.DEFAULTS, rows(500, i -> new Object[] { bytes(i) }), 0);
            check(gpu, kernels, "murmur3", TcOptions.DEFAULTS, rows(500, i -> new Object[] { bytes(i), (int) mixLong(i + 11) }), 0);
            check(gpu, kernels, "stringBucket", TcOptions.DEFAULTS, rows(500, i -> new Object[] { bytes(i), (int) mixLong(i + 13) }), 0);
            // Two rows, one chosen at run time through a const local: rows of at least one byte, as a[0] needs.
            check(gpu, kernels, "pickFirst", TcOptions.DEFAULTS, rows(300, i -> new Object[] { bytes(i * 41 + 1 + i % 40), bytes(i * 41 + 2 + i % 39), (int) mixLong(i) }), 0);
            // CUDA's sin, pow and exp are not Java's: within a few ulp.
            check(gpu, kernels, "fp", TcOptions.DEFAULTS, rows(1000, i -> new Object[] { i * 0.999 + 0.001 }), 4);
        }
    }

    private static String cType(Class<?> c) {
        return c == boolean.class ? "bool" : c == byte.class ? "signed char" : c == char.class ? "unsigned short" : c == short.class ? "short" : c == int.class ? "int" : c == long.class ? "long long"
                : c == float.class ? "float" : "double";
    }

    private static String literal(Object v) {
        return switch (v) {
            case Boolean b -> b ? "true" : "false";
            case Long l -> l == Long.MIN_VALUE ? "(-9223372036854775807LL - 1)" : l + "LL";
            case Integer n -> n == Integer.MIN_VALUE ? "(-2147483647 - 1)" : n.toString();
            case Short s -> "(short) " + s;
            case Byte b -> "(signed char) " + b;
            case Double d -> "bits_to_double(0x" + Long.toHexString(Double.doubleToRawLongBits(d)) + "ULL)";
            default -> throw new IllegalArgumentException(v.toString());
        };
    }

    /** Where the GPU check builds and runs: a scratch directory, nvcc, and the architecture it builds for. */
    private record Gpu(Path dir, Path nvcc, CudaProbe.Arch arch) {
        /** TC generates for the compute capability nvcc builds for, so the two cannot disagree about the target. */
        TargetCaps target() {
            var b = TargetCaps.CUDA_SM89;
            return new TargetCaps(b.dialect(), arch.cc(), b.fp64(), b.fp16(), b.arrayHeaderBytes(), b.maxWorkGroupSize(), b.localMemoryBytes(), b.warpSize(), b.deviceLaunch(), b.nvrtcVersion(),
                    b.arch());
        }
    }

    /** Compiles {@code method}, runs it on the GPU over {@code rows} and compares with Java ({@code ulps} for doubles). */
    private static void check(Gpu gpu, Class<?> kernels, String method, TcOptions options, List<Object[]> rows, long ulps) throws Exception {
        var dir = gpu.dir();
        var c = compile(method, options, gpu.target());
        assertTrue(c.succeeded(), method + ": " + c.diagnostics());
        Method java = null;
        for (var m : kernels.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                java = m;
            }
        }
        var params = java.getParameterTypes();
        var result = java.getReturnType();
        var renamed = new String[1];
        var udf = cudfRename(c.source(), "GENERIC_TRANSFORM_OP", renamed);
        var cu = new StringBuilder();
        cu.append("#include <cstdio>\n#include <cstring>\n");
        cu.append("static double bits_to_double(unsigned long long b) { double d; std::memcpy(&d, &b, 8); return d; }\n");
        // cudf passes a string row as a cudf::string_view: what a device function uses of it, as cudf declares it.
        cu.append("namespace cudf { struct string_view { const char *d; int n;\n");
        cu.append("  __device__ string_view(const char *data, int bytes) : d(data), n(bytes) {}\n");
        cu.append("  __device__ const char *data() const { return d; }\n  __device__ int size_bytes() const { return n; } }; }\n");
        cu.append(udf).append('\n');
        cu.append("__global__ void k(").append(cType(result)).append(" *out");
        for (int p = 0; p < params.length; p++) {
            if (params[p] == byte[].class) {
                cu.append(", const unsigned char *in").append(p).append("c, const int *in").append(p).append('o');
            } else {
                cu.append(", const ").append(cType(params[p])).append(" *in").append(p);
            }
        }
        cu.append(", int n) {\n  int i = blockIdx.x * blockDim.x + threadIdx.x;\n  if (i < n) GENERIC_TRANSFORM_OP(&out[i]");
        for (int p = 0; p < params.length; p++) {
            if (params[p] == byte[].class) {
                var chars = "in" + p + "c";
                var offsets = "in" + p + "o";
                cu.append(", cudf::string_view((const char *) ").append(chars).append(" + ").append(offsets).append("[i], ").append(offsets).append("[i + 1] - ").append(offsets).append("[i])");
            } else {
                cu.append(", in").append(p).append("[i]");
            }
        }
        cu.append(");\n}\n");
        cu.append("int main() {\n  const int n = ").append(rows.size()).append(";\n");
        for (int p = 0; p < params.length; p++) {
            if (params[p] == byte[].class) {
                stringColumn(cu, p, rows);
                continue;
            }
            cu.append("  static const ").append(cType(params[p])).append(" h").append(p).append("[] = {");
            for (var row : rows) {
                cu.append(literal(row[p])).append(", ");
            }
            cu.append("};\n  ").append(cType(params[p])).append(" *d").append(p).append(";\n");
            cu.append("  cudaMalloc(&d").append(p).append(", sizeof(h").append(p).append("));\n");
            cu.append("  cudaMemcpy(d").append(p).append(", h").append(p).append(", sizeof(h").append(p).append("), cudaMemcpyHostToDevice);\n");
        }
        var r = cType(result);
        cu.append("  ").append(r).append(" *dout, hout[n];\n  cudaMalloc(&dout, sizeof(hout));\n");
        cu.append("  k<<<(n + 127) / 128, 128>>>(dout");
        for (int p = 0; p < params.length; p++) {
            cu.append(params[p] == byte[].class ? ", d" + p + "c, d" + p + "o" : ", d" + p);
        }
        // cudaMemcpy alone does not report a launch that never happened: a kernel rejected by the driver
        // leaves cudaDeviceSynchronize and the copy returning cudaSuccess, and hout holds whatever cudaMalloc
        // handed back. Reading the launch's own error first is what keeps untouched memory out of the
        // comparison, where it would read as a wrong answer from the compiler rather than as no answer.
        cu.append(", n);\n");
        cu.append("  if (cudaGetLastError() != cudaSuccess || cudaDeviceSynchronize() != cudaSuccess) { std::printf(\"NO-LAUNCH\\n\"); return 3; }\n");
        cu.append("  if (cudaMemcpy(hout, dout, sizeof(hout), cudaMemcpyDeviceToHost) != cudaSuccess) { std::printf(\"NO-GPU\\n\"); return 3; }\n");
        cu.append("  for (int i = 0; i < n; i++) {\n");
        if (result == double.class) {
            cu.append("    unsigned long long b; std::memcpy(&b, &hout[i], 8); std::printf(\"%llu\\n\", b);\n");
        } else {
            cu.append("    std::printf(\"%lld\\n\", (long long) hout[i]);\n");
        }
        cu.append("  }\n  return 0;\n}\n");
        var file = dir.resolve(method + ".cu");
        Files.writeString(file, cu);
        var exe = dir.resolve(method);
        var build = CudaProbe.run(dir, List.of(gpu.nvcc().toString(), "-std=c++17", "-arch=" + gpu.arch().flag(), "-o", exe.toString(), file.toString()));
        assertEquals(0, build.exit(), method + ": nvcc failed (" + gpu.nvcc() + " -arch=" + gpu.arch().flag() + ")\n" + build.output() + "\n" + cu);
        // This architecture already built and ran a kernel on this GPU (runs), so a binary that cannot
        // reach it now is a failure, not a skip.
        var ran = CudaProbe.run(dir, List.of(exe.toString()));
        assertEquals(0, ran.exit(), method + ": " + ran.output());
        var lines = ran.output().strip().lines().toList();
        assertEquals(rows.size(), lines.size(), ran.output());
        for (int i = 0; i < rows.size(); i++) {
            var expected = java.invoke(null, rows.get(i));
            long got = result == double.class ? Long.parseUnsignedLong(lines.get(i).strip()) : Long.parseLong(lines.get(i).strip());
            if (result == double.class) {
                long want = Double.doubleToRawLongBits((Double) expected);
                long distance = Math.abs(want - got);
                assertTrue(distance <= ulps, method + " row " + i + ": Java " + expected + ", GPU " + Double.longBitsToDouble(got) + " (" + distance + " ulp)");
            } else {
                long want = switch (expected) {
                    case Boolean b -> b ? 1 : 0;
                    case Number num -> num.longValue();
                    default -> throw new IllegalStateException();
                };
                assertEquals(want, got, method + " row " + i + " " + java.toGenericString());
            }
        }
    }

    /**
     * Parameter {@code p} of every row as a strings column: the rows' bytes back to back
     * ({@code d<p>c}, with one padding byte so that it is never empty) and {@code n + 1} offsets ({@code d<p>o}).
     */
    private static void stringColumn(StringBuilder cu, int p, List<Object[]> rows) {
        var chars = new StringBuilder();
        var offsets = new StringBuilder("0, ");
        int at = 0;
        for (var row : rows) {
            for (byte b : (byte[]) row[p]) {
                chars.append(b & 0xff).append(", ");
            }
            at += ((byte[]) row[p]).length;
            offsets.append(at).append(", ");
        }
        chars.append('0');
        for (var part : List.of(new String[] { "c", "unsigned char", chars.toString() }, new String[] { "o", "int", offsets.toString() })) {
            var h = "h" + p + part[0];
            var d = "d" + p + part[0];
            cu.append("  static const ").append(part[1]).append(' ').append(h).append("[] = {").append(part[2]).append("};\n");
            cu.append("  ").append(part[1]).append(" *").append(d).append(";\n  cudaMalloc(&").append(d).append(", sizeof(").append(h).append("));\n");
            cu.append("  cudaMemcpy(").append(d).append(", ").append(h).append(", sizeof(").append(h).append("), cudaMemcpyHostToDevice);\n");
        }
    }

}
