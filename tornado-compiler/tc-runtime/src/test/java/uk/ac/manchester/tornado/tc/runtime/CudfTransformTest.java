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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.check.ExternalTool;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * TC device functions through the real {@code cudf::transform_extended} (libcudf's JIT), compared
 * row by row with the Java methods: the plain shape, string rows, the null-aware shape
 * ({@code null_aware::YES}) and the error result ({@code cudf::evaluation_error}).
 *
 * <p>
 * Opt-in: it runs only when {@code CUDF_HOME} names a libcudf prefix (with {@code include/} and
 * {@code lib64/}), with g++ and a GPU. {@code RMM_HOME} and {@code RAPIDS_LOGGER_HOME} default to the
 * {@code librmm} and {@code rapids_logger} next to it (the layout of the RAPIDS wheels), {@code CUDA_HOME}
 * to {@code /usr/local/cuda}; {@code CUDF_LD_LIBRARY_PATH} is added to the library path libcudf runs
 * with (its own dependencies: kvikio, nvcomp, ...), and {@code CUDF_ROWS} sets the rows of the large
 * cases (default 10,000,000). The harness is {@code src/test/resources/cudf/transform_check.cpp}.
 */
public class CudfTransformTest {

    private static final Path CLASSES = Path.of("../tc-frontend/target/test-classes");
    private static final String UDFS = "uk.ac.manchester.tornado.tc.frontend.kernels.UdfKernels";
    private static final String NULLABLE = "uk.ac.manchester.tornado.tc.frontend.kernels.NullableUdfKernels";

    /** A column: its Java type and row {@code i}'s value (null for SQL NULL). */
    record Column(Class<?> type, IntFunction<Object> value) {
    }

    private static long mix(int i) {
        long z = 0x9E3779B97F4A7C15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        return z ^ (z >>> 27);
    }

    private static byte[] bytes(int i) {
        var b = new byte[i % 41];
        for (int k = 0; k < b.length; k++) {
            b[k] = (byte) mix(i * 64 + k);
        }
        return b;
    }

    private static IntFunction<Object> nullEvery(int k, IntFunction<Object> v) {
        return i -> i % k == 0 ? null : v.apply(i);
    }

    @Test
    public void transformsAsInJava(@TempDir Path dir) throws Exception {
        var cudfHome = System.getenv("CUDF_HOME");
        assumeTrue(cudfHome != null && !cudfHome.isBlank(), "CUDF_HOME is not set: the cudf::transform integration test is opt-in");
        var gxx = ExternalTool.find("g++");
        assumeTrue(gxx.isPresent(), "no g++");
        var harness = build(dir, gxx.get(), Path.of(cudfHome));
        int large = Integer.parseInt(System.getenv().getOrDefault("CUDF_ROWS", "10000000"));
        int small = Math.min(large, 1_000_000);
        var table = new StringBuilder("| function | mode | rows | transform ms | mismatches |\n|---|---|---|---|---|\n");
        try (var loader = new URLClassLoader(new URL[] { CLASSES.toUri().toURL() }, getClass().getClassLoader())) {
            var run = new Run(dir, harness, loader, table);
            var none = TcOptions.DEFAULTS;
            var nullAware = TcOptions.DEFAULTS.withNullAwareDeviceFunction(true);
            var errors = TcOptions.DEFAULTS.withDeviceFunctionErrors(true);
            // The plain shape (the cudf-spark spike's UDFs), at full size.
            run.values(UDFS, "collatz", none, large, new Column(long.class, i -> Math.floorMod(mix(i), 100_000_000L) + 1));
            run.values(UDFS, "bucket", none, large, new Column(long.class, i -> mix(i)));
            run.values(UDFS, "mixed", none, large, new Column(int.class, i -> (int) mix(i)), new Column(long.class, i -> mix(i + 7)), new Column(short.class, i -> (short) mix(i + 3)),
                    new Column(byte.class, i -> (byte) mix(i + 5)));
            // Not null-aware, with nulls: cudf skips those rows and the result is null there.
            run.values(UDFS, "bucket", none, small, new Column(long.class, nullEvery(9, i -> mix(i))));
            // String rows.
            run.values(UDFS, "fnv1a", none, small, new Column(byte[].class, CudfTransformTest::bytes));
            run.values(UDFS, "murmur3", none, small, new Column(byte[].class, CudfTransformTest::bytes), new Column(int.class, i -> (int) mix(i + 11)));
            // Null-aware.
            run.values(NULLABLE, "plusOne", nullAware, small, new Column(Integer.class, nullEvery(5, i -> (int) mix(i))));
            run.values(NULLABLE, "ratio", nullAware, small, new Column(Long.class, nullEvery(7, i -> mix(i))), new Column(Integer.class, nullEvery(5, i -> (int) (mix(i + 3) % 4))));
            run.values(NULLABLE, "coalesce", nullAware, small, new Column(Long.class, nullEvery(3, i -> mix(i))), new Column(Long.class, nullEvery(2, i -> mix(i + 1))));
            run.values(NULLABLE, "isPositive", nullAware, small, new Column(Double.class, nullEvery(4, i -> (double) mix(i))));
            run.values(NULLABLE, "plainTwice", nullAware, small, new Column(long.class, nullEvery(4, i -> mix(i))));
            run.values(NULLABLE, "nullableLength", nullAware, small, new Column(byte[].class, nullEvery(6, CudfTransformTest::bytes)));
            run.values(NULLABLE, "safeAdd", nullAware.withDeviceFunctionErrors(true), small, new Column(Integer.class, nullEvery(9, i -> (int) (mix(i) % 1_000_000))),
                    new Column(Integer.class, nullEvery(8, i -> (int) (mix(i + 5) % 1000))));
            // Errors: no row fails, then one does and the call fails.
            run.values(NULLABLE, "addExact", errors, small, new Column(int.class, i -> (int) (mix(i) % 1_000_000)), new Column(int.class, i -> (int) (mix(i + 1) % 1_000_000)));
            run.fails(NULLABLE, "addExact", errors, "OVERFLOW", new Column(int.class, i -> i == 500 ? Integer.MAX_VALUE : i), new Column(int.class, i -> 1));
            run.fails(NULLABLE, "divide", errors, "DIVISION_BY_ZERO", new Column(int.class, i -> (int) mix(i)), new Column(int.class, i -> i == 77 ? 0 : 3));
            run.fails(NULLABLE, "safeAdd", nullAware.withDeviceFunctionErrors(true), "OVERFLOW", new Column(Integer.class, i -> i == 9 ? Integer.MIN_VALUE : i),
                    new Column(Integer.class, i -> i == 9 ? -1 : null));
            // A null unboxed (Java's NullPointerException): code 3, past cudf::errc's last, reported as unknown.
            run.fails(NULLABLE, "unboxed", nullAware, "UNKNOWN_ERROR", new Column(Integer.class, nullEvery(100, i -> i)));
        }
        System.out.println("cudf::transform_extended, libcudf at " + cudfHome + ":\n" + table);
    }

    /** Builds the harness against libcudf. */
    private static Path build(Path dir, Path gxx, Path cudf) throws Exception {
        var env = System.getenv();
        var rmm = Path.of(env.getOrDefault("RMM_HOME", cudf.resolveSibling("librmm").toString()));
        var logger = Path.of(env.getOrDefault("RAPIDS_LOGGER_HOME", cudf.resolveSibling("rapids_logger").toString()));
        var cuda = Path.of(env.getOrDefault("CUDA_HOME", "/usr/local/cuda"));
        var source = dir.resolve("transform_check.cpp");
        try (var in = CudfTransformTest.class.getResourceAsStream("/cudf/transform_check.cpp")) {
            Files.write(source, in.readAllBytes());
        }
        var exe = dir.resolve("transform_check");
        var command = List.of(gxx.toString(), "-std=c++20", "-O2", "-o", exe.toString(), source.toString(), "-I" + cudf.resolve("include"), "-I" + cudf.resolve("include/rapids"),
                "-I" + rmm.resolve("include"), "-I" + rmm.resolve("include/rapids"), "-I" + logger.resolve("include"), "-I" + cuda.resolve("include"), "-L" + cudf.resolve("lib64"),
                "-L" + rmm.resolve("lib64"), "-L" + cuda.resolve("lib64"), "-lcudf", "-lrmm", "-lcudart", "-Wl,--allow-shlib-undefined");
        var built = NullAwareDeviceFunctionTest.run(command);
        assertEquals(0, built.exit(), "building the harness failed:\n" + built.output());
        return exe;
    }

    private record Run(Path dir, Path harness, ClassLoader loader, StringBuilder table) {

        private static final Map<Class<?>, String> TYPES = Map.ofEntries(Map.entry(int.class, "INT32"), Map.entry(Integer.class, "INT32"), Map.entry(long.class, "INT64"),
                Map.entry(Long.class, "INT64"), Map.entry(short.class, "INT16"), Map.entry(Short.class, "INT16"), Map.entry(byte.class, "INT8"), Map.entry(Byte.class, "INT8"),
                Map.entry(char.class, "UINT16"), Map.entry(Character.class, "UINT16"), Map.entry(boolean.class, "BOOL8"), Map.entry(Boolean.class, "BOOL8"),
                Map.entry(double.class, "FLOAT64"), Map.entry(Double.class, "FLOAT64"), Map.entry(float.class, "FLOAT32"), Map.entry(Float.class, "FLOAT32"),
                Map.entry(byte[].class, "STRING"));

        private Method method(String cls, String name) throws ClassNotFoundException {
            return Arrays.stream(loader.loadClass(cls).getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        }

        /** Writes the inputs and the spec, runs the harness; its one line of output. */
        private String transform(String cls, String name, TcOptions options, int rows, Column[] columns) throws Exception {
            var c = TcCompiler.compileDeviceFunction(Frontend.BytecodeSource.classpath(CLASSES.toString()), cls, name, null, TargetCaps.CUDA_SM89, options);
            assertTrue(c.succeeded(), name + ": " + c.diagnostics());
            var at = Files.createTempDirectory(dir, name);
            Files.writeString(at.resolve("udf.cu"), c.source());
            var spec = new StringBuilder();
            spec.append("udf ").append(at.resolve("udf.cu")).append("\nrows ").append(rows).append("\nnull_aware ").append(options.deviceFunctionModes().nullAware() ? 1 : 0).append('\n');
            spec.append("output ").append(TYPES.get(method(cls, name).getReturnType())).append(' ').append(at.resolve("out.bin")).append(' ').append(at.resolve("out.valid")).append('\n');
            for (int p = 0; p < columns.length; p++) {
                spec.append(write(at, p, columns[p], rows)).append('\n');
            }
            Files.writeString(at.resolve("spec"), spec);
            var ran = NullAwareDeviceFunctionTest.run(List.of(harness.toString(), at.resolve("spec").toString()), libraryPath());
            assertEquals(0, ran.exit(), name + ": " + ran.output());
            var line = ran.output().strip().lines().reduce((a, b) -> b).orElse("");
            assumeTrue(!line.contains("no CUDA-capable device"), "no GPU");
            return line + "\n" + at;
        }

        /** Runs {@code name} over {@code rows} and compares every row with Java. */
        void values(String cls, String name, TcOptions options, int rows, Column... columns) throws Exception {
            var java = method(cls, name);
            var result = transform(cls, name, options, rows, columns).split("\n");
            assertTrue(result[0].startsWith("ok "), name + ": " + result[0]);
            var at = Path.of(result[1]);
            var data = ByteBuffer.wrap(Files.readAllBytes(at.resolve("out.bin"))).order(ByteOrder.LITTLE_ENDIAN);
            var valid = Files.readAllBytes(at.resolve("out.valid"));
            boolean nullAware = options.deviceFunctionModes().nullAware();
            var type = java.getReturnType();
            int width = data.capacity() / rows;
            long mismatches = 0;
            String first = null;
            var args = new Object[columns.length];
            for (int i = 0; i < rows; i++) {
                boolean anyNull = false;
                for (int p = 0; p < columns.length; p++) {
                    args[p] = columns[p].value().apply(i);
                    anyNull |= args[p] == null;
                }
                // Not null-aware: cudf skips a row with a null input, and the result is null.
                var want = !nullAware && anyNull ? new NullAwareDeviceFunctionTest.Expected(0, null) : NullAwareDeviceFunctionTest.expected(java, nullAware, args);
                assertEquals(0, want.error(), name + " row " + i + " fails in Java: use fails() for it");
                Object got = valid[i] == 0 ? null : read(data, i * width, type);
                if (!same(want.value(), got)) {
                    mismatches++;
                    if (first == null) {
                        first = "row " + i + " " + Arrays.toString(args) + ": Java " + want.value() + ", cudf " + got;
                    }
                }
            }
            var mode = (nullAware ? "null-aware" : "plain") + (options.deviceFunctionModes().errors() ? " + errors" : "");
            table.append("| ").append(name).append(" | ").append(mode).append(" | ").append(rows).append(" | ").append(result[0].split(" ")[1]).append(" | ").append(mismatches).append(" |\n");
            assertEquals(0, mismatches, name + ": " + first);
        }

        /** Runs {@code name} over 1000 rows that include one Java fails: cudf must throw with {@code errc}. */
        void fails(String cls, String name, TcOptions options, String errc, Column... columns) throws Exception {
            var result = transform(cls, name, options, 1000, columns).split("\n");
            assertTrue(result[0].startsWith("error ") && result[0].contains(errc), name + ": expected cudf::evaluation_error " + errc + ", got " + result[0]);
            table.append("| ").append(name).append(" | fails | 1000 | - | evaluation_error ").append(errc).append(" |\n");
        }

        private static Object read(ByteBuffer data, int at, Class<?> type) {
            if (type == int.class || type == Integer.class) {
                return data.getInt(at);
            } else if (type == long.class || type == Long.class) {
                return data.getLong(at);
            } else if (type == short.class || type == Short.class) {
                return data.getShort(at);
            } else if (type == byte.class || type == Byte.class) {
                return data.get(at);
            } else if (type == char.class || type == Character.class) {
                return data.getChar(at);
            } else if (type == boolean.class || type == Boolean.class) {
                return data.get(at) != 0;
            } else if (type == float.class || type == Float.class) {
                return data.getFloat(at);
            }
            return data.getDouble(at);
        }

        private static boolean same(Object want, Object got) {
            if (want instanceof Double d && got instanceof Double g) {
                return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(g);
            }
            return java.util.Objects.equals(want, got);
        }

        /** A column's files and its spec line. */
        private static String write(Path at, int p, Column column, int rows) throws IOException {
            var validity = at.resolve("in" + p + ".valid");
            try (var v = new BufferedOutputStream(Files.newOutputStream(validity), 1 << 20)) {
                for (int i = 0; i < rows; i++) {
                    v.write(column.value().apply(i) == null ? 0 : 1);
                }
            }
            if (column.type() == byte[].class) {
                var offsets = at.resolve("in" + p + ".offsets");
                var chars = at.resolve("in" + p + ".chars");
                try (var o = new BufferedOutputStream(Files.newOutputStream(offsets), 1 << 20); var c = new BufferedOutputStream(Files.newOutputStream(chars), 1 << 20)) {
                    int total = 0;
                    putInt(o, 0);
                    for (int i = 0; i < rows; i++) {
                        var b = (byte[]) column.value().apply(i);
                        if (b != null) {
                            c.write(b);
                            total += b.length;
                        }
                        putInt(o, total);
                    }
                    c.write(0); // never an empty buffer
                }
                return "input STRING " + offsets + " " + chars + " " + validity;
            }
            var data = at.resolve("in" + p + ".bin");
            try (var d = new BufferedOutputStream(Files.newOutputStream(data), 1 << 20)) {
                var one = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < rows; i++) {
                    var v = column.value().apply(i);
                    one.clear();
                    switch (v) {
                        case null -> one.put(new byte[width(column.type())]);
                        case Integer n -> one.putInt(n);
                        case Long n -> one.putLong(n);
                        case Short n -> one.putShort(n);
                        case Byte n -> one.put(n);
                        case Character ch -> one.putChar(ch);
                        case Boolean b -> one.put((byte) (b ? 1 : 0));
                        case Float f -> one.putFloat(f);
                        case Double x -> one.putDouble(x);
                        default -> throw new IllegalArgumentException(v.toString());
                    }
                    d.write(one.array(), 0, one.position());
                }
            }
            return "input " + TYPES.get(column.type()) + " " + data + " " + validity;
        }

        private static int width(Class<?> type) {
            return switch (TYPES.get(type)) {
                case "INT64", "FLOAT64" -> 8;
                case "INT32", "FLOAT32" -> 4;
                case "INT16", "UINT16" -> 2;
                default -> 1;
            };
        }

        private static void putInt(OutputStream out, int v) throws IOException {
            out.write(v);
            out.write(v >>> 8);
            out.write(v >>> 16);
            out.write(v >>> 24);
        }

        /** The library path the harness runs with: libcudf's, librmm's and CUDF_LD_LIBRARY_PATH, then the inherited one. */
        private static String libraryPath() {
            var env = System.getenv();
            var cudf = Path.of(env.get("CUDF_HOME"));
            var parts = new ArrayList<String>();
            parts.add(cudf.resolve("lib64").toString());
            parts.add(Path.of(env.getOrDefault("RMM_HOME", cudf.resolveSibling("librmm").toString())).resolve("lib64").toString());
            if (env.get("CUDF_LD_LIBRARY_PATH") != null) {
                parts.add(env.get("CUDF_LD_LIBRARY_PATH"));
            }
            if (env.get("LD_LIBRARY_PATH") != null) {
                parts.add(env.get("LD_LIBRARY_PATH"));
            }
            return String.join(":", parts);
        }
    }
}
