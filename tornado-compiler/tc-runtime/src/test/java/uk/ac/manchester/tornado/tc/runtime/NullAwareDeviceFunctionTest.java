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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * The null-aware and error modes of a device function ({@code tc emit --device-fn --null-aware
 * --errors}): the signatures cudf::transform calls with {@code null_aware::YES} and with an error
 * result, what each mode refuses, and the rows on the GPU against the Java methods themselves --
 * nulls in and out, and ArithmeticException and NullPointerException as cudf's error codes.
 */
public class NullAwareDeviceFunctionTest {

    private static final String KERNELS = "uk.ac.manchester.tornado.tc.frontend.kernels.NullableUdfKernels";
    private static final Path CLASSES = Path.of("../tc-frontend/target/test-classes");
    private static final TcOptions NULL_AWARE = TcOptions.DEFAULTS.withNullAwareDeviceFunction(true);
    private static final TcOptions ERRORS = TcOptions.DEFAULTS.withDeviceFunctionErrors(true);
    private static final TcOptions BOTH = NULL_AWARE.withDeviceFunctionErrors(true);

    static TcCompiler.Compilation compile(String method, TcOptions options) {
        return TcCompiler.compileDeviceFunction(Frontend.BytecodeSource.classpath(CLASSES.toString()), KERNELS, method, null, TargetCaps.CUDA_SM89, options);
    }

    private static String source(String method, TcOptions options) {
        var c = compile(method, options);
        assertTrue(c.succeeded(), method + ": " + c.diagnostics());
        return c.source();
    }

    private static void refused(String method, TcOptions options, String code) {
        var c = compile(method, options);
        assertFalse(c.succeeded(), method + " compiled:\n" + c.source());
        assertEquals(code, c.diagnostics().get(0).code(), method + ": " + c.diagnostics());
    }

    @Test
    public void nullAwareSignaturesAreOptionalsAndReturnAnErrorCode() {
        var src = source("plusOne", NULL_AWARE);
        assertTrue(src.startsWith("#include <cuda/std/optional>\n__device__ int NullableUdfKernels_plusOne(cuda::std::optional<int> *out, cuda::std::optional<int> a_opt)"), src);
        var renamed = new String[1];
        DeviceFunctionTest.cudfRename(src, "GENERIC_TRANSFORM_OP", renamed);
        assertEquals("NullableUdfKernels_plusOne", renamed[0], src);
        assertTrue(source("ratio", NULL_AWARE).contains("(cuda::std::optional<double> *out, cuda::std::optional<long long> a_opt, cuda::std::optional<int> b_opt)"));
        assertTrue(source("isPositive", NULL_AWARE).contains("(cuda::std::optional<bool> *out, cuda::std::optional<double> d_opt)"));
        assertTrue(source("shortSum", NULL_AWARE).contains("(cuda::std::optional<short> *out, cuda::std::optional<short> a_opt, cuda::std::optional<signed char> b_opt, "
                + "cuda::std::optional<unsigned short> c_opt)"));
        // A primitive argument: null in, null out, without running the method.
        assertTrue(source("plainTwice", NULL_AWARE).contains("if (!a_opt.has_value()) { *out = cuda::std::nullopt; return 0; }"));
        // A string row: an optional string_view.
        assertTrue(source("nullableLength", NULL_AWARE).contains("cuda::std::optional<cudf::string_view> s_opt"));
        // Unboxing a null is Java's NullPointerException: error 3, which cudf reports as UNKNOWN_ERROR.
        assertTrue(source("unboxed", NULL_AWARE).contains("return 3;"));
    }

    @Test
    public void arithmeticExceptionsAreErrorCodes() {
        assertTrue(source("addExact", ERRORS).startsWith("__device__ int NullableUdfKernels_addExact(int *out, int a, int b)"), source("addExact", ERRORS));
        assertTrue(source("addExact", ERRORS).contains("return 1;"), source("addExact", ERRORS));
        assertTrue(source("divide", ERRORS).contains("if (b == 0) return 2;"), source("divide", ERRORS));
        assertTrue(source("floorMod", ERRORS).contains("return 2;"), source("floorMod", ERRORS));
        assertTrue(source("checked", ERRORS).contains("return 1;"), source("checked", ERRORS));
        // Without --errors: the plain shape, unchanged.
        assertTrue(source("divide", TcOptions.DEFAULTS).startsWith("__device__ void NullableUdfKernels_divide(int *out, int a, int b)"));
        assertFalse(source("divide", TcOptions.DEFAULTS).contains("return 2"));
    }

    @Test
    public void whatEachModeStillRefuses() {
        refused("addExact", TcOptions.DEFAULTS, "TC-E3506");
        refused("checked", NULL_AWARE, "TC-E3506");
        refused("notArithmetic", BOTH, "TC-E1011");
        refused("sameBox", BOTH, "TC-E3505");
        refused("plusOne", ERRORS, "TC-E3501");
    }

    // ---- on the GPU

    private static List<Object[]> rows(int n, IntFunction<Object[]> row) {
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < n; i++) {
            rows.add(row.apply(i));
        }
        return rows;
    }

    private static long mix(int i) {
        long z = 0x9E3779B97F4A7C15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        return z ^ (z >>> 27);
    }

    /** Every {@code k}-th row null, the others {@code v}. */
    private static <T> T nullEvery(int i, int k, T v) {
        return i % k == 0 ? null : v;
    }

    @Test
    public void runsOnTheGpuAsInJava(@TempDir Path dir) throws Exception {
        // Null-aware output includes <cuda/std/optional>, so a toolkit without CCCL is no use here even
        // though it compiles everything else: the probe builds with that include ahead of it.
        var toolkit = CudaProbe.find(dir, "#include <cuda/std/optional>\n");
        if (System.getenv("TC_REQUIRE_GPU") != null) {
            assertTrue(toolkit.usable(), "TC_REQUIRE_GPU is set, but nothing here can build and run a null-aware CUDA kernel: " + toolkit.why());
        }
        assumeTrue(toolkit.nvcc().isPresent(), "no nvcc");
        assumeTrue(toolkit.cc().isPresent(), "no GPU");
        assumeTrue(toolkit.usable(), "nothing this nvcc builds reaches this GPU: " + toolkit.why());
        try (var loader = new URLClassLoader(new URL[] { CLASSES.toUri().toURL() }, getClass().getClassLoader())) {
            var k = loader.loadClass(KERNELS);
            var run = new Gpu(dir, toolkit.nvccPath(), toolkit.architecture(), k);
            run.check("plusOne", NULL_AWARE, rows(300, i -> new Object[] { nullEvery(i, 5, i == 7 ? Integer.MAX_VALUE : (int) mix(i)) }));
            run.check("coalesce", NULL_AWARE, rows(300, i -> new Object[] { nullEvery(i, 3, mix(i)), nullEvery(i, 2, mix(i + 1)) }));
            run.check("ratio", NULL_AWARE, rows(300, i -> new Object[] { nullEvery(i, 7, mix(i)), nullEvery(i, 5, (int) (mix(i + 3) % 4)) }));
            run.check("isPositive", NULL_AWARE, rows(9, i -> new Object[] { new Double[] { null, 1.0, -1.0, 0.0, -0.0, Double.NaN, Double.NEGATIVE_INFINITY, 1e-300, null }[i] }));
            run.check("unboxed", NULL_AWARE, rows(100, i -> new Object[] { nullEvery(i, 17, (int) mix(i)) }));
            run.check("plainTwice", NULL_AWARE, rows(100, i -> new Object[] { nullEvery(i, 4, mix(i)) }));
            run.check("nullableLength", NULL_AWARE, rows(200, i -> new Object[] { nullEvery(i, 6, new byte[i % 9]) }));
            run.check("shortSum", NULL_AWARE,
                    rows(300, i -> new Object[] { nullEvery(i, 11, (short) mix(i)), nullEvery(i, 13, (byte) mix(i + 1)), nullEvery(i, 7, (char) mix(i + 2)) }));
            run.check("safeAdd", BOTH, rows(300, i -> new Object[] { nullEvery(i, 9, i % 50 == 1 ? Integer.MAX_VALUE : (int) mix(i)), nullEvery(i, 8, (int) (mix(i + 5) % 1000)) }));
            run.check("addExact", ERRORS, rows(300, i -> new Object[] { i % 40 == 1 ? Integer.MAX_VALUE : (int) mix(i), i % 40 == 1 ? 1 : (int) (mix(i + 1) % 100000) }));
            run.check("multiplyExact", ERRORS, rows(300, i -> new Object[] { mix(i) >> (i % 64), mix(i + 1) >> (i % 61) }));
            run.check("toIntExact", ERRORS, rows(300, i -> new Object[] { mix(i) >> (30 + i % 4) }));
            run.check("divide", ERRORS, rows(300, i -> new Object[] { i == 3 ? Integer.MIN_VALUE : (int) mix(i), i == 3 ? -1 : (int) (mix(i + 1) % 5) }));
            run.check("remainder", ERRORS, rows(300, i -> new Object[] { mix(i), mix(i + 1) % 4 }));
            run.check("floorMod", ERRORS, rows(300, i -> new Object[] { mix(i), (int) (mix(i + 1) % 7) }));
            run.check("checked", ERRORS, rows(300, i -> new Object[] { (int) mix(i) }));
        }
    }

    /** The error code Java's exception becomes (cudf::errc: 1 OVERFLOW, 2 DIVISION_BY_ZERO; 3 a null unboxed). */
    static int errorOf(Throwable t) {
        if (t instanceof ArithmeticException a) {
            return a.getMessage() != null && a.getMessage().contains("by zero") ? 2 : 1;
        }
        if (t instanceof NullPointerException) {
            return 3;
        }
        throw new AssertionError("unexpected " + t, t);
    }

    /** What Java gives for one row: an error code, or a value (null for SQL NULL). */
    record Expected(int error, Object value) {
    }

    /**
     * Java's result for {@code args}: a null-aware function's primitive or string argument that is
     * null makes the result null without a call (cudf passes it; the device function returns null).
     */
    static Expected expected(Method m, boolean nullAware, Object[] args) {
        if (nullAware) {
            var types = m.getParameterTypes();
            for (int p = 0; p < types.length; p++) {
                if ((types[p].isPrimitive() || types[p] == byte[].class) && args[p] == null) {
                    return new Expected(0, null);
                }
            }
        }
        try {
            return new Expected(0, m.invoke(null, args));
        } catch (InvocationTargetException e) {
            return new Expected(errorOf(e.getCause()), null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static String cType(Class<?> c) {
        if (c == boolean.class || c == Boolean.class) {
            return "bool";
        }
        if (c == byte.class || c == Byte.class) {
            return "signed char";
        }
        if (c == char.class || c == Character.class) {
            return "unsigned short";
        }
        if (c == short.class || c == Short.class) {
            return "short";
        }
        if (c == int.class || c == Integer.class) {
            return "int";
        }
        if (c == long.class || c == Long.class) {
            return "long long";
        }
        return c == float.class || c == Float.class ? "float" : "double";
    }

    private static String literal(Object v) {
        return switch (v) {
            case null -> "0";
            case Boolean b -> b ? "true" : "false";
            case Long l -> l == Long.MIN_VALUE ? "(-9223372036854775807LL - 1)" : l + "LL";
            case Integer n -> n == Integer.MIN_VALUE ? "(-2147483647 - 1)" : n.toString();
            case Short s -> "(short) " + s;
            case Byte b -> "(signed char) " + b;
            case Character c -> "(unsigned short) " + (int) c;
            case Double d -> "bits_to_double(0x" + Long.toHexString(Double.doubleToRawLongBits(d)) + "ULL)";
            default -> throw new IllegalArgumentException(v.toString());
        };
    }

    /** One nvcc-built program per method: it calls the device function as cudf's kernel does, and prints each row. */
    private record Gpu(Path dir, Path nvcc, CudaProbe.Arch arch, Class<?> kernels) {

        void check(String method, TcOptions options, List<Object[]> rows) throws Exception {
            var c = compile(method, options);
            assertTrue(c.succeeded(), method + ": " + c.diagnostics());
            var java = Arrays.stream(kernels.getDeclaredMethods()).filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
            boolean nullAware = options.deviceFunctionModes().nullAware();
            var params = java.getParameterTypes();
            var r = cType(java.getReturnType());
            var cu = new StringBuilder();
            cu.append("#include <cstdio>\n#include <cstring>\n#include <cuda/std/optional>\n");
            cu.append("static double bits_to_double(unsigned long long b) { double d; std::memcpy(&d, &b, 8); return d; }\n");
            cu.append("namespace cudf { struct string_view { const char *d; int n;\n");
            cu.append("  __device__ string_view(const char *data, int bytes) : d(data), n(bytes) {}\n");
            cu.append("  __device__ const char *data() const { return d; }\n  __device__ int size_bytes() const { return n; } }; }\n");
            cu.append(c.source()).append('\n');
            cu.append("__global__ void k(").append(r).append(" *outv, unsigned char *outvalid, int *err");
            for (int p = 0; p < params.length; p++) {
                if (params[p] == byte[].class) {
                    cu.append(", const unsigned char *in").append(p).append("c, const int *in").append(p).append('o');
                } else {
                    cu.append(", const ").append(cType(params[p])).append(" *in").append(p);
                }
                cu.append(", const unsigned char *v").append(p);
            }
            cu.append(", int n) {\n  int i = blockIdx.x * blockDim.x + threadIdx.x;\n  if (i >= n) return;\n");
            var args = new StringBuilder();
            for (int p = 0; p < params.length; p++) {
                var value = params[p] == byte[].class ? "cudf::string_view((const char *) in" + p + "c + in" + p + "o[i], in" + p + "o[i + 1] - in" + p + "o[i])" : "in" + p + "[i]";
                var type = params[p] == byte[].class ? "cudf::string_view" : cType(params[p]);
                args.append(", ").append(nullAware ? "v" + p + "[i] ? cuda::std::optional<" + type + ">(" + value + ") : cuda::std::optional<" + type + ">()" : value);
            }
            if (nullAware) {
                cu.append("  cuda::std::optional<").append(r).append("> o;\n  err[i] = ").append(c.entryName()).append("(&o").append(args).append(");\n");
                cu.append("  outvalid[i] = o.has_value();\n  outv[i] = o.has_value() ? *o : (").append(r).append(") 0;\n}\n");
            } else {
                cu.append("  ").append(r).append(" o = 0;\n  err[i] = ").append(c.entryName()).append("(&o").append(args).append(");\n  outvalid[i] = 1;\n  outv[i] = o;\n}\n");
            }
            cu.append("int main() {\n  const int n = ").append(rows.size()).append(";\n");
            var launch = new StringBuilder();
            for (int p = 0; p < params.length; p++) {
                var valid = new StringBuilder();
                for (var row : rows) {
                    valid.append(row[p] == null ? 0 : 1).append(", ");
                }
                array(cu, "v" + p, "unsigned char", valid.toString());
                if (params[p] == byte[].class) {
                    var chars = new StringBuilder();
                    var offsets = new StringBuilder("0, ");
                    int at = 0;
                    for (var row : rows) {
                        var bytes = row[p] == null ? new byte[0] : (byte[]) row[p];
                        for (byte b : bytes) {
                            chars.append(b & 0xff).append(", ");
                        }
                        at += bytes.length;
                        offsets.append(at).append(", ");
                    }
                    array(cu, "s" + p + "c", "unsigned char", chars.append('0').toString());
                    array(cu, "s" + p + "o", "int", offsets.toString());
                    launch.append(", d_s").append(p).append("c, d_s").append(p).append('o');
                } else {
                    var values = new StringBuilder();
                    for (var row : rows) {
                        values.append(literal(row[p])).append(", ");
                    }
                    array(cu, "a" + p, cType(params[p]), values.toString());
                    launch.append(", d_a").append(p);
                }
                launch.append(", d_v").append(p);
            }
            cu.append("  ").append(r).append(" *outv, houtv[n];\n  unsigned char *outvalid, houtvalid[n];\n  int *err, herr[n];\n");
            cu.append("  cudaMalloc(&outv, sizeof(houtv));\n  cudaMalloc(&outvalid, sizeof(houtvalid));\n  cudaMalloc(&err, sizeof(herr));\n");
            cu.append("  k<<<(n + 127) / 128, 128>>>(outv, outvalid, err").append(launch).append(", n);\n");
            cu.append("  if (cudaMemcpy(houtv, outv, sizeof(houtv), cudaMemcpyDeviceToHost) != cudaSuccess) { std::printf(\"NO-GPU\\n\"); return 3; }\n");
            cu.append("  cudaMemcpy(houtvalid, outvalid, sizeof(houtvalid), cudaMemcpyDeviceToHost);\n  cudaMemcpy(herr, err, sizeof(herr), cudaMemcpyDeviceToHost);\n");
            cu.append("  for (int i = 0; i < n; i++) {\n");
            if (r.equals("double")) {
                cu.append("    unsigned long long b; std::memcpy(&b, &houtv[i], 8); std::printf(\"%d %d %llu\\n\", herr[i], houtvalid[i], b);\n");
            } else {
                cu.append("    std::printf(\"%d %d %lld\\n\", herr[i], houtvalid[i], (long long) houtv[i]);\n");
            }
            cu.append("  }\n  return 0;\n}\n");
            var file = dir.resolve(method + ".cu");
            Files.writeString(file, cu);
            var exe = dir.resolve(method);
            var build = CudaProbe.run(dir, List.of(nvcc.toString(), "-std=c++17", "-arch=" + arch.flag(), "-o", exe.toString(), file.toString()));
            assertEquals(0, build.exit(), method + ": nvcc failed (" + nvcc + " -arch=" + arch.flag() + ")\n" + build.output() + "\n" + cu);
            // This architecture already built and ran a kernel on this GPU (CudaProbe.find), so a binary
            // that cannot reach it now is a failure, not a skip.
            var ran = CudaProbe.run(dir, List.of(exe.toString()));
            assertEquals(0, ran.exit(), ran.output());
            var lines = ran.output().strip().lines().toList();
            assertEquals(rows.size(), lines.size(), ran.output());
            int errors = 0;
            for (int i = 0; i < rows.size(); i++) {
                var want = expected(java, nullAware, rows.get(i));
                var got = lines.get(i).strip().split(" ");
                var where = method + " row " + i + " " + Arrays.toString(rows.get(i));
                assertEquals(want.error(), Integer.parseInt(got[0]), where + ": error code");
                if (want.error() != 0) {
                    errors++;
                    continue; // the row failed: cudf throws for the call, whatever the output holds
                }
                assertEquals(want.value() == null ? 0 : 1, Integer.parseInt(got[1]), where + ": validity, Java " + want.value());
                if (want.value() != null) {
                    long gpu = r.equals("double") ? Long.parseUnsignedLong(got[2]) : Long.parseLong(got[2]);
                    long javaBits = switch (want.value()) {
                        case Double d -> Double.doubleToRawLongBits(d);
                        case Boolean b -> b ? 1 : 0;
                        case Character ch -> ch;
                        case Number num -> num.longValue();
                        default -> throw new IllegalStateException(want.value().toString());
                    };
                    assertEquals(javaBits, gpu, where + ": Java " + want.value());
                }
            }
            if (options.deviceFunctionModes().errors() || method.equals("unboxed")) {
                assertTrue(errors > 0, method + ": no row fails, so the error path is not tested");
            }
        }

        private static void array(StringBuilder cu, String name, String type, String values) {
            cu.append("  static const ").append(type).append(" h_").append(name).append("[] = {").append(values.isEmpty() ? "0" : values).append("};\n");
            cu.append("  ").append(type).append(" *d_").append(name).append(";\n  cudaMalloc(&d_").append(name).append(", sizeof(h_").append(name).append("));\n");
            cu.append("  cudaMemcpy(d_").append(name).append(", h_").append(name).append(", sizeof(h_").append(name).append("), cudaMemcpyHostToDevice);\n");
        }
    }

    static CudaProbe.Ran run(List<String> command) throws IOException, InterruptedException {
        return CudaProbe.run(null, command, null);
    }

    /** {@code libraryPath}: the LD_LIBRARY_PATH to run with, or null to inherit it. */
    static CudaProbe.Ran run(List<String> command, String libraryPath) throws IOException, InterruptedException {
        return CudaProbe.run(null, command, libraryPath);
    }
}
