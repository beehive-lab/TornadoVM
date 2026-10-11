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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import uk.ac.manchester.tornado.tc.codegen.CEmitter;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IrModule;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.opt.Canonicalizer;
import uk.ac.manchester.tornado.tc.opt.CfgSimplify;
import uk.ac.manchester.tornado.tc.opt.DeadCodeElimination;
import uk.ac.manchester.tornado.tc.opt.Inliner;
import uk.ac.manchester.tornado.tc.opt.FmaRematerialization;
import uk.ac.manchester.tornado.tc.opt.MathPeepholes;
import uk.ac.manchester.tornado.tc.opt.PassManager;
import uk.ac.manchester.tornado.tc.lower.S;
import uk.ac.manchester.tornado.tc.lower.SPrinter;
import uk.ac.manchester.tornado.tc.lower.Structurizer;
import uk.ac.manchester.tornado.tc.tornado.AbiLowering;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.BatchOffset;
import uk.ac.manchester.tornado.tc.tornado.Legality;
import uk.ac.manchester.tornado.tc.tornado.ObjectLayout;
import uk.ac.manchester.tornado.tc.frontend.LocalArrays;
import uk.ac.manchester.tornado.tc.frontend.LocalCollections;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
import uk.ac.manchester.tornado.tc.tornado.TileTyping;
import uk.ac.manchester.tornado.tc.tornado.ReductionLowering;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * Drives one TC compilation from TC-SSA to target source and records every stage: its text form,
 * time and IR size. Stages are dumped on request ({@link TcOptions}), verified after every IR change
 * by default, and reported as JFR events ({@code tc.Compilation}, {@code tc.Stage}). A failure never
 * escapes as an exception: it becomes a {@link Compilation} with diagnostics.
 */
public final class TcCompiler {

    /** Kind of text a stage produced, which decides how it is displayed. */
    public enum Kind {
        BYTECODE, IR, STRUCTURED, SOURCE, NONE
    }

    /** One pipeline stage: its output text (empty unless dumped or reported), duration and size. */
    public record Stage(String name, Kind kind, String text, long nanos, int size) {
    }

    /** The result of compiling one function. */
    public record Compilation(String function, TargetCaps target, boolean succeeded, String source, String entryName, List<Stage> stages, List<Diagnostic> diagnostics, long nanos,
            List<ParallelLoopLowering.Domain> domain, AtomicSlots.Info atomics) {
    }

    private static final class Failed extends RuntimeException {
        private final transient List<Diagnostic> diagnostics;

        Failed(List<Diagnostic> diagnostics) {
            super(null, null, false, false);
            this.diagnostics = diagnostics;
        }
    }

    private final Function function;
    private final TargetCaps target;
    private final TcOptions options;
    private List<ValueSpecialisation.Fact> facts = List.of();
    /** The kernel's module with its callees, from Java; null when compiling one function of IR text. */
    private IrModule module;
    private final List<Stage> stages = new ArrayList<>();
    private CEmitter.Result emitted;

    private TcCompiler(Function function, TargetCaps target, TcOptions options) {
        this.function = function;
        this.target = target;
        this.options = options;
    }

    /** Compiles {@code function} (pre-ABI TC-SSA); the function is lowered in place. */
    public static Compilation compile(Function function, TargetCaps target, TcOptions options) {
        return new TcCompiler(function, target, options).run();
    }

    /**
     * Compiles a Java kernel method from its class bytes: the frontend (shown as the stages
     * {@code bytecode} and {@code frontend}) followed by the pipeline of {@link #compile}.
     */
    public static Compilation compileJava(Frontend.BytecodeSource source, String className, String methodName, TargetCaps target, TcOptions options) {
        return compileJava(source, className, methodName, target, options, List.of());
    }

    /**
     * As {@link #compileJava(Frontend.BytecodeSource, String, String, TargetCaps, TcOptions)}, specialised
     * for the given facts about the actual task arguments (one per Java parameter; see
     * {@link ValueSpecialisation}). The result is only valid for those facts.
     */
    public static Compilation compileJava(Frontend.BytecodeSource source, String className, String methodName, TargetCaps target, TcOptions options, List<ValueSpecialisation.Fact> facts) {
        return compileJava(source, className, methodName, null, target, options, facts);
    }

    /**
     * How the kernel uses each parameter ({@link uk.ac.manchester.tornado.tc.tornado.AccessAnalysis}
     * bit sets, Java parameter order), from its bytecode alone: frontend and inlining, no target and
     * no argument facts. Empty when the frontend cannot translate the method.
     */
    public static java.util.Optional<int[]> access(Frontend.BytecodeSource source, String className, String methodName, String descriptor) {
        return javaLevel(source, className, methodName, descriptor).map(uk.ac.manchester.tornado.tc.tornado.AccessAnalysis::run);
    }

    /** Whether the kernel writes a loop index as data (batched execution offsets it per batch). */
    public static java.util.Optional<Boolean> writesLoopIndex(Frontend.BytecodeSource source, String className, String methodName, String descriptor) {
        return javaLevel(source, className, methodName, descriptor).map(uk.ac.manchester.tornado.tc.tornado.AccessAnalysis::writesLoopIndex);
    }

    /** The reductions of a task with {@code @Reduce} parameters {@code reduceIndices}, for the runtime's task-graph rewrite. */
    public static java.util.Optional<uk.ac.manchester.tornado.tc.tornado.ReductionLowering.Info> reductions(Frontend.BytecodeSource source, String className, String methodName, String descriptor,
            List<Integer> reduceIndices) {
        return javaLevel(source, className, methodName, descriptor).map(f -> {
            // An instance method's receiver is parameter 0 of the function, not of the Java signature.
            int receiver = f.params().size() - parameterCount(descriptor);
            var info = uk.ac.manchester.tornado.tc.tornado.ReductionLowering.info(f, reduceIndices.stream().map(i -> i + receiver).toList());
            return new uk.ac.manchester.tornado.tc.tornado.ReductionLowering.Info(info.operations(), info.boundParameter() < 0 ? -1 : info.boundParameter() - receiver, info.boundConstant());
        });
    }

    private static int parameterCount(String descriptor) {
        int count = 0;
        for (int i = 1; descriptor.charAt(i) != ')'; i++) {
            char c = descriptor.charAt(i);
            while (c == '[') {
                c = descriptor.charAt(++i);
            }
            if (c == 'L') {
                i = descriptor.indexOf(';', i);
            }
            count++;
        }
        return count;
    }

    /** The kernel's IR after the frontend, inlining and local collections: the Java-level view the runtime asks about. */
    private static java.util.Optional<Function> javaLevel(Frontend.BytecodeSource source, String className, String methodName, String descriptor) {
        var translated = Frontend.translateWithCallees(source, className, methodName, descriptor);
        if (translated.module().isEmpty()) {
            return java.util.Optional.empty();
        }
        var module = translated.module().get();
        var function = module.functions().get(0);
        if (module.functions().size() > 1 && !Inliner.run(module, function).isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!LocalCollections.run(function).isEmpty()) {
            return java.util.Optional.empty();
        }
        // Fold inlining's return blocks (a callee's result reaches its use through a block argument).
        new uk.ac.manchester.tornado.tc.opt.Canonicalizer().run(function);
        new uk.ac.manchester.tornado.tc.opt.CfgSimplify().run(function);
        return java.util.Optional.of(function);
    }

    /** As above for one overload: {@code descriptor} is the JVM method descriptor, {@code null} for the first match. */
    public static Compilation compileJava(Frontend.BytecodeSource source, String className, String methodName, String descriptor, TargetCaps target, TcOptions options,
            List<ValueSpecialisation.Fact> facts) {
        var parent = compileJava(source, className, methodName, descriptor, target, options, facts, null);
        return parent.function() == null || !parent.compilation().succeeded() ? parent.compilation() : withDeviceLaunches(source, parent, target, options);
    }

    /** A compilation, with the lowered function and the emitted parts when it succeeded (null otherwise). */
    private record Compiled(Compilation compilation, Function function, CEmitter.Result emitted) {
    }

    /** A kernel launched from the device: {@code Owner.method} and its JVM descriptor. */
    private record Launched(String callee, String descriptor) {
    }

    /**
     * Kernels launched from the device ({@code KernelContext.launch}) are compiled as {@code __global__}
     * functions of the same CUDA module, with no facts (they get their arguments from the device).
     * Each child is compiled once, so launch cycles (ping launches pong launches ping) terminate.
     */
    private static Compilation withDeviceLaunches(Frontend.BytecodeSource source, Compiled parent, TargetCaps target, TcOptions options) {
        var work = new java.util.ArrayDeque<>(launchedKernels(parent.function()));
        if (work.isEmpty()) {
            return parent.compilation();
        }
        var seen = new java.util.HashMap<String, String>();
        var children = new ArrayList<CEmitter.Result>();
        long nanos = 0;
        while (!work.isEmpty()) {
            var launch = work.poll();
            var child = launch.callee();
            var known = seen.putIfAbsent(child, launch.descriptor());
            if (known != null) {
                if (!known.equals(launch.descriptor())) {
                    // Each launched kernel is one entry point named after its method (CEmitter.deviceKernelName).
                    var p = parent.compilation();
                    var problem = new Diagnostic("TC-E3208", "two overloads of " + child + " are launched from the device", "", Loc.UNKNOWN);
                    return new Compilation(p.function(), p.target(), false, "", "", p.stages(), List.of(problem), p.nanos() + nanos, p.domain(), p.atomics());
                }
                continue;
            }
            int dot = child.lastIndexOf('.');
            var compiled = compileJava(source, child.substring(0, dot), child.substring(dot + 1), launch.descriptor(), target, options, List.of(), CEmitter.deviceKernelName(child));
            var c = compiled.compilation();
            nanos += c.nanos();
            if (!c.succeeded() || compiled.function() == null) {
                var problems = c.diagnostics().stream()
                        .map(d -> new Diagnostic(d.code(), "in " + child + ", launched from the device: " + d.message(), d.where(), d.loc(), d.line())).toList();
                var p = parent.compilation();
                return new Compilation(p.function(), p.target(), false, "", "", p.stages(), problems, p.nanos() + nanos, p.domain(), p.atomics());
            }
            if (!c.atomics().initial().isEmpty()) {
                // A child numbers its atomic slots from 0 like its parent and shares the parent's
                // atomics region: refused until children get their own slot range (design review TP-1).
                var p = parent.compilation();
                var problem = new Diagnostic("TC-E3207", "kernel " + child + ", launched from the device, uses atomic counters", "", Loc.UNKNOWN);
                return new Compilation(p.function(), p.target(), false, "", "", p.stages(), List.of(problem), p.nanos() + nanos, p.domain(), p.atomics());
            }
            children.add(compiled.emitted());
            work.addAll(launchedKernels(compiled.function()));
        }
        var p = parent.compilation();
        var source1 = CEmitter.link(parent.emitted(), children, target);
        return new Compilation(p.function(), p.target(), true, source1, p.entryName(), p.stages(), p.diagnostics(), p.nanos() + nanos, p.domain(), p.atomics());
    }

    /** The kernels {@code function} launches from the device. */
    private static List<Launched> launchedKernels(Function function) {
        var names = new ArrayList<Launched>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(CEmitter.DEVICE_LAUNCH)) {
                    names.add(new Launched(inst.immediates().requireString("callee"), inst.immediates().requireString("descriptor")));
                }
            }
        }
        return names;
    }

    /** {@code rename}: the function name to compile under (a device-launched kernel), {@code null} to keep it. */
    private static Compiled compileJava(Frontend.BytecodeSource source, String className, String methodName, String descriptor, TargetCaps target, TcOptions options,
            List<ValueSpecialisation.Fact> facts, String rename) {
        long start = System.nanoTime();
        var translated = Frontend.translateWithCallees(source, className, methodName, descriptor);
        java.util.Optional<Function> kernel = translated.module().map(m -> m.functions().get(0));
        if (rename != null) {
            kernel.ifPresent(k -> k.rename(rename));
        }
        List<Diagnostic> frontendDiagnostics = new ArrayList<>(translated.diagnostics());
        long frontendNanos = System.nanoTime() - start;
        var bytecode = new Stage("bytecode", Kind.BYTECODE, options.htmlReport() || options.dumps("bytecode") ? translated.bytecode() : "", 0, 0);
        if (kernel.isEmpty()) {
            var name = className.substring(className.lastIndexOf('.') + 1) + "." + methodName;
            return new Compiled(new Compilation(name, target, false, "", "", List.of(bytecode), frontendDiagnostics, frontendNanos, List.of(), new AtomicSlots.Info(List.of(), java.util.Map.of())), null, null);
        }
        var compiler = new TcCompiler(kernel.get(), target, options);
        compiler.facts = List.copyOf(facts);
        compiler.module = translated.module().get();
        compiler.stages.add(bytecode);
        var c = compiler.run();
        var stages = new ArrayList<>(c.stages());
        var size = stages.size() > 1 ? stages.get(1).size() : 0;
        stages.add(1, new Stage("frontend", Kind.NONE, "", frontendNanos, size));
        return new Compiled(new Compilation(c.function(), c.target(), c.succeeded(), c.source(), c.entryName(), List.copyOf(stages), c.diagnostics(), c.nanos() + frontendNanos, c.domain(), c.atomics()),
                c.succeeded() ? compiler.function : null, c.succeeded() ? compiler.emitted : null);
    }

    /**
     * One step of the IR pipeline: when it applies, what it does (diagnostics fail the compilation)
     * and the IR level it leaves, which the verifier checks after it ({@code verify=each}).
     */
    private record Step(String name, Predicate<TcCompiler> applies, java.util.function.Function<TcCompiler, List<Diagnostic>> run, Verifier.Stage leaves) {
    }

    /**
     * The IR pipeline, in order. Ordering rules: inlining first (later steps see one function);
     * specialisation before anything that needs constant bounds (tiles, reductions, the launch
     * domain); atomics, reductions and batch offsets before parallel-loop lowering, which consumes the
     * {@code parallel} loops they look at; local arrays after optimisation folds their lengths; ABI
     * legality once types and local-array sizes are final; ABI lowering last, after which the IR is
     * post-ABI.
     */
    private static final List<Step> PIPELINE = List.of(
            new Step("inline", c -> c.module != null && c.module.functions().size() > 1, c -> Inliner.run(c.module, c.function), Verifier.Stage.PRE_ABI),
            new Step("local-collections", c -> c.module != null, c -> LocalCollections.run(c.function), Verifier.Stage.PRE_ABI),
            new Step("specialise", c -> !c.facts.isEmpty(), TcCompiler::specialise, Verifier.Stage.PRE_ABI),
            new Step("tiles", c -> TileTyping.usesTiles(c.function), TcCompiler::tiles, Verifier.Stage.PRE_ABI),
            new Step("atomics", c -> c.usesIntrinsic("tornado.atomic_int."), c -> AtomicSlots.run(c.function), Verifier.Stage.PRE_ABI),
            new Step("reduce", c -> c.facts.stream().anyMatch(f -> f instanceof ValueSpecialisation.Fact.Reduce),
                    c -> ReductionLowering.run(c.function, c.facts.stream().map(f -> f instanceof ValueSpecialisation.Fact.Reduce).toList(), c.target.maxWorkGroupSize()), Verifier.Stage.PRE_ABI),
            new Step("batch", c -> c.batchOffset().isPresent(), c -> {
                BatchOffset.run(c.function, c.batchOffset().getAsLong());
                return List.of();
            }, Verifier.Stage.PRE_ABI),
            new Step("parallel", c -> true, TcCompiler::parallel, Verifier.Stage.PRE_ABI),
            new Step("optimize", c -> true, TcCompiler::optimize, Verifier.Stage.PRE_ABI),
            new Step("local-arrays", c -> LocalArrays.hasPending(c.function), c -> LocalArrays.run(c.function), Verifier.Stage.PRE_ABI),
            new Step("legality", c -> true, c -> Legality.run(c.function, c.target), Verifier.Stage.PRE_ABI),
            new Step("abi", c -> true, c -> AbiLowering.run(c.function, c.target, c.layouts()), Verifier.Stage.POST_ABI),
            new Step("optimize.post-abi", c -> true, c -> {
                PassManager.standard(Verifier.Stage.POST_ABI).verifyEach(c.options.verifyEach())
                        .observe((run, fn) -> TcTrace.current().ir("optimize.post-abi", run.pass(), run.changed(), run.nanos(), fn)).run(c.function);
                return List.of();
            }, Verifier.Stage.POST_ABI),
            // Last: GVN would merge the copies again. NVRTC contracts a*b+c by default; the other
            // dialects keep the shared product.
            new Step("fma-remat", c -> c.options.mathOptimizations() && c.target.dialect() == TargetCaps.Dialect.CUDA && FmaRematerialization.applies(c.function), c -> {
                new FmaRematerialization().run(c.function);
                return List.of();
            }, Verifier.Stage.POST_ABI));

    /** Folds what the argument values made constant (loop bounds such as n + 1), so later steps see constants. */
    private List<Diagnostic> specialise() {
        ValueSpecialisation.run(function, facts);
        // Through inlined getters too: merging blocks removes the call's return argument.
        for (int round = 0; round < 4 && (new Canonicalizer().run(function) | new CfgSimplify().run(function)); round++) {
        }
        new DeadCodeElimination().run(function);
        return List.of();
    }

    private List<Diagnostic> tiles() {
        return TileTyping.run(function);
    }

    private List<Diagnostic> parallel() {
        boolean hasParallelLoops = function.blocks().stream().anyMatch(b -> b.attributes().stream().anyMatch(a -> a.name().equals("parallel")));
        var problems = ParallelLoopLowering.run(function);
        if (!problems.isEmpty()) {
            return problems;
        }
        // With the task's arguments known, every @Parallel loop must give a launch domain: without
        // one the kernel would run on a single thread.
        if (!facts.isEmpty() && hasParallelLoops && ParallelLoopLowering.domain(function).isEmpty()) {
            return List.of(new Diagnostic("TC-E3103", "the bounds of the @Parallel loops are not compile-time constants after specialisation, so no launch domain can be derived", "", Loc.UNKNOWN));
        }
        return List.of();
    }

    private List<Diagnostic> optimize() {
        var passes = PassManager.standard(Verifier.Stage.PRE_ABI).verifyEach(options.verifyEach());
        if (options.mathOptimizations()) {
            passes.add(new MathPeepholes()).add(new DeadCodeElimination());
        }
        passes.observe((run, fn) -> TcTrace.current().ir("optimize", run.pass(), run.changed(), run.nanos(), fn)).run(function);
        return List.of();
    }

    private boolean usesIntrinsic(String prefix) {
        return function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof uk.ac.manchester.tornado.tc.ir.Opcode.Intrinsic(var n) && n.startsWith(prefix));
    }

    private java.util.OptionalLong batchOffset() {
        return facts.stream().filter(f -> f instanceof ValueSpecialisation.Fact.IndexOffset).mapToLong(f -> ((ValueSpecialisation.Fact.IndexOffset) f).offset()).findFirst();
    }

    private List<ObjectLayout> layouts() {
        return facts.stream().filter(f -> f instanceof ValueSpecialisation.Fact.Instance).flatMap(f -> ((ValueSpecialisation.Fact.Instance) f).layouts().stream()).toList();
    }

    /** The verifier after step {@code name}; a failure names the step that broke the IR. */
    private void verify(String name, Verifier.Stage level) {
        var problems = Verifier.verify(function, level);
        if (!problems.isEmpty()) {
            throw new Failed(problems.stream().map(d -> new Diagnostic(d.code(), "after " + name + ": " + d.message(), d.where(), d.loc(), d.line())).toList());
        }
    }

    private Compilation run() {
        var event = new TcEvents.Compilation();
        event.begin();
        long start = System.nanoTime();
        String source = "";
        String entry = "";
        List<Diagnostic> diagnostics = List.of();
        boolean ok = true;
        try {
            stage("input", Kind.IR, () -> {
            }, () -> IrPrinter.print(function));
            check("verify.pre-abi", Verifier.Stage.PRE_ABI);
            for (var step : PIPELINE) {
                if (step.applies().test(this)) {
                    stage(step.name(), Kind.IR, () -> {
                        var problems = step.run().apply(this);
                        if (!problems.isEmpty()) {
                            throw new Failed(problems);
                        }
                        if (options.verifyEach()) {
                            verify(step.name(), step.leaves());
                        }
                    }, () -> IrPrinter.print(function));
                }
            }
            if (!options.verifyEach()) {
                check("verify.end", Verifier.Stage.POST_ABI);
            }
            var structured = new S.Kernel[1];
            stage("structure", Kind.STRUCTURED, () -> {
                try {
                    structured[0] = Structurizer.structure(function);
                } catch (Structurizer.StructuringException e) {
                    throw new Failed(List.of(new Diagnostic(e.code(), e.getMessage().substring(e.code().length() + 2), "@" + function.name(), Loc.UNKNOWN)));
                }
            }, () -> SPrinter.print(structured[0]));
            TcTrace.current().structured(function, structured[0]);
            var emitted = new CEmitter.Result[1];
            stage("emit", Kind.SOURCE, () -> {
                try {
                    emitted[0] = CEmitter.emit(structured[0], target, true, options.task());
                } catch (CEmitter.EmitException e) {
                    throw new Failed(List.of(new Diagnostic(e.code(), e.getMessage().substring(e.code().length() + 2), "@" + function.name(), Loc.UNKNOWN)));
                }
            }, () -> emitted[0].source());
            TcTrace.current().emitted(function, emitted[0]);
            source = emitted[0].source();
            entry = emitted[0].entryName();
            this.emitted = emitted[0];
        } catch (Failed f) {
            ok = false;
            diagnostics = List.copyOf(f.diagnostics);
        }
        long nanos = System.nanoTime() - start;
        event.end();
        if (event.shouldCommit()) {
            event.function = function.name();
            event.target = target.dialect() + "/sm_" + target.computeCapability();
            event.succeeded = ok;
            event.sourceLines = (int) source.lines().count();
            event.diagnostic = diagnostics.isEmpty() ? "" : diagnostics.get(0).code() + ": " + diagnostics.get(0).message();
            event.commit();
        }
        return new Compilation(function.name(), target, ok, source, entry, List.copyOf(stages), diagnostics, nanos, ParallelLoopLowering.domain(function), AtomicSlots.info(function));
    }

    private void check(String name, Verifier.Stage stage) {
        stage(name, Kind.NONE, () -> {
            var problems = Verifier.verify(function, stage);
            if (!problems.isEmpty()) {
                throw new Failed(problems);
            }
        }, () -> "");
    }

    /**
     * Runs one stage. Its text form is rendered only when a dump or report needs it: profiling the
     * C3 kernels showed that printing the IR costs as much as a lowering (decision review, C3).
     */
    private void stage(String name, Kind kind, Runnable work, Supplier<String> render) {
        var event = new TcEvents.Stage();
        event.begin();
        long start = System.nanoTime();
        try {
            work.run();
        } finally {
            event.end();
        }
        long nanos = System.nanoTime() - start;
        var wanted = kind != Kind.NONE && (options.htmlReport() || options.dumps(name)) && options.selects(function.name());
        var text = wanted ? render.get() : "";
        int size = function.blocks().stream().mapToInt(b -> b.insts().size()).sum();
        stages.add(new Stage(name, kind, text, nanos, size));
        if (kind == Kind.IR) {
            TcTrace.current().ir(name, null, true, nanos, function);
        }
        if (event.shouldCommit()) {
            event.function = function.name();
            event.stage = name;
            event.sizeAfter = size;
            event.commit();
        }
        if (wanted && options.dumps(name)) {
            dump(name, kind, text);
        }
    }

    private void dump(String name, Kind kind, String text) {
        var extension = switch (kind) {
            case IR -> "tcir";
            case STRUCTURED -> "tcs";
            case SOURCE -> target.dialect() == TargetCaps.Dialect.CUDA ? "cu" : "c";
            case BYTECODE, NONE -> "txt";
        };
        try {
            var dir = Files.createDirectories(options.dumpDir());
            Files.writeString(dir.resolve(String.format("%s.%02d-%s.%s", CEmitter.entryName(function), stages.size(), name, extension)), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
