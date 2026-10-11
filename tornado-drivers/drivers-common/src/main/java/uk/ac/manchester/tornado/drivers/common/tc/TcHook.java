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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.tc.debug.HtmlReport;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.ErrorCodes;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.analysis.KernelFeatures;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The runtime side of TC, TornadoVM's kernel compiler: compiles a task method, specialised on the
 * actual task arguments (array lengths, scalars), into the source, entry point and launch domain,
 * and records statistics. A kernel TC cannot compile raises the TornadoVM exception for the
 * diagnostic's category.
 *
 * <p>Options: {@code tornado.tc.verbose=true} (one line per kernel), {@code tornado.tc.stats=true}
 * (summary at exit), and the {@code tornado.tc.*} dump options of {@link TcOptions}.</p>
 */
public final class TcHook {

    private static final boolean VERBOSE = Boolean.getBoolean("tornado.tc.verbose");
    private static final boolean STATS = Boolean.getBoolean("tornado.tc.stats");

    private static final AtomicInteger COMPILED = new AtomicInteger();
    private static final Map<String, AtomicInteger> REFUSED = new ConcurrentHashMap<>();

    static {
        if (STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(TcHook::printStats));
        }
    }

    /** A kernel compiled by TC. */
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos, AtomicSlots.Info atomics, KernelFeatures features) {
    }

    private TcHook() {
    }

    /**
     * Compiles {@code method} for the actual {@code arguments}. Returns empty when TC cannot compile
     * it; throws when fallback is disabled.
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target) {
        return compile(method, arguments, target, 0, 0, 0);
    }

    /**
     * As {@link #compile(Method, Object[], TargetCaps)}; {@code batchThreads > 0} when the task runs in
     * batches; {@code numThreads > 0} when the runtime fixes the number of threads (a reduction over a
     * size that is not a power of two runs on the largest power-of-two prefix; the host folds the rest).
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset) {
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, "?");
    }

    /** As above, for task {@code taskId} (named in device-launch reports). */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId) {
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, false);
    }

    /** As above, for a task of a plan with {@code withStrictFloatingPoint()} when {@code strictFloatingPoint}. */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint) {
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, strictFloatingPoint, false);
    }

    /** As above; {@code workerGrid} when the task runs with a worker grid of the user's (no launch domain needed). */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint, boolean workerGrid) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        var facts = TcArguments.facts(method, arguments, batchThreads, batchIndexOffset);
        if (workerGrid) {
            facts.add(new ValueSpecialisation.Fact.WorkerGrid());
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        TcCompiler.Compilation c;
        var options = TcOptions.fromSystemProperties().withTask(taskId).withStrictFloatingPoint(strictFloatingPoint);
        try {
            c = TcCompiler.compileJava(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor, target, options, facts);
        } catch (RuntimeException | StackOverflowError e) {
            // An internal TC failure becomes a diagnostic, reported like any other.
            if (VERBOSE) {
                e.printStackTrace();
            }
            return refuse(name, "TC-E9003", "internal compiler error: " + e);
        }
        if (options.htmlReport() && options.selects(name)) {
            writeReport(options.dumpDir().resolve(name.replace('.', '_') + ".report.html"), c);
        }
        if (!c.succeeded()) {
            var d = c.diagnostics().isEmpty() ? new Diagnostic("TC-E9003", "compilation failed without a diagnostic", "", Loc.UNKNOWN) : c.diagnostics().get(0);
            return reject(name, d);
        }
        COMPILED.incrementAndGet();
        if (VERBOSE) {
            System.out.printf("[TC] compiled %s in %.2f ms%n", name, c.nanos() / 1e6);
        }
        Optional<DomainTree> domain = Optional.empty();
        if (!c.domain().isEmpty()) {
            var tree = new DomainTree(c.domain().size());
            boolean reduction = facts.stream().anyMatch(f -> f instanceof ValueSpecialisation.Fact.Reduce);
            for (var d : c.domain()) {
                long upper = reduction && d.dim() == 0 && numThreads > 0 ? d.offset() + numThreads : d.upper();
                // The runtime's launch domains are int: a wider one would wrap.
                if (d.offset() != (int) d.offset() || d.step() != (int) d.step() || upper != (int) upper) {
                    var why = "the launch domain of dimension " + d.dim() + " (" + d.offset() + " to " + upper + ") does not fit the runtime's int domains";
                    return reject(name, new Diagnostic("TC-E3209", why, "", Loc.UNKNOWN));
                }
                tree.set(d.dim(), new IntDomain((int) d.offset(), (int) d.step(), (int) upper));
            }
            domain = Optional.of(tree);
        }
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos(), c.atomics(), c.features()));
    }

    /** -Dtornado.tc.report=html: every stage of one compilation, refused ones included, in one page. */
    private static void writeReport(Path file, TcCompiler.Compilation c) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, HtmlReport.render(c));
        } catch (IOException e) {
            System.err.println("[TC] cannot write " + file + ": " + e.getMessage());
        }
    }

    private static Optional<Kernel> refuse(String name, String code, String reason) {
        return reject(name, new Diagnostic(code, reason, "", Loc.UNKNOWN));
    }

    /** TC cannot compile {@code name}: the diagnostic becomes the TornadoVM exception for its category (ErrorCodes). */
    private static Optional<Kernel> reject(String name, Diagnostic d) {
        REFUSED.computeIfAbsent(d.code(), k -> new AtomicInteger()).incrementAndGet();
        var category = ErrorCodes.category(d.code());
        var e = exception(name, d, category);
        if (e instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) e;
    }

    /** The TornadoVM exception for a diagnostic, the one TornadoVM has always thrown for that kind of kernel. */
    static Throwable exception(String name, Diagnostic d, ErrorCodes.Category category) {
        var text = message(name, d);
        if (d.code().equals("TC-E4012")) {
            // A device without fp64 (Metal; OpenCL without cl_khr_fp64).
            return new TornadoDeviceFP64NotSupported(text);
        }
        return switch (category) {
            // TornadoInternalError formats its message: pass the text as an argument, not as the format.
            case INTERNAL -> new TornadoInternalError("%s", "TC internal error (please report with the kernel): " + text);
            // Kernels the GPU cannot run as written raise an Error.
            case INVALID_KERNEL -> switch (d.code()) {
                case "TC-E1002", "TC-E1004", "TC-E2108" -> new TornadoInternalError("%s", text);
                default -> new TornadoBailoutRuntimeException(text);
            };
            case UNSUPPORTED, BAILOUT -> new TornadoBailoutRuntimeException(text);
        };
    }

    /** {@code [TC] cannot compile Class.method: TC-E1004 message / at File.java:49 (bci 7) / hint: ...}. */
    static String message(String name, Diagnostic d) {
        var sb = new StringBuilder("[TC] cannot compile ").append(name).append(": ").append(d.code()).append(' ').append(d.message());
        if (d.loc().isKnown()) {
            sb.append("\n    at ").append(d.loc().file()).append(':').append(d.loc().line());
            if (d.loc().bci() >= 0) {
                sb.append(" (bci ").append(d.loc().bci()).append(')');
            }
        }
        if (!d.where().isBlank()) {
            sb.append("\n    in ").append(d.where());
        }
        var hint = ErrorCodes.hint(d.code());
        if (!hint.isBlank()) {
            sb.append("\n    hint: ").append(hint);
        }
        return sb.toString();
    }

    private static void printStats() {
        var reasons = new TreeMap<String, Integer>();
        REFUSED.forEach((k, v) -> reasons.put(k, v.get()));
        System.out.println("[TC] kernels compiled by TC: " + COMPILED.get() + ", refused: " + reasons.values().stream().mapToInt(Integer::intValue).sum() + " " + reasons);
    }
}
