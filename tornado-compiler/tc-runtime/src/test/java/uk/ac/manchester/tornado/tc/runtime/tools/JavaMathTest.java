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
 */
package uk.ac.manchester.tornado.tc.runtime.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleBinaryOperator;
import java.util.regex.Pattern;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.runtime.CudaProbe;

/**
 * {@code tc emit --java-math}: the functions StrictMath specifies, called through Math, are compiled
 * from the JDK's FdLibm code (with its tables as constant arrays) and are bit-identical to StrictMath.
 */
public class JavaMathTest {

    private static final String API = "../../tornado-api/target/classes";

    /** Every StrictMath-specified function through Math, plus sqrt (the device's, correctly rounded). */
    private static final List<String> FUNCTIONS = List.of("sin", "cos", "tan", "asin", "acos", "atan", "exp", "log", "log10", "cbrt", "sinh", "cosh", "tanh", "sqrt", "pow",
            "atan2");

    private static final List<DoubleBinaryOperator> STRICT = List.of((a, b) -> StrictMath.sin(a), (a, b) -> StrictMath.cos(a), (a, b) -> StrictMath.tan(a),
            (a, b) -> StrictMath.asin(a), (a, b) -> StrictMath.acos(a), (a, b) -> StrictMath.atan(a), (a, b) -> StrictMath.exp(a), (a, b) -> StrictMath.log(a),
            (a, b) -> StrictMath.log10(a), (a, b) -> StrictMath.cbrt(a), (a, b) -> StrictMath.sinh(a), (a, b) -> StrictMath.cosh(a), (a, b) -> StrictMath.tanh(a),
            (a, b) -> StrictMath.sqrt(a), StrictMath::pow, StrictMath::atan2);

    private static String kernelSource() {
        var body = new StringBuilder();
        for (int k = 0; k < FUNCTIONS.size(); k++) {
            var fn = FUNCTIONS.get(k);
            var call = fn.equals("pow") || fn.equals("atan2") ? "Math." + fn + "(a, b)" : "Math." + fn + "(a)";
            body.append("            out.set(").append(FUNCTIONS.size()).append(" * i + ").append(k).append(", ").append(call).append(");\n");
        }
        return String.join("\n", //
                "import uk.ac.manchester.tornado.api.annotations.Parallel;", //
                "import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;", //
                "class JavaMath {", //
                "    static void all(DoubleArray x, DoubleArray y, DoubleArray out) {", //
                "        for (@Parallel int i = 0; i < x.getSize(); i++) {", //
                "            double a = x.get(i);", //
                "            double b = y.get(i);", //
                body + "        }", //
                "    }", //
                "}", "");
    }

    private record Result(int status, String out, String err) {
    }

    private static Result emit(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = Tc.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String classPath(Path dir) throws IOException {
        var file = dir.resolve("JavaMath.java");
        Files.writeString(file, kernelSource());
        var options = new ArrayList<>(List.of("-g", "-parameters", "-cp", API, "-d", dir.toString(), file.toString()));
        if (Runtime.version().feature() == 21) {
            options.addAll(0, List.of("--release", "21", "--enable-preview"));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)), "javac failed");
        return dir + File.pathSeparator + API;
    }

    /** A call to a device math function other than sqrt (the device's sqrt is exact, so it stays). */
    private static final Pattern DEVICE_MATH = Pattern.compile("\\b(sin|cos|tan|asin|acos|atan|atan2|exp|log|log10|cbrt|sinh|cosh|tanh|pow)\\s*\\(");

    @Test
    public void javaMathCompilesFdLibmWithItsTablesAsConstants(@TempDir Path dir) throws IOException {
        var cp = classPath(dir);
        var plain = emit("emit", "--cp", cp, "JavaMath", "all");
        assertEquals(0, plain.status(), plain.err());
        assertTrue(DEVICE_MATH.matcher(plain.out()).find(), "without --java-math, Math goes to the device library");

        var exact = emit("emit", "--java-math", "--cp", cp, "JavaMath", "all");
        assertEquals(0, exact.status(), exact.err());
        assertFalse(DEVICE_MATH.matcher(exact.out()).find(), exact.out());
        assertTrue(exact.out().contains("sqrt("), "sqrt stays the device's");
        // FdLibm's tables (sin's npio2_hw and two_over_pi, atan's atanhi, ...) as constants, not per-thread arrays.
        assertTrue(exact.out().contains("static const int "), exact.out());
        assertTrue(exact.out().contains("static const double "), exact.out());
        // Java-exact math implies strict floating point: no product may be contracted into an fma.
        assertTrue(exact.out().contains("__dmul_rn("), exact.out());

        var opencl = emit("emit", "--target", "opencl", "--java-math", "--cp", cp, "JavaMath", "all");
        assertEquals(0, opencl.status(), opencl.err());
        assertTrue(opencl.out().contains("__constant int "), opencl.out());
        assertTrue(opencl.out().contains("#pragma OPENCL FP_CONTRACT OFF"), opencl.out());

        // Metal has no doubles; with them assumed, the tables are what it refuses (not declarable there yet).
        var metal = emit("emit", "--target", "metal", "--fp64", "on", "--java-math", "--cp", cp, "JavaMath", "all");
        assertEquals(1, metal.status(), metal.out());
        assertTrue(metal.err().contains("constant tables"), metal.err());
    }

    // ---------------------------------------------------------------- on a GPU

    /** Runs the emitted kernel once over x and y and writes out (16 results per element); prints nothing. */
    private static final String HARNESS = String.join("\n", //
            "#include <cstdio>", //
            "#include <cstdlib>", //
            "#include <cstring>", //
            "#include <vector>", //
            "#include <cuda_runtime.h>", //
            "static unsigned char *up(const std::vector<double> &v) {", //
            "  size_t bytes = 16 + v.size() * 8; std::vector<unsigned char> h(bytes, 0); long long n = (long long) v.size();", //
            "  memcpy(h.data(), &n, 8); memcpy(h.data() + 16, v.data(), v.size() * 8);", //
            "  unsigned char *d; if (cudaMalloc(&d, bytes) != cudaSuccess) exit(2); cudaMemcpy(d, h.data(), bytes, cudaMemcpyHostToDevice); return d; }", //
            "static std::vector<double> rd(const char *p, size_t n) {", //
            "  std::vector<double> v(n); FILE *f = fopen(p, \"rb\"); if (!f || fread(v.data(), 8, n, f) != n) exit(3); fclose(f); return v; }", //
            "int main(int argc, char **argv) {", //
            "  size_t n = strtoull(argv[1], 0, 10), w = strtoull(argv[2], 0, 10);", //
            "  auto x = up(rd(argv[3], n)); auto y = up(rd(argv[4], n)); auto o = up(std::vector<double>(n * w, 0.0));", //
            "  JavaMath_all<<<(unsigned) ((n + 255) / 256), 256>>>(0, 0, 0, 0, x, y, o);", //
            // The launch's own error, before the sync: a kernel the driver rejects (built for another
            // architecture, or PTX the driver is too old to compile) leaves cudaDeviceSynchronize and the
            // copy below both returning cudaSuccess, and the output buffer holding the zeros it was filled
            // with -- which would be compared against StrictMath as though the kernel had run.
            "  if (cudaGetLastError() != cudaSuccess) exit(4);", //
            "  if (cudaDeviceSynchronize() != cudaSuccess) exit(4);", //
            "  std::vector<double> r(n * w); cudaMemcpy(r.data(), o + 16, n * w * 8, cudaMemcpyDeviceToHost);", //
            "  FILE *f = fopen(argv[5], \"wb\"); fwrite(r.data(), 8, n * w, f); fclose(f); return 0; }", "");

    private static final double[] SPECIAL = { 0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, 0x0.fffffffffffffp-1022, Double.MIN_NORMAL, -Double.MIN_NORMAL, Double.MAX_VALUE,
            -Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, 1, -1, 0.5, -0.5, 2, -2, 3, 1e-300, 1e300, Math.PI, -Math.PI, Math.PI / 2, Math.PI / 4,
            1e22, 0x1p19 * Math.PI / 2, 0x1p20 * Math.PI, 709.782712893384, -745.1332191019411, 710, -746, 1.0000000000000002, 0.9999999999999999, 0x1p53, 0x1p53 + 2, 0.1 };

    private static void write(Path p, double[] v) throws IOException {
        var buf = ByteBuffer.allocate(v.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        buf.asDoubleBuffer().put(v);
        Files.write(p, buf.array());
    }

    /**
     * Every function on 2^18 inputs (edge cases, every pair of them for pow and atan2, random bit
     * patterns and in-range values) on the GPU, compiled by nvcc with its default flags (which
     * contract a * b + c): each result must have StrictMath's bits. Skipped without nvcc or a GPU.
     */
    @Test
    public void javaMathIsBitIdenticalToStrictMathOnTheGpu(@TempDir Path dir) throws Exception {
        var toolkit = CudaProbe.find(dir, "");
        // Skipped without nvcc or a GPU, and when the two cannot be made to talk to each other. Set
        // TC_REQUIRE_GPU=1 where all three are expected, so that losing one fails the build instead
        // of skipping the check quietly.
        if (System.getenv("TC_REQUIRE_GPU") != null) {
            assertTrue(toolkit.usable(), "TC_REQUIRE_GPU is set, but nothing here can build and run a CUDA kernel: " + toolkit.why());
        }
        assumeTrue(toolkit.nvcc().isPresent(), "no nvcc");
        assumeTrue(toolkit.cc().isPresent(), "no GPU");
        assumeTrue(toolkit.usable(), "nothing this nvcc builds reaches this GPU: " + toolkit.why());
        var cp = classPath(dir);
        var exact = emit("emit", "--java-math", "--cp", cp, "JavaMath", "all");
        assertEquals(0, exact.status(), exact.err());
        Files.writeString(dir.resolve("kernel.cu"), exact.out());
        Files.writeString(dir.resolve("harness.cu"), HARNESS);
        // The architecture comes from CudaProbe, which has already built and run something for it here.
        // -arch=native would be nvcc's own guess, and it fails outright on a card this toolkit predates.
        // Absolute paths throughout: CudaProbe.run does not set a working directory, by design -- it
        // only decides where the log goes.
        var built = CudaProbe.run(dir, List.of(toolkit.nvccPath().toString(), "-O3", "-arch=" + toolkit.architecture().flag(), "-include", dir.resolve("kernel.cu").toString(), "-o",
                dir.resolve("harness").toString(), dir.resolve("harness.cu").toString()));
        assertEquals(0, built.exit(), built::output);

        int n = 1 << 18;
        int w = FUNCTIONS.size();
        double[] x = new double[n];
        double[] y = new double[n];
        var r = new Random(42);
        int k = 0;
        for (double a : SPECIAL) {
            for (double b : SPECIAL) {
                x[k] = a;
                y[k++] = b;
            }
        }
        for (; k < n; k++) {
            switch (k % 3) {
                case 0 -> {
                    x[k] = Double.longBitsToDouble(r.nextLong());
                    y[k] = Double.longBitsToDouble(r.nextLong());
                }
                case 1 -> {
                    x[k] = (r.nextDouble() - 0.5) * 40;
                    y[k] = (r.nextDouble() - 0.5) * 40;
                }
                default -> {
                    x[k] = (r.nextBoolean() ? 1 : -1) * Math.pow(10, -20 + 40 * r.nextDouble());
                    y[k] = r.nextInt(41) - 20 + (r.nextInt(4) == 0 ? 0.5 : 0);
                }
            }
        }
        write(dir.resolve("x.bin"), x);
        write(dir.resolve("y.bin"), y);
        var ran = CudaProbe.run(dir, List.of(dir.resolve("harness").toString(), String.valueOf(n), String.valueOf(w), dir.resolve("x.bin").toString(), dir.resolve("y.bin").toString(),
                dir.resolve("out.bin").toString()));
        // Not an assumption any more: CudaProbe has already proved this toolkit and this GPU run a
        // kernel together, so a failure here is a failure of the code under test. Exit 2 is cudaMalloc,
        // 4 the launch or the sync -- the case that used to skip quietly and report success.
        assertEquals(0, ran.exit(), ran::output);
        var out = ByteBuffer.wrap(Files.readAllBytes(dir.resolve("out.bin"))).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
        var failures = new ArrayList<String>();
        for (int f = 0; f < w; f++) {
            long bad = 0;
            String first = null;
            for (int i = 0; i < n; i++) {
                double got = out.get(i * w + f);
                double want = STRICT.get(f).applyAsDouble(x[i], y[i]);
                boolean same = Double.doubleToRawLongBits(got) == Double.doubleToRawLongBits(want) || (Double.isNaN(got) && Double.isNaN(want));
                if (!same) {
                    bad++;
                    if (first == null) {
                        first = FUNCTIONS.get(f) + "(" + x[i] + ", " + y[i] + ") = " + got + ", StrictMath " + want;
                    }
                }
            }
            if (bad > 0) {
                failures.add(FUNCTIONS.get(f) + ": " + bad + " of " + n + " differ, first " + first);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

}
