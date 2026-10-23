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
package uk.ac.manchester.tornado.fuzz.gen;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.RandomGen;

/**
 * Phase 3 driver: generates a typed program ({@link ProgramGen}), compiles it twice into one class
 * (a {@code KernelContext} kernel and an {@code @Parallel} kernel) plus a JVM reference, runs both
 * kernels on the device and compares every output with the reference.
 *
 * <p>Outcomes:</p>
 * <ul>
 *   <li>{@code pass};</li>
 *   <li>{@code miscompile} — an output differs from the JVM (ints and longs exactly, floats within
 *       1e-3 relative or 1e-3 absolute, NaN equal to NaN). The program is shrunk by dropping statements;</li>
 *   <li>{@code internal} — TC's internal error {@code TC-E9xxx}, a compiler bug;</li>
 *   <li>{@code crash} — any other exception on the device path;</li>
 *   <li>{@code refused:TC-Exxxx} — TC declined the kernel with a diagnostic: not a bug, but a
 *       shortcoming worth knowing, counted per code;</li>
 *   <li>{@code harness} — the program did not compile with javac, or the reference threw.</li>
 * </ul>
 */
public final class Phase3Fuzzer {

    private static final int[] SIZES = { 256, 1024, 4096, 1000 };
    private static final Pattern TC_CODE = Pattern.compile("TC-E\\d{4}");

    private final InProcessCompiler compiler;
    private final TornadoDevice device;
    private final boolean fp64;
    private final Path outDir;
    private final AtomicLong classes = new AtomicLong();
    private final Map<String, Integer> outcomes = new TreeMap<>();
    private final Map<String, Long> firstSeed = new TreeMap<>();

    public Phase3Fuzzer(InProcessCompiler compiler, TornadoDevice device, boolean fp64, Path outDir) {
        this.compiler = compiler;
        this.device = device;
        this.fp64 = fp64;
        this.outDir = outDir.resolve("phase3");
    }

    public Map<String, Integer> outcomes() {
        return outcomes;
    }

    /** Runs one seed; returns its outcome key. */
    public String run(long seed) throws Exception {
        var rng = new RandomGen(seed);
        var program = ProgramGen.generate(rng, fp64);
        int size = SIZES[rng.nextInt(SIZES.length)];
        var profile = DataGen.Profile.values()[rng.nextInt(DataGen.Profile.values().length)];
        var in = Inputs.make(rng, size, profile, fp64);

        var outcome = evaluate(program, in);
        if (outcome.key.equals("miscompile") || outcome.key.equals("internal") || outcome.key.equals("crash")) {
            program = shrink(program, in, outcome.key);
            outcome = evaluate(program, in);
        }
        String key = outcome.key.equals("refused") ? "refused:" + outcome.detailCode() : outcome.key;
        if (outcome.key.equals("miscompile")) {
            key = "miscompile:" + outcome.which + (program.transcendental() && outcome.onlyFloats ? " (float, transcendental)" : "");
        }
        if (outcome.key.equals("internal") || outcome.key.equals("crash")) {
            key = outcome.key + ":" + outcome.detailCode();
        }
        int seen = outcomes.merge(key, 1, Integer::sum);
        if (seen == 1 && !key.equals("pass")) {
            firstSeed.put(key, seed);
            writeBundle(key, seed, program, size, outcome, in);
        }
        return key;
    }

    private record Inputs(IntArray ia, IntArray ib, LongArray la, FloatArray fa, DoubleArray da, int size) {
        static Inputs make(RandomGen rng, int size, DataGen.Profile profile, boolean fp64) {
            var ia = new IntArray(size);
            var ib = new IntArray(size);
            var la = new LongArray(size);
            var fa = new FloatArray(size);
            var da = new DoubleArray(fp64 ? size : 1);
            DataGen.fill(ia, rng, profile);
            DataGen.fill(ib, rng, profile);
            DataGen.fill(la, rng, profile);
            DataGen.fill(fa, rng, profile);
            if (fp64) {
                DataGen.fill(da, rng, profile);
            }
            return new Inputs(ia, ib, la, fa, da, size);
        }
    }

    private static final class Outcome {
        String key = "pass";
        String which = "";
        boolean onlyFloats;
        String detail = "";
        String source = "";
        String kernelStyle = "";

        String detailCode() {
            var m = TC_CODE.matcher(detail);
            if (m.find()) {
                return m.group();
            }
            int nl = detail.indexOf('\n');
            String first = nl > 0 ? detail.substring(0, nl) : detail;
            return first.length() > 80 ? first.substring(0, 80) : first;
        }
    }

    private Outcome evaluate(ProgramGen.Program program, Inputs in) {
        String className = "P" + classes.incrementAndGet();
        return evaluateSource(className, source(className, program), in);
    }

    private Outcome evaluateSource(String className, String source, Inputs in) {
        var o = new Outcome();
        String fqcn = KernelSourceEmitter.PACKAGE + "." + className;
        o.source = source;
        Class<?> cls;
        try {
            cls = compiler.compileAndLoad(fqcn, o.source);
        } catch (Throwable e) {
            o.key = "harness";
            o.detail = String.valueOf(e.getMessage());
            return o;
        }
        int n = in.size;
        var ri = new IntArray(n);
        var rl = new LongArray(n);
        var rf = new FloatArray(n);
        var rd = new DoubleArray(fp64 ? n : 1);
        try {
            Method ref = cls.getMethod("reference", IntArray.class, IntArray.class, LongArray.class, FloatArray.class, DoubleArray.class, IntArray.class, LongArray.class, FloatArray.class,
                    DoubleArray.class);
            ref.invoke(null, in.ia, in.ib, in.la, in.fa, in.da, ri, rl, rf, rd);
        } catch (Throwable e) {
            o.key = "harness";
            o.detail = "reference threw: " + e.getCause();
            return o;
        }
        for (var style : new String[] { "kernelContext", "parallel" }) {
            var oi = new IntArray(n);
            var ol = new LongArray(n);
            var of = new FloatArray(n);
            var od = new DoubleArray(fp64 ? n : 1);
            try {
                TaskGraph tg = new TaskGraph("f" + className).transferToDevice(DataTransferMode.EVERY_EXECUTION, in.ia, in.ib, in.la, in.fa, in.da);
                GridScheduler grid = null;
                if (style.equals("kernelContext")) {
                    @SuppressWarnings("unchecked")
                    var task = (Task10<KernelContext, IntArray, IntArray, LongArray, FloatArray, DoubleArray, IntArray, LongArray, FloatArray, DoubleArray>) cls.getMethod("contextTask").invoke(null);
                    tg.task("t", task, new KernelContext(), in.ia, in.ib, in.la, in.fa, in.da, oi, ol, of, od);
                    var worker = new WorkerGrid1D(n);
                    worker.setLocalWork(n % 256 == 0 ? 256 : 8, 1, 1);
                    grid = new GridScheduler("f" + className + ".t", worker);
                } else {
                    @SuppressWarnings("unchecked")
                    var task = (Task9<IntArray, IntArray, LongArray, FloatArray, DoubleArray, IntArray, LongArray, FloatArray, DoubleArray>) cls.getMethod("parallelTask").invoke(null);
                    tg.task("t", task, in.ia, in.ib, in.la, in.fa, in.da, oi, ol, of, od);
                }
                tg.transferToHost(DataTransferMode.EVERY_EXECUTION, oi, ol, of, od);
                try (var plan = new TornadoExecutionPlan(tg.snapshot())) {
                    plan.withDevice(device).withStrictFloatingPoint();
                    if (grid != null) {
                        plan.withGridScheduler(grid);
                    }
                    plan.execute();
                }
            } catch (Throwable e) {
                var text = describe(e);
                o.kernelStyle = style;
                o.detail = text;
                var m = TC_CODE.matcher(text);
                if (m.find()) {
                    o.key = m.group().startsWith("TC-E9") ? "internal" : "refused";
                } else {
                    o.key = "crash";
                }
                return o;
            }
            var which = new ArrayList<String>();
            var detail = new StringBuilder();
            compare("int", ri, oi, which, detail);
            compare("long", rl, ol, which, detail);
            compare("float", rf, of, which, detail);
            if (fp64) {
                compare("double", rd, od, which, detail);
            }
            if (!which.isEmpty()) {
                o.key = "miscompile";
                o.kernelStyle = style;
                o.which = String.join(",", which);
                o.onlyFloats = which.stream().allMatch(w -> w.equals("float") || w.equals("double"));
                o.detail = style + ":\n" + detail;
                return o;
            }
        }
        return o;
    }

    private static String describe(Throwable e) {
        var sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
            if (t.getCause() == t) {
                break;
            }
        }
        return sb.toString();
    }

    private static void compare(String name, IntArray e, IntArray a, List<String> which, StringBuilder d) {
        for (int i = 0; i < e.getSize(); i++) {
            if (e.get(i) != a.get(i)) {
                which.add(name);
                d.append(name).append("[").append(i).append("] expected ").append(e.get(i)).append(" got ").append(a.get(i)).append('\n');
                return;
            }
        }
    }

    private static void compare(String name, LongArray e, LongArray a, List<String> which, StringBuilder d) {
        for (int i = 0; i < e.getSize(); i++) {
            if (e.get(i) != a.get(i)) {
                which.add(name);
                d.append(name).append("[").append(i).append("] expected ").append(e.get(i)).append(" got ").append(a.get(i)).append('\n');
                return;
            }
        }
    }

    private static boolean close(double e, double a) {
        if (Double.isNaN(e) || Double.isNaN(a)) {
            return Double.isNaN(e) && Double.isNaN(a);
        }
        if (e == a) {
            return true;
        }
        double diff = Math.abs(e - a);
        return diff <= 1e-3 || diff <= 1e-3 * Math.max(Math.abs(e), Math.abs(a));
    }

    private static void compare(String name, FloatArray e, FloatArray a, List<String> which, StringBuilder d) {
        for (int i = 0; i < e.getSize(); i++) {
            if (!close(e.get(i), a.get(i))) {
                which.add(name);
                d.append(name).append("[").append(i).append("] expected ").append(e.get(i)).append(" got ").append(a.get(i)).append('\n');
                return;
            }
        }
    }

    private static void compare(String name, DoubleArray e, DoubleArray a, List<String> which, StringBuilder d) {
        for (int i = 0; i < e.getSize(); i++) {
            if (!close(e.get(i), a.get(i))) {
                which.add(name);
                d.append(name).append("[").append(i).append("] expected ").append(e.get(i)).append(" got ").append(a.get(i)).append('\n');
                return;
            }
        }
    }

    /** Drops statements one at a time while the program still fails the same way. */
    private ProgramGen.Program shrink(ProgramGen.Program program, Inputs in, String kind) {
        var current = program;
        boolean progress = true;
        int budget = 60;
        while (progress && budget > 0) {
            progress = false;
            for (int s = current.statements().size() - 1; s >= 0 && budget > 0; s--) {
                var kept = new ArrayList<>(current.statements());
                kept.remove(s);
                var candidate = current.withStatements(kept);
                budget--;
                if (evaluate(candidate, in).key.equals(kind)) {
                    current = candidate;
                    progress = true;
                }
            }
        }
        return current;
    }

    /** The generated class: computed static finals, both kernels, the reference and task factories. */
    String source(String className, ProgramGen.Program p) {
        String params = "IntArray ia, IntArray ib, LongArray la, FloatArray fa, DoubleArray da, IntArray oi, LongArray ol, FloatArray of, DoubleArray od";
        return "package " + KernelSourceEmitter.PACKAGE + ";\n\n" //
                + "import uk.ac.manchester.tornado.api.KernelContext;\n" //
                + "import uk.ac.manchester.tornado.api.annotations.Parallel;\n" //
                + "import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;\n" //
                + "import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;\n" //
                + "import uk.ac.manchester.tornado.api.types.arrays.*;\n\n" //
                + "public final class " + className + " {\n" //
                + "    static final class K {\n" //
                + "        static final int SCALE = Integer.parseInt(\"3\");\n" //
                + "        static final long BIG = Long.parseLong(\"10000000000\");\n" //
                + "        static final float HALF = (float) Math.sqrt(0.25);\n" //
                + "        static final double TO_RADIANS = Math.PI / 180;\n" //
                + "    }\n\n" //
                + "    public static void contextKernel(KernelContext c, " + params + ") {\n" //
                + "        int i = c.globalIdx;\n" //
                + "        if (i < oi.getSize()) {\n" //
                + p.body("            ", fp64) //
                + "        }\n" //
                + "    }\n\n" //
                + "    public static void parallelKernel(" + params + ") {\n" //
                + "        for (@Parallel int i = 0; i < oi.getSize(); i++) {\n" //
                + p.body("            ", fp64) //
                + "        }\n" //
                + "    }\n\n" //
                + "    public static void reference(" + params + ") {\n" //
                + "        for (int i = 0; i < oi.getSize(); i++) {\n" //
                + p.body("            ", fp64) //
                + "        }\n" //
                + "    }\n\n" //
                + "    public static Task10<KernelContext, IntArray, IntArray, LongArray, FloatArray, DoubleArray, IntArray, LongArray, FloatArray, DoubleArray> contextTask() {\n" //
                + "        return " + className + "::contextKernel;\n" //
                + "    }\n\n" //
                + "    public static Task9<IntArray, IntArray, LongArray, FloatArray, DoubleArray, IntArray, LongArray, FloatArray, DoubleArray> parallelTask() {\n" //
                + "        return " + className + "::parallelKernel;\n" //
                + "    }\n" //
                + "}\n";
    }

    /**
     * Replays a finding: compiles the bundle's Kernel.java (edit it to narrow a bug down) under a
     * fresh class name and runs it on the bundle's saved inputs. Returns the outcome and its details.
     */
    public String replay(Path bundle) throws Exception {
        var source = Files.readString(bundle.resolve("Kernel.java"));
        var matcher = Pattern.compile("public final class (P\\d+)").matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("no generated class in " + bundle.resolve("Kernel.java"));
        }
        String old = matcher.group(1);
        String fresh = "R" + classes.incrementAndGet();
        source = source.replace("class " + old + " ", "class " + fresh + " ").replace(old + "::", fresh + "::");
        var o = evaluateSource(fresh, source, loadInputs(bundle.resolve("inputs.bin")));
        return o.key + (o.kernelStyle.isEmpty() ? "" : " (" + o.kernelStyle + ")") + "\n" + o.detail;
    }

    private static void saveInputs(Path file, Inputs in) throws Exception {
        try (var out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(in.size);
            out.writeInt(in.da.getSize());
            for (int i = 0; i < in.size; i++) {
                out.writeInt(in.ia.get(i));
                out.writeInt(in.ib.get(i));
                out.writeLong(in.la.get(i));
                out.writeFloat(in.fa.get(i));
            }
            for (int i = 0; i < in.da.getSize(); i++) {
                out.writeDouble(in.da.get(i));
            }
        }
    }

    private static Inputs loadInputs(Path file) throws Exception {
        try (var in = new java.io.DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int n = in.readInt();
            int nd = in.readInt();
            var ia = new IntArray(n);
            var ib = new IntArray(n);
            var la = new LongArray(n);
            var fa = new FloatArray(n);
            var da = new DoubleArray(nd);
            for (int i = 0; i < n; i++) {
                ia.set(i, in.readInt());
                ib.set(i, in.readInt());
                la.set(i, in.readLong());
                fa.set(i, in.readFloat());
            }
            for (int i = 0; i < nd; i++) {
                da.set(i, in.readDouble());
            }
            return new Inputs(ia, ib, la, fa, da, n);
        }
    }

    private void writeBundle(String key, long seed, ProgramGen.Program program, int size, Outcome o, Inputs in) throws Exception {
        String dirName = key.replaceAll("[^A-Za-z0-9._-]+", "_") + "-" + seed;
        Path dir = Files.createDirectories(outDir.resolve(dirName));
        Files.writeString(dir.resolve("Kernel.java"), o.source, StandardCharsets.UTF_8);
        saveInputs(dir.resolve("inputs.bin"), in);
        Files.writeString(dir.resolve("finding.txt"), "outcome: " + key + "\nseed: " + seed + "\nsize: " + size + "\nkernel style: " + o.kernelStyle + "\ndevice: " + device.getDeviceName()
                + "\nfp64: " + fp64 + "\n\n" + o.detail, StandardCharsets.UTF_8);
    }

    /** Writes summary.json: outcome counts and the first seed of each. */
    public void writeSummary() throws Exception {
        Files.createDirectories(outDir);
        var sb = new StringBuilder("{\n");
        int k = 0;
        for (var e : outcomes.entrySet()) {
            sb.append(k++ == 0 ? "" : ",\n").append("  \"").append(e.getKey().replace("\"", "'")).append("\": { \"count\": ").append(e.getValue());
            if (firstSeed.containsKey(e.getKey())) {
                sb.append(", \"firstSeed\": ").append(firstSeed.get(e.getKey()));
            }
            sb.append(" }");
        }
        sb.append("\n}\n");
        Files.writeString(outDir.resolve("summary.json"), sb.toString(), StandardCharsets.UTF_8);
    }
}
