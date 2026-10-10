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
import java.util.function.Supplier;

import uk.ac.manchester.tornado.tc.codegen.CEmitter;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.opt.Canonicalizer;
import uk.ac.manchester.tornado.tc.opt.CfgSimplify;
import uk.ac.manchester.tornado.tc.opt.DeadCodeElimination;
import uk.ac.manchester.tornado.tc.opt.Inliner;
import uk.ac.manchester.tornado.tc.opt.MathPeepholes;
import uk.ac.manchester.tornado.tc.opt.PassManager;
import uk.ac.manchester.tornado.tc.lower.S;
import uk.ac.manchester.tornado.tc.lower.SPrinter;
import uk.ac.manchester.tornado.tc.lower.Structurizer;
import uk.ac.manchester.tornado.tc.tornado.AbiLowering;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.BatchOffset;
import uk.ac.manchester.tornado.tc.frontend.LocalArrays;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
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

    /** As above for one overload: {@code descriptor} is the JVM method descriptor, {@code null} for the first match. */
    public static Compilation compileJava(Frontend.BytecodeSource source, String className, String methodName, String descriptor, TargetCaps target, TcOptions options,
            List<ValueSpecialisation.Fact> facts) {
        var parent = compileJava(source, className, methodName, descriptor, target, options, facts, null);
        return parent.compiler() == null || !parent.compilation().succeeded() ? parent.compilation() : withDeviceLaunches(source, parent, target, options);
    }

    private record Compiled(Compilation compilation, TcCompiler compiler) {
    }

    /**
     * Kernels launched from the device ({@code KernelContext.launch}) are compiled as {@code __global__}
     * functions of the same CUDA module, with no facts (they get their arguments from the device).
     * Each child is compiled once, so launch cycles (ping launches pong launches ping) terminate.
     */
    private static Compilation withDeviceLaunches(Frontend.BytecodeSource source, Compiled parent, TargetCaps target, TcOptions options) {
        var work = new java.util.ArrayDeque<>(launchedKernels(parent.compiler().function));
        if (work.isEmpty()) {
            return parent.compilation();
        }
        var seen = new java.util.HashSet<String>();
        var children = new ArrayList<CEmitter.Result>();
        long nanos = 0;
        while (!work.isEmpty()) {
            var child = work.poll();
            if (!seen.add(child)) {
                continue;
            }
            int dot = child.lastIndexOf('.');
            var compiled = compileJava(source, child.substring(0, dot), child.substring(dot + 1), null, target, options, List.of(), child + "_dp");
            var c = compiled.compilation();
            nanos += c.nanos();
            if (!c.succeeded() || compiled.compiler() == null) {
                var problems = c.diagnostics().stream()
                        .map(d -> new Diagnostic(d.code(), "in " + child + ", launched from the device: " + d.message(), d.where(), d.loc(), d.line())).toList();
                var p = parent.compilation();
                return new Compilation(p.function(), p.target(), false, "", "", p.stages(), problems, p.nanos() + nanos, p.domain(), p.atomics());
            }
            children.add(compiled.compiler().emitted);
            work.addAll(launchedKernels(compiled.compiler().function));
        }
        var p = parent.compilation();
        var source1 = CEmitter.link(parent.compiler().emitted, children, target);
        return new Compilation(p.function(), p.target(), true, source1, p.entryName(), p.stages(), p.diagnostics(), p.nanos() + nanos, p.domain(), p.atomics());
    }

    /** The {@code Owner.method} names of the kernels {@code function} launches from the device. */
    private static List<String> launchedKernels(Function function) {
        var names = new ArrayList<String>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(CEmitter.DEVICE_LAUNCH)) {
                    var rest = name.substring(CEmitter.DEVICE_LAUNCH.length());
                    names.add(rest.substring(rest.indexOf('.') + 1));
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
        if (kernel.isPresent() && translated.module().get().functions().size() > 1) {
            var problems = Inliner.run(translated.module().get(), kernel.get());
            if (!problems.isEmpty()) {
                frontendDiagnostics.addAll(problems);
                kernel = java.util.Optional.empty();
            }
        }
        long frontendNanos = System.nanoTime() - start;
        var bytecode = new Stage("bytecode", Kind.BYTECODE, options.htmlReport() || options.dumps("bytecode") ? translated.bytecode() : "", 0, 0);
        if (kernel.isEmpty()) {
            var name = className.substring(className.lastIndexOf('.') + 1) + "." + methodName;
            return new Compiled(new Compilation(name, target, false, "", "", List.of(bytecode), frontendDiagnostics, frontendNanos, List.of(), new AtomicSlots.Info(List.of(), java.util.Map.of())), null);
        }
        var compiler = new TcCompiler(kernel.get(), target, options);
        compiler.facts = List.copyOf(facts);
        compiler.stages.add(bytecode);
        var c = compiler.run();
        var stages = new ArrayList<>(c.stages());
        var size = stages.size() > 1 ? stages.get(1).size() : 0;
        stages.add(1, new Stage("frontend", Kind.NONE, "", frontendNanos, size));
        return new Compiled(new Compilation(c.function(), c.target(), c.succeeded(), c.source(), c.entryName(), List.copyOf(stages), c.diagnostics(), c.nanos() + frontendNanos, c.domain(), c.atomics()),
                compiler);
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
            if (!facts.isEmpty()) {
                // Fold what the argument values made constant (loop bounds such as n + 1), so the
                // parallel-loop lowering can record the launch domain.
                stage("specialise", Kind.IR, () -> {
                    ValueSpecialisation.run(function, facts);
                    // Through inlined getters too: merging blocks removes the call's return argument.
                    for (int round = 0; round < 4 && (new Canonicalizer().run(function) | new CfgSimplify().run(function)); round++) {
                    }
                    new DeadCodeElimination().run(function);
                }, () -> IrPrinter.print(function));
            }
            if (function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof uk.ac.manchester.tornado.tc.ir.Opcode.Intrinsic(var n) && n.startsWith("tornado.atomic_int."))) {
                stage("atomics", Kind.IR, () -> {
                    var problems = AtomicSlots.run(function);
                    if (!problems.isEmpty()) {
                        throw new Failed(problems);
                    }
                }, () -> IrPrinter.print(function));
            }
            var reduce = facts.stream().map(f -> f instanceof ValueSpecialisation.Fact.Reduce).toList();
            if (reduce.contains(true)) {
                stage("reduce", Kind.IR, () -> {
                    var problems = ReductionLowering.run(function, reduce);
                    if (!problems.isEmpty()) {
                        throw new Failed(problems);
                    }
                }, () -> IrPrinter.print(function));
            }
            facts.stream().filter(f -> f instanceof ValueSpecialisation.Fact.IndexOffset).findFirst()
                    .ifPresent(f -> stage("batch", Kind.IR, () -> BatchOffset.run(function, ((ValueSpecialisation.Fact.IndexOffset) f).offset()), () -> IrPrinter.print(function)));
            boolean hasParallelLoops = function.blocks().stream().anyMatch(b -> b.attributes().stream().anyMatch(a -> a.name().equals("parallel")));
            stage("parallel", Kind.IR, () -> {
                var problems = ParallelLoopLowering.run(function);
                if (!problems.isEmpty()) {
                    throw new Failed(problems);
                }
                // With the task's arguments known, every @Parallel loop must give a launch domain:
                // without one the kernel would run on a single thread.
                if (!facts.isEmpty() && hasParallelLoops && ParallelLoopLowering.domain(function).isEmpty()) {
                    throw new Failed(List.of(new Diagnostic("TC-E3103", "the bounds of the @Parallel loops are not compile-time constants after specialisation, so no launch domain can be derived", "", Loc.UNKNOWN)));
                }
            }, () -> IrPrinter.print(function));
            stage("optimize", Kind.IR, () -> {
                var passes = PassManager.standard(Verifier.Stage.PRE_ABI).verifyEach(options.verifyEach());
                if (options.mathOptimizations()) {
                    passes.add(new MathPeepholes()).add(new DeadCodeElimination());
                }
                passes.run(function);
            }, () -> IrPrinter.print(function));
            if (LocalArrays.hasPending(function)) {
                stage("local-arrays", Kind.IR, () -> {
                    var problems = LocalArrays.run(function);
                    if (!problems.isEmpty()) {
                        throw new Failed(problems);
                    }
                }, () -> IrPrinter.print(function));
            }
            stage("abi", Kind.IR, () -> {
                var layouts = facts.stream().filter(f -> f instanceof ValueSpecialisation.Fact.Instance).flatMap(f -> ((ValueSpecialisation.Fact.Instance) f).layouts().stream()).toList();
                var problems = AbiLowering.run(function, target, layouts);
                if (!problems.isEmpty()) {
                    throw new Failed(problems);
                }
            }, () -> IrPrinter.print(function));
            if (options.verifyEach()) {
                check("verify.post-abi", Verifier.Stage.POST_ABI);
            }
            stage("optimize.post-abi", Kind.IR, () -> PassManager.standard(Verifier.Stage.POST_ABI).verifyEach(options.verifyEach()).run(function), () -> IrPrinter.print(function));
            var structured = new S.Kernel[1];
            stage("structure", Kind.STRUCTURED, () -> {
                try {
                    structured[0] = Structurizer.structure(function);
                } catch (Structurizer.StructuringException e) {
                    throw new Failed(List.of(new Diagnostic(e.code(), e.getMessage().substring(e.code().length() + 2), "@" + function.name(), Loc.UNKNOWN)));
                }
            }, () -> SPrinter.print(structured[0]));
            var emitted = new CEmitter.Result[1];
            stage("emit", Kind.SOURCE, () -> {
                try {
                    emitted[0] = CEmitter.emit(structured[0], target);
                } catch (CEmitter.EmitException e) {
                    throw new Failed(List.of(new Diagnostic(e.code(), e.getMessage().substring(e.code().length() + 2), "@" + function.name(), Loc.UNKNOWN)));
                }
            }, () -> emitted[0].source());
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
