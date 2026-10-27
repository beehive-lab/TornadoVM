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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.analysis.KernelFeatures;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * TC's compilations on disk, so that a later run skips TC for a kernel it compiled before (spike).
 *
 * <p>An entry is found by a key over everything the compilation's result depends on that is known
 * before compiling: the TC build (a hash of the TC jars), the method, the target, the options that
 * change code, the argument facts, and the {@code tornado.tc.*} / {@code tornado.enable.*} system
 * properties. It is used only if everything the compilation read through its bytecode source
 * still reads the same: the class files (SHA-256), static final values, enum constants and device
 * kernels, recorded while compiling ({@link Recording}). A changed kernel, callee or constant
 * therefore misses, and the kernel is compiled again.</p>
 *
 * <p>{@code -Dtornado.tc.cache=false} turns it off; {@code -Dtornado.tc.cache.dir} moves it
 * (default {@code $TORNADOVM_HOME/var/tc-cache}). Kernels that launch kernels (their source names
 * the task) and compilations that dump or report are not cached.</p>
 */
final class TcDiskCache {

    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("tornado.tc.cache", "true"));

    private static final String VERSION = "1";

    private static volatile Path directory = Path.of(System.getProperty("tornado.tc.cache.dir", defaultDirectory()));

    /** Hash of the TC jars, or empty when TC does not run from jars (then nothing is cached). */
    private static volatile Optional<String> fingerprint;

    private TcDiskCache() {
    }

    /** Tests: a cache in {@code dir} for a TC build named {@code build} (the classes are not in jars there). */
    static void useForTests(Path dir, String build) {
        directory = dir;
        fingerprint = Optional.of(build);
    }

    private static String defaultDirectory() {
        String home = System.getenv("TORNADOVM_HOME");
        return home != null ? home + "/var/tc-cache" : System.getProperty("user.home") + "/.cache/tornadovm/tc-cache";
    }

    // ---------------------------------------------------------------- key

    /** The entry's name for this compilation, or empty when the cache cannot be used. */
    static Optional<String> key(String owner, String method, String descriptor, TargetCaps target, TcOptions options, List<ValueSpecialisation.Fact> facts) {
        if (!ENABLED || !options.dump().isEmpty() || options.htmlReport()) {
            return Optional.empty();
        }
        Optional<String> build = fingerprint();
        if (build.isEmpty()) {
            return Optional.empty();
        }
        var text = new StringBuilder();
        text.append(VERSION).append('\n').append(build.get()).append('\n');
        text.append(owner).append('.').append(method).append(descriptor).append('\n');
        text.append(canonical(target)).append('\n');
        text.append("math=").append(options.mathOptimizations()).append(",fma=").append(options.fuseMultiplyAdd()).append('\n');
        text.append(canonical(facts)).append('\n');
        new TreeMap<>(System.getProperties()).forEach((k, v) -> {
            String name = k.toString();
            if (name.startsWith("tornado.tc.") || name.startsWith("tornado.enable.")) {
                text.append(name).append('=').append(v).append('\n');
            }
        });
        return Optional.of(sha256(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The entry's name for an analysis of a method that the runtime asks for every task it builds
     * ({@code kind}: "access", how it reads and writes each parameter; "loop-index", whether it writes
     * its parallel loop index); empty when the cache cannot be used.
     */
    static Optional<String> analysisKey(String kind, String owner, String method, String descriptor) {
        if (!ENABLED) {
            return Optional.empty();
        }
        Optional<String> build = fingerprint();
        if (build.isEmpty()) {
            return Optional.empty();
        }
        var text = new StringBuilder();
        text.append(VERSION).append('\n').append(build.get()).append('\n').append(kind).append('\n');
        text.append(owner).append('.').append(method).append(descriptor).append('\n');
        new TreeMap<>(System.getProperties()).forEach((k, v) -> {
            String name = k.toString();
            if (name.startsWith("tornado.tc.") || name.startsWith("tornado.enable.")) {
                text.append(name).append('=').append(v).append('\n');
            }
        });
        return Optional.of(sha256(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** The analysis result stored under {@code key}, if what the analysis read is unchanged. */
    static Optional<int[]> loadAnalysis(String key, Frontend.BytecodeSource live) {
        Path file = directory.resolve(key + ".analysis");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            var p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            var reads = new TreeMap<String, String>();
            p.stringPropertyNames().stream().filter(n -> n.startsWith("read.")).forEach(n -> reads.put(n.substring(5), p.getProperty(n)));
            if (!unchanged(reads, live)) {
                return Optional.empty();
            }
            return Optional.of(split(p.getProperty("bits"), ',').stream().mapToInt(Integer::parseInt).toArray());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static void storeAnalysis(String key, Map<String, String> reads, int[] bits) {
        try {
            Files.createDirectories(directory);
            var p = new Properties();
            p.setProperty("bits", String.join(",", java.util.Arrays.stream(bits).mapToObj(String::valueOf).toList()));
            reads.forEach((k, v) -> p.setProperty("read." + k, v));
            Path temporary = Files.createTempFile(directory, key, ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                p.store(out, null);
            }
            Files.move(temporary, directory.resolve(key + ".analysis"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // A cache that cannot be written only costs the next run an analysis.
        }
    }

    /** A rendering that does not depend on map iteration order (Map.of differs between runs). */
    private static String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, String>();
            map.forEach((k, v) -> sorted.put(canonical(k), canonical(v)));
            return sorted.toString();
        }
        if (value instanceof List<?> list) {
            var items = new ArrayList<String>();
            list.forEach(item -> items.add(canonical(item)));
            return items.toString();
        }
        if (value instanceof Record record) {
            var sb = new StringBuilder(record.getClass().getName()).append('(');
            for (var component : record.getClass().getRecordComponents()) {
                try {
                    sb.append(component.getName()).append('=').append(canonical(component.getAccessor().invoke(record))).append(';');
                } catch (ReflectiveOperationException e) {
                    return value.toString();
                }
            }
            return sb.append(')').toString();
        }
        if (value instanceof Number number) {
            return number.getClass().getSimpleName() + ":" + number;
        }
        return value.toString();
    }

    private static Optional<String> fingerprint() {
        Optional<String> f = fingerprint;
        if (f == null) {
            synchronized (TcDiskCache.class) {
                f = fingerprint;
                if (f == null) {
                    f = computeFingerprint();
                    fingerprint = f;
                }
            }
        }
        return f;
    }

    /** SHA-256 over the jars of TC's modules (and of this one): a different TC build never reuses an entry. */
    private static Optional<String> computeFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Class<?>[] modules = { TcCompiler.class, Frontend.class, KernelFeatures.class, ParallelLoopLowering.class, uk.ac.manchester.tornado.tc.codegen.CEmitter.class,
                    uk.ac.manchester.tornado.tc.lower.Structurizer.class, uk.ac.manchester.tornado.tc.opt.PassManager.class, TcDiskCache.class };
            for (Class<?> c : modules) {
                var source = c.getProtectionDomain().getCodeSource();
                if (source == null) {
                    return Optional.empty();
                }
                Path jar = Path.of(source.getLocation().toURI());
                if (!Files.isRegularFile(jar)) {
                    return Optional.empty(); // classes directories: a development build, not cached
                }
                try (InputStream in = Files.newInputStream(jar)) {
                    digest.update(in.readAllBytes());
                }
            }
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- dependencies

    /** A bytecode source that records what the compilation reads, to validate an entry later. */
    static final class Recording implements Frontend.BytecodeSource {
        private final Frontend.BytecodeSource source;
        private final Map<String, String> reads = new ConcurrentHashMap<>();

        Recording(Frontend.BytecodeSource source) {
            this.source = source;
        }

        Map<String, String> reads() {
            return new TreeMap<>(reads);
        }

        @Override
        public Optional<byte[]> classBytes(String internalName) {
            var bytes = source.classBytes(internalName);
            reads.put("class:" + internalName, bytes.map(TcDiskCache::sha256).orElse("-"));
            return bytes;
        }

        @Override
        public Optional<Frontend.MethodRef> deviceKernel(String ownerInternalName, String field) {
            var value = source.deviceKernel(ownerInternalName, field);
            reads.put("kernel:" + ownerInternalName + "." + field, value.map(TcDiskCache::canonical).orElse("-"));
            return value;
        }

        @Override
        public Optional<ClassLoader> loader() {
            return source.loader();
        }

        @Override
        public Optional<Frontend.EnumInfo> enumInfo(String internalName) {
            var value = source.enumInfo(internalName);
            reads.put("enum:" + internalName, value.map(TcDiskCache::canonical).orElse("-"));
            return value;
        }

        @Override
        public Optional<Number> staticFinal(String ownerInternalName, String field) {
            var value = source.staticFinal(ownerInternalName, field);
            reads.put("static:" + ownerInternalName + "." + field, value.map(TcDiskCache::canonical).orElse("-"));
            return value;
        }
    }

    /** Whether everything the entry's compilation read still reads the same through {@code live}. */
    private static boolean unchanged(Map<String, String> reads, Frontend.BytecodeSource live) {
        for (var read : reads.entrySet()) {
            String what = read.getKey();
            int colon = what.indexOf(':');
            String kind = what.substring(0, colon);
            String name = what.substring(colon + 1);
            int dot = name.lastIndexOf('.');
            String now = switch (kind) {
                case "class" -> live.classBytes(name).map(TcDiskCache::sha256).orElse("-");
                case "kernel" -> live.deviceKernel(name.substring(0, dot), name.substring(dot + 1)).map(TcDiskCache::canonical).orElse("-");
                case "enum" -> live.enumInfo(name).map(TcDiskCache::canonical).orElse("-");
                case "static" -> live.staticFinal(name.substring(0, dot), name.substring(dot + 1)).map(TcDiskCache::canonical).orElse("-");
                default -> null;
            };
            if (!read.getValue().equals(now)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- entries

    static Optional<TcHook.Compiled> load(String key, Frontend.BytecodeSource live) {
        Path file = directory.resolve(key + ".tc");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            var p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            var reads = new TreeMap<String, String>();
            p.stringPropertyNames().stream().filter(n -> n.startsWith("read.")).forEach(n -> reads.put(n.substring(5), p.getProperty(n)));
            if (!unchanged(reads, live)) {
                return Optional.empty();
            }
            String source = new String(Base64.getDecoder().decode(p.getProperty("source")), StandardCharsets.UTF_8);
            var domain = new ArrayList<ParallelLoopLowering.Domain>();
            for (String d : split(p.getProperty("domain"), ';')) {
                String[] f = d.split(",");
                domain.add(new ParallelLoopLowering.Domain(Integer.parseInt(f[0]), Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3])));
            }
            var initial = new ArrayList<Integer>();
            split(p.getProperty("atomics.initial"), ',').forEach(v -> initial.add(Integer.parseInt(v)));
            var params = new LinkedHashMap<Integer, Integer>();
            split(p.getProperty("atomics.params"), ',').forEach(e -> params.put(Integer.parseInt(e.split("=")[0]), Integer.parseInt(e.split("=")[1])));
            var counts = new EnumMap<KernelFeatures.Feature, Integer>(KernelFeatures.Feature.class);
            split(p.getProperty("features"), ',').forEach(e -> counts.put(KernelFeatures.Feature.valueOf(e.split("=")[0]), Integer.parseInt(e.split("=")[1])));
            return Optional.of(new TcHook.Compiled(source, p.getProperty("entry"), 0, domain, new AtomicSlots.Info(initial, params), new KernelFeatures(counts), "?", false));
        } catch (Exception e) {
            return Optional.empty(); // unreadable or from another format: compiled again, and rewritten
        }
    }

    static void store(String key, Map<String, String> reads, TcHook.Compiled c) {
        if (c.taskInSource()) {
            return;
        }
        try {
            Files.createDirectories(directory);
            var p = new Properties();
            p.setProperty("entry", c.entryName());
            p.setProperty("source", Base64.getEncoder().encodeToString(c.source().getBytes(StandardCharsets.UTF_8)));
            p.setProperty("domain", String.join(";", c.domain().stream().map(d -> d.dim() + "," + d.offset() + "," + d.step() + "," + d.upper()).toList()));
            p.setProperty("atomics.initial", String.join(",", c.atomics().initial().stream().map(String::valueOf).toList()));
            p.setProperty("atomics.params", String.join(",", c.atomics().params().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
            p.setProperty("features", String.join(",", c.features().counts().entrySet().stream().map(e -> e.getKey().name() + "=" + e.getValue()).toList()));
            reads.forEach((k, v) -> p.setProperty("read." + k, v));
            Path temporary = Files.createTempFile(directory, key, ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                p.store(out, null);
            }
            Files.move(temporary, directory.resolve(key + ".tc"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // A cache that cannot be written only costs the next run a compilation.
        }
    }

    private static List<String> split(String s, char separator) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        return List.of(s.split(Pattern.quote(String.valueOf(separator))));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
