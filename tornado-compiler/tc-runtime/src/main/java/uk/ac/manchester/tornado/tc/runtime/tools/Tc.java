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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * {@code tc}, the headless compiler: compiles kernel methods from class files to CUDA C++, OpenCL C
 * or MSL and prints them. No GPU and no TornadoVM runtime are needed.
 *
 * <pre>
 *   tc emit [--target cuda|opencl|metal|hip] [--cc N] [--fp16 on|off] [--fp64 on|off] [--cp PATH] [--stats] Class method
 *   tc emit [same options] --all
 *   tc --version
 *   ... [--arg name=value]...
 * </pre>
 *
 * {@code --all} compiles every kernel of every class in the directories on the class path (the
 * TornadoVM API's own classes excepted): each
 * static {@code void} method whose parameters are primitives, primitive arrays or TornadoVM API types,
 * except {@code main}, synthetic methods and the children of {@code DeviceKernel.of} (compiled with
 * the kernel that launches them). Each kernel's code follows a {@code // ===== Class.method =====}
 * header, in source order; a refused kernel prints its diagnostics and the others still compile.
 *
 * <p>
 * {@code --cc} is the CUDA compute capability (default 89: an RTX 4090), as {@code major * 10 + minor}.
 * {@code --arg} gives a parameter's value as the runtime would see it: a scalar, an array length, or the
 * size an object is constructed with; with {@code --all}, every kernel with a parameter of that name
 * gets it. Names come from the class files' MethodParameters ({@code javac -parameters}), else
 * {@code arg0}, {@code arg1}, ... A kernel whose {@code @Parallel} bounds need values not given
 * (TC-E3103) is compiled again with integers and object sizes taken as 256, and a note says so.
 * Diagnostics are printed as {@code File.java:17: error: TC-E4011 message}, the form editors and
 * Compiler Explorer recognise.
 *
 * <p>
 * On a JVM the classes are loaded (never run, except their static initialisers), so that
 * {@code static final DeviceKernel} fields, enum constants such as {@code DType.FP8_E4M3} and computed
 * constants resolve as when TornadoVM compiles a task. As a GraalVM native image, class bytes are
 * only read, and kernels that need those are refused.
 *
 * <p>
 * The default class path is {@code .}; {@code $TORNADOVM_HOME/share/java/tornado/tornado-api*.jar}
 * is always added when set. Exit status: 0 on success, 1 when TC refuses a kernel, 2 on a usage error.
 */
public final class Tc {

    private static final String API = "uk/ac/manchester/tornado/api/";
    private static final String DEVICE_KERNEL = API + "DeviceKernel";
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_BRIDGE = 0x0040;
    private static final int ACC_SYNTHETIC = 0x1000;

    private Tc() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** One kernel to compile: its class (internal name), method name and descriptor, first source line and file. */
    record Kernel(String owner, String name, String descriptor, int line, String file) {
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("tc " + version());
            return 0;
        }
        if (args.length < 1 || !args[0].equals("emit")) {
            return usage(err);
        }
        var dialect = TargetCaps.Dialect.CUDA;
        int cc = 89;
        Boolean fp16 = null;
        Boolean fp64 = null;
        String classPath = ".";
        boolean stats = false;
        boolean all = false;
        var given = new LinkedHashMap<String, String>();
        var positional = new ArrayList<String>();
        try {
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--target" -> {
                        switch (value(args, ++i)) {
                            case "cuda" -> dialect = TargetCaps.Dialect.CUDA;
                            case "opencl" -> dialect = TargetCaps.Dialect.OPENCL;
                            case "metal" -> dialect = TargetCaps.Dialect.METAL;
                            case "hip" -> dialect = TargetCaps.Dialect.HIP;
                            default -> throw new IllegalArgumentException("--target takes cuda, opencl, metal or hip");
                        }
                    }
                    case "--cc" -> cc = Integer.parseInt(value(args, ++i));
                    case "--fp16" -> fp16 = onOff(value(args, ++i));
                    case "--fp64" -> fp64 = onOff(value(args, ++i));
                    case "--cp", "-cp", "--classpath" -> classPath = value(args, ++i);
                    case "--stats" -> stats = true;
                    case "--all" -> all = true;
                    case "--arg" -> {
                        var nameValue = value(args, ++i).split("=", 2);
                        if (nameValue.length != 2) {
                            throw new IllegalArgumentException("--arg takes name=value, as in --arg n=1024");
                        }
                        given.put(nameValue[0], nameValue[1]);
                    }
                    default -> positional.add(args[i]);
                }
            }
        } catch (IllegalArgumentException e) {
            err.println("tc: " + e.getMessage());
            return usage(err);
        }
        if (all ? !positional.isEmpty() : positional.size() != 2) {
            return usage(err);
        }
        var target = target(dialect, cc, fp16, fp64);
        var path = withApi(classPath);
        var source = source(path);
        List<Kernel> kernels = all ? kernels(classPath) : List.of(named(source, positional.get(0).replace('.', '/'), positional.get(1)));
        boolean refused = false;
        for (var kernel : kernels) {
            if (all) {
                out.println("// ===== " + kernel.owner().replace('/', '.') + "." + kernel.name() + " =====");
            }
            var method = method(source, kernel);
            var assumed = new ArrayList<String>();
            TcCompiler.Compilation c;
            try {
                c = compile(source, kernel, target, method, given, false, assumed);
            } catch (TypeLayouts.BadArgument e) {
                err.println("tc: " + e.getMessage());
                return usage(err);
            }
            if (!c.succeeded() && method != null && c.diagnostics().stream().anyMatch(d -> NEEDS_VALUES.contains(d.code()))) {
                // The runtime knows every argument when it compiles a task; here, assume what the domain needs.
                var retry = compile(source, kernel, target, method, given, true, assumed);
                if (retry.succeeded()) {
                    c = retry;
                    var note = location(kernel) + "note: compiled for " + String.join(", ", assumed) + ": these values must be known when the kernel is compiled,"
                            + " as they are when TornadoVM runs it (set them with --arg name=value)";
                    err.println(note);
                    if (all) {
                        out.println("// " + note);
                    }
                }
            }
            if (!c.succeeded()) {
                refused = true;
                c.diagnostics().forEach(d -> err.println(render(d, kernel)));
                if (all) {
                    c.diagnostics().forEach(d -> out.println("// " + render(d, kernel)));
                    out.println();
                }
                continue;
            }
            out.print(c.source());
            if (all) {
                out.println();
            }
            if (stats) {
                err.printf("[TC] %s: %.2f ms%n", c.entryName(), c.nanos() / 1e6);
            }
        }
        if (all && kernels.isEmpty()) {
            err.println("tc: no kernel methods in the classes on " + classPath);
            return 1;
        }
        return refused ? 1 : 0;
    }

    /** The target as the backend's device queries would describe it, for a device with no other limits. */
    static TargetCaps target(TargetCaps.Dialect dialect, int cc, Boolean fp16, Boolean fp64) {
        var base = switch (dialect) {
            // Native fp16 arithmetic from sm_53, device-side launches from sm_35 (as CUDATornadoDevice).
            case CUDA -> new TargetCaps(TargetCaps.Dialect.CUDA, cc, true, cc >= 53, 16, 1024, 48 * 1024, 32, cc >= 35, 0);
            case OPENCL -> TargetCaps.OPENCL_GENERIC;
            case METAL -> TargetCaps.METAL_APPLE;
            case HIP -> TargetCaps.HIP_GFX942;
        };
        return new TargetCaps(base.dialect(), base.computeCapability(), fp64 == null ? base.fp64() : fp64, fp16 == null ? base.fp16() : fp16, base.arrayHeaderBytes(), base.maxWorkGroupSize(),
                base.localMemoryBytes(), base.warpSize(), base.deviceLaunch(), base.nvrtcVersion(), base.arch());
    }

    /**
     * {@code File.java:17: error: TC-E4011 message}: the form compilers print, so editors and Compiler
     * Explorer mark the line. A diagnostic about the kernel as a whole (no instruction) points at the
     * kernel's first line; without that either, the diagnostic as TC renders it.
     */
    static String render(Diagnostic d, Kernel kernel) {
        if (d.loc().line() >= 0 && !d.loc().file().isEmpty()) {
            return d.loc().file() + ":" + d.loc().line() + ": error: " + d.code() + " " + d.message();
        }
        if (kernel.line() > 0 && kernel.line() < Integer.MAX_VALUE && kernel.file() != null) {
            return kernel.file() + ":" + kernel.line() + ": error: " + d.code() + " " + d.message();
        }
        return "[TC] " + d.code() + " " + d.message() + (d.where().isBlank() ? "" : " in " + d.where());
    }

    /** Refusals a value the runtime would know fixes: the launch domain (E3103), an object's layout (E3101). */
    private static final Set<String> NEEDS_VALUES = Set.of("TC-E3103", "TC-E3101");

    private static TcCompiler.Compilation compile(Frontend.BytecodeSource source, Kernel kernel, TargetCaps target, Method method, Map<String, String> given, boolean assume,
            List<String> assumed) {
        // Facts need the loaded class (parameter types and names, object constructors): none in a native image.
        // Without values given or assumed, no facts at all: the kernel TC compiles before any argument is seen.
        List<ValueSpecialisation.Fact> facts = method == null || (given.isEmpty() && !assume) ? List.of() : TypeLayouts.facts(method, given, assume, assumed);
        return TcCompiler.compileJava(source, kernel.owner().replace('/', '.'), kernel.name(), kernel.descriptor(), target, TcOptions.DEFAULTS, facts);
    }

    /** The kernel as a loaded method, when the source loads classes and the class loads. */
    private static Method method(Frontend.BytecodeSource source, Kernel kernel) {
        var loader = source.loader();
        if (loader.isEmpty()) {
            return null;
        }
        try {
            var owner = Class.forName(kernel.owner().replace('/', '.'), false, loader.get());
            for (var method : owner.getDeclaredMethods()) {
                if (method.getName().equals(kernel.name()) && Modifier.isStatic(method.getModifiers())
                        && (kernel.descriptor() == null || kernel.descriptor().equals(MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString()))) {
                    return method;
                }
            }
        } catch (ClassNotFoundException | LinkageError e) {
            // The class does not load (a missing dependency): compile without facts, as from class bytes.
        }
        return null;
    }

    private static String location(Kernel kernel) {
        return kernel.file() != null && kernel.line() > 0 && kernel.line() < Integer.MAX_VALUE ? kernel.file() + ":" + kernel.line() + ": " : "";
    }

    /** The kernel {@code Class method} names; its descriptor and line when the class file has the method. */
    private static Kernel named(Frontend.BytecodeSource source, String owner, String name) {
        var classFile = source.classFile(owner);
        var line = classFile.stream().flatMap(c -> c.methods().stream()).filter(m -> m.name().equals(name)).findFirst() //
                .flatMap(ClassFile.Method::code).map(code -> code.lines().stream().mapToInt(ClassFile.LineNumber::line).min().orElse(0)).orElse(0);
        return new Kernel(owner, name, null, line, sourceFile(owner));
    }

    /** The source file of a class, named the way the frontend names it in locations: the outer class + .java. */
    static String sourceFile(String owner) {
        var simple = owner.substring(owner.lastIndexOf('/') + 1);
        return (simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple) + ".java";
    }

    /**
     * On a JVM, classes loaded from the class path, so that the source resolves what only the class
     * initialiser knows (device kernels, enum constants, computed constants). In a native image, where
     * classes cannot be loaded, class bytes only.
     */
    private static Frontend.BytecodeSource source(String path) {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            return Frontend.BytecodeSource.classpath(path);
        }
        var urls = new ArrayList<URL>();
        for (var entry : path.split(File.pathSeparator)) {
            try {
                urls.add(Path.of(entry).toUri().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return Frontend.BytecodeSource.classLoader(new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()));
    }

    /** The kernels of every class in the directories on {@code classPath}, in source order. */
    static List<Kernel> kernels(String classPath) {
        var kernels = new ArrayList<Kernel>();
        for (var entry : classPath.split(File.pathSeparator)) {
            var root = Path.of(entry);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (var file : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                    kernels.addAll(kernels(ClassFile.parse(Files.readAllBytes(file))));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return kernels;
    }

    /** The kernels of one class: see the class comment for which methods count. TornadoVM's own API has none. */
    static List<Kernel> kernels(ClassFile classFile) {
        if (classFile.name().startsWith(API)) {
            return List.of();
        }
        var children = deviceKernelChildren(classFile);
        var kernels = new ArrayList<Kernel>();
        for (var m : classFile.methods()) {
            boolean candidate = m.isStatic() && (m.access() & (ACC_SYNTHETIC | ACC_BRIDGE)) == 0 && m.code().isPresent() && m.descriptor().endsWith(")V") && !m.name().equals("main")
                    && !m.name().startsWith("lambda$") && !m.name().startsWith("<") && !children.contains(m.name() + m.descriptor()) && kernelParameters(m.descriptor());
            if (candidate) {
                int line = m.code().get().lines().stream().mapToInt(ClassFile.LineNumber::line).min().orElse(Integer.MAX_VALUE);
                kernels.add(new Kernel(classFile.name(), m.name(), m.descriptor(), line, sourceFile(classFile.name())));
            }
        }
        kernels.sort(Comparator.comparingInt(Kernel::line));
        return kernels;
    }

    /**
     * Methods of the class that are passed to {@code DeviceKernel.of}: an {@code invokedynamic} whose
     * bootstrap arguments name a method of this class, consumed by {@code invokestatic DeviceKernel.of}.
     */
    static Set<String> deviceKernelChildren(ClassFile classFile) {
        var children = new HashSet<String>();
        var pool = classFile.constants();
        for (var m : classFile.methods()) {
            if (m.code().isEmpty()) {
                continue;
            }
            var insns = Bytecode.decode(m.code().get().bytes());
            for (int i = 0; i + 1 < insns.size(); i++) {
                var next = insns.get(i + 1);
                if (insns.get(i).opcode() != Bytecode.INVOKEDYNAMIC || next.opcode() != Bytecode.INVOKESTATIC) {
                    continue;
                }
                var callee = pool.member(next.index());
                if (!callee.owner().equals(DEVICE_KERNEL) || !callee.name().equals("of")) {
                    continue;
                }
                var site = pool.dynamicSite(insns.get(i).index());
                for (int argument : classFile.bootstrapMethods().get(site.bootstrap()).arguments()) {
                    pool.methodHandle(argument).filter(ref -> ref.owner().equals(classFile.name())).ifPresent(ref -> children.add(ref.name() + ref.descriptor()));
                }
            }
        }
        return children;
    }

    /** At least one parameter, each a primitive, a primitive array or a TornadoVM API type. */
    static boolean kernelParameters(String descriptor) {
        var params = descriptor.substring(1, descriptor.indexOf(')'));
        if (params.isEmpty()) {
            return false;
        }
        for (int i = 0; i < params.length();) {
            char c = params.charAt(i);
            if (c == '[') {
                if (i + 1 >= params.length() || "ZBCSIJFD".indexOf(params.charAt(i + 1)) < 0) {
                    return false;
                }
                i += 2;
            } else if (c == 'L') {
                int end = params.indexOf(';', i);
                if (!params.startsWith(API, i + 1)) {
                    return false;
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return true;
    }

    /** The TornadoVM version, from the name of the jar this class is in ({@code tc-runtime-8.0.1.jar}). */
    static String version() {
        try {
            var location = Tc.class.getProtectionDomain().getCodeSource();
            var name = location == null ? "" : Path.of(location.getLocation().toURI()).getFileName().toString();
            var matcher = java.util.regex.Pattern.compile("tc-runtime-(.+)\\.jar").matcher(name);
            return matcher.matches() ? matcher.group(1) : "(development build)";
        } catch (java.net.URISyntaxException | RuntimeException e) {
            return "(development build)";
        }
    }

    private static String withApi(String classPath) {
        var entries = new ArrayList<>(List.of(classPath.split(File.pathSeparator)));
        var home = System.getenv("TORNADOVM_HOME");
        var jars = home == null ? null : new File(home, "share/java/tornado").listFiles((dir, name) -> name.startsWith("tornado-api") && name.endsWith(".jar"));
        if (jars != null) {
            for (var jar : jars) {
                entries.add(jar.getPath());
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException(args[i - 1] + " needs a value");
        }
        return args[i];
    }

    private static boolean onOff(String value) {
        return switch (value) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException("expected on or off, not " + value);
        };
    }

    private static int usage(PrintStream err) {
        err.println("usage: tc emit [--target cuda|opencl|metal|hip] [--cc N] [--fp16 on|off] [--fp64 on|off] [--arg name=value]... [--cp PATH] [--stats] (Class method | --all)");
        return 2;
    }
}
