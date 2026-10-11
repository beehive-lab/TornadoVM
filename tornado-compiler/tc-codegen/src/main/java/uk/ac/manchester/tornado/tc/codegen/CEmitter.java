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
package uk.ac.manchester.tornado.tc.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Type.FloatType;
import uk.ac.manchester.tornado.tc.ir.Type.IntType;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.lower.S;

/**
 * Prints TC-S (post-ABI) as C-family source for a target dialect (path A of the architecture).
 *
 * <p>Readability rules: expressions use C precedence instead of full parenthesisation; a jump that C
 * can express structurally becomes {@code break;} or {@code continue;}, otherwise {@code goto}; a
 * loop whose body is a single test-and-exit becomes {@code while}. All locals are declared at the top
 * of the function, so a {@code goto} never skips an initialisation (required by CUDA C++).</p>
 *
 * <p>A dialect without {@code goto} (MSL) gets the same jumps through a flag: the jump sets
 * a flag ({@code tc_jump_}) to its number and breaks out of the innermost loop or switch, and after that
 * statement {@code if (tc_jump_ == k)} repeats the jump from there, until it is a plain {@code break}
 * or {@code continue} (which clears the flag). A labelled block that must be left from inside it
 * becomes {@code do { ... } while (false)}; the kernel is then emitted again with that block
 * wrapped.</p>
 *
 * <p>Java semantics: shift counts are masked, {@code >>>} shifts an unsigned value, float-to-integer
 * conversions saturate (the dialect names the conversion), and {@code Math.min/max} on floating
 * point use preamble helpers. Integer {@code + - *} are printed as C operators, as on the Graal path
 * (see {@code compatibility-contract.md} §3).</p>
 *
 * <p>Error codes: {@code TC-E4001} construct not supported by the target, {@code TC-E4002} unknown
 * intrinsic.</p>
 */
public final class CEmitter {

    /** Thrown when TC-S contains something the dialect cannot express. */
    public static final class EmitException extends RuntimeException {
        private final String code;

        EmitException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Emitted source plus what the runtime needs to install it. */
    /**
     * An emitted function: the whole translation unit ({@code source}), and its parts for
     * {@link #link}: the {@code #include} lines, the helpers it uses (dependencies first), its
     * definition and its forward declaration.
     */
    /**
     * {@code bodyOrigins}: for each line of {@code definition} after its header, the structured
     * statement that printed it (null for declarations and closing braces), so tools can map the
     * emitted source back to IR instructions.
     */
    public record Result(String source, String entryName, List<String> includes, List<String> helpers, String definition, String declaration, List<S.Stmt> bodyOrigins) {

        /** The structured statement that printed line {@code line} (0-based) of {@code source}, if any. */
        public java.util.Optional<S.Stmt> origin(int line) {
            int start = (int) source.substring(0, source.length() - definition.length()).lines().count();
            int body = start + (int) definition.substring(0, definition.indexOf("{\n") + 2).lines().count();
            int k = line - body;
            return k >= 0 && k < bodyOrigins.size() ? java.util.Optional.ofNullable(bodyOrigins.get(k)) : java.util.Optional.empty();
        }
    }

    /** Prefix of a device-side kernel launch ({@code KernelContext.launch}): {@code <prefix><mode>.<Owner>.<method>}. */
    /** A kernel launch from the device ({@code KernelContext.launch}); immediates {@code callee} and {@code mode}. */
    public static final String DEVICE_LAUNCH = "tornado.device_launch";

    /** The TC function name of a kernel launched from the device ({@code Owner.method}): {@code Owner.method_dp}. */
    public static String deviceKernelName(String ownerDotMethod) {
        return ownerDotMethod + "_dp";
    }

    /** Its entry point, as {@link #entryName} spells that function name: {@code Owner_method_dp}. */
    public static String deviceKernelEntry(String ownerDotMethod) {
        return deviceKernelName(ownerDotMethod).replace('.', '_').replace('$', '_');
    }

    /**
     * One translation unit holding {@code main} and the kernels it launches from the device: shared
     * includes and helpers once, then forward declarations (launches may form cycles), then the
     * device-launched kernels and finally {@code main}.
     */
    public static String link(Result main, List<Result> launched, TargetCaps target) {
        if (launched.isEmpty()) {
            return main.source();
        }
        var all = new ArrayList<Result>(launched);
        all.add(main);
        var includes = new LinkedHashSet<String>();
        var helperNames = new LinkedHashSet<String>();
        for (var r : all) {
            includes.addAll(r.includes());
            helperNames.addAll(r.helpers());
        }
        var source = new StringBuilder();
        includes.forEach(i -> source.append(i).append('\n'));
        if (!includes.isEmpty()) {
            source.append('\n');
        }
        helperNames.forEach(h -> source.append(Helpers.source(h, target.dialect())).append('\n'));
        for (var r : launched) {
            source.append(r.declaration());
        }
        source.append('\n');
        for (var r : all) {
            source.append(r.definition()).append(r == main ? "" : "\n");
        }
        return source.toString();
    }

    // C precedence levels (higher binds tighter).
    private static final int ATOM = 16, UNARY = 15, MUL = 13, ADD = 12, SHIFT = 11, REL = 10, EQ = 9, BAND = 8, BXOR = 7, BOR = 6, LAND = 5, LOR = 4, COND = 3;

    private record R(String text, int prec) {
    }

    private sealed interface Frame {
    }

    /**
     * {@code continueTargets}: labelled blocks that end just before the loop's latch, so leaving them is {@code continue}.
     * {@code flagged}: jumps (without goto) that leave the loop through the jump flag.
     */
    private record LoopFrame(String label, Set<String> breakTargets, Set<String> continueTargets, List<FlagJump> flagged) implements Frame {
    }

    private record SwitchFrame(Set<String> breakTargets, List<FlagJump> flagged) implements Frame {
    }

    /** A labelled block printed as {@code do { ... } while (false)} so that a jump can leave it (no goto). */
    private record BlockFrame(Set<String> breakTargets, List<FlagJump> flagged) implements Frame {
    }

    /** A labelled block without a C statement of its own; the place a jump to {@code label} lands. */
    private record LabelFrame(String label) implements Frame {
    }

    /** A jump on its way out through the jump flag, numbered {@code id}. */
    private record FlagJump(int id, String label, boolean isBreak) {
    }

    private final TargetCaps target;
    private final boolean lineComments;
    private final StringBuilder out = new StringBuilder();
    /** The statement each line of {@code out} belongs to (see {@link Result#bodyOrigins}). */
    private final List<S.Stmt> origins = new ArrayList<>();
    private S.Stmt current;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Set<String> neededLabels = new HashSet<>();
    private final Set<String> helpers = new LinkedHashSet<>();
    private final Map<S.Var, String> names = new IdentityHashMap<>();
    private final Map<S.Alloca, String> allocaNames = new IdentityHashMap<>();
    private Map<S.Var, Type> elementTypes = Map.of();
    private final CDialect dialect;
    private final Set<CDialect.Feature> features = java.util.EnumSet.noneOf(CDialect.Feature.class);
    private final String task;
    /** Labelled blocks to print as {@code do { ... } while (false)} (dialects without goto). */
    private final Set<String> wrapped;
    /** Labelled blocks found to need wrapping; non-empty means emit again. */
    private final Set<String> toWrap = new HashSet<>();
    private String jumpFlag;
    private int flagJumps;

    private CEmitter(TargetCaps target, boolean lineComments, String task, Set<String> wrapped) {
        this.dialect = CDialect.of(target.dialect());
        this.target = target;
        this.lineComments = lineComments;
        this.task = task;
        this.wrapped = wrapped;
    }

    public static Result emit(S.Kernel kernel, TargetCaps target) {
        return emit(kernel, target, true);
    }

    public static Result emit(S.Kernel kernel, TargetCaps target, boolean lineComments) {
        return emit(kernel, target, lineComments, "?");
    }

    /** {@code task}: the runtime's task id, printed by device-launch reports. */
    public static Result emit(S.Kernel kernel, TargetCaps target, boolean lineComments, String task) {
        return emit(kernel, target, lineComments, task, true);
    }

    /**
     * As above; {@code contract} false for a strict-floating-point plan: Java does not fuse a * b + c,
     * and OpenCL C lets the compiler contract unless the kernel says otherwise.
     */
    public static Result emit(S.Kernel kernel, TargetCaps target, boolean lineComments, String task, boolean contract) {
        var wrapped = new HashSet<String>();
        while (true) {
            var emitter = new CEmitter(target, lineComments, task, Set.copyOf(wrapped));
            if (!contract) {
                emitter.features.add(CDialect.Feature.NO_CONTRACT);
            }
            var result = emitter.kernel(kernel);
            if (emitter.toWrap.isEmpty() || !wrapped.addAll(emitter.toWrap)) {
                return result;
            }
        }
    }

    /** The entry-point name for a TC function name (dots and dollars become underscores). */
    public static String entryName(Function function) {
        return function.name().replace('.', '_').replace('$', '_');
    }

    // ---------------------------------------------------------------- kernel

    private Result kernel(S.Kernel k) {
        elementTypes = k.elementTypes();
        var used = new HashSet<String>();
        k.params().forEach(v -> names.put(v, cIdentifier(v.name(), used)));
        k.locals().forEach(v -> names.put(v, cIdentifier(v.name(), used)));
        k.allocas().forEach(a -> allocaNames.put(a, cIdentifier(a.name(), used)));
        jumpFlag = cIdentifier("tc_jump", used);

        var f = k.source();
        var entry = entryName(f);
        var header = new StringBuilder();
        var tiles = f.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof Opcode.Intrinsic(var n) && n.startsWith("tile."));
        if (tiles) {
            tileFeatures(f);
        }
        if (f.kind() == Function.Kind.KERNEL) {
            header.append(dialect.kernelPrefix(tiles)).append(entry).append('(').append(dialect.abiParameters());
            int index = dialect.abiParameterCount();
            for (var p : k.params()) {
                header.append(", ").append(dialect.kernelParameter(p.type(), declaration(p.type(), names.get(p)), names.get(p), index++));
            }
            header.append(dialect.trailingKernelParameters(index));
        } else {
            header.append(dialect.functionPrefix()).append(typeName(f.returnType())).append(' ').append(entry).append('(');
            header.append(k.params().stream().map(p -> declaration(p.type(), names.get(p))).collect(Collectors.joining(", ")));
        }
        header.append(")\n{\n");

        for (var alloca : k.allocas()) {
            var a = alloca.kind();
            // Java zero-fills `new T[n]` (private arrays); KernelContext local arrays start undefined, as on Graal.
            var init = a.space() == AddressSpace.PRIVATE ? " = {}" : "";
            line(1, dialect.arrayQualifier(a.space()) + typeName(a.element()) + " " + allocaNames.get(alloca) + "[" + a.count() + "]" + init + ";");
        }
        // Locals of one type share a declaration (`float x, v;`, `float *a_data, *b_data;`), in first-use order.
        var groups = new java.util.LinkedHashMap<String, List<String>>();
        for (var v : k.locals()) {
            if (v.type() instanceof Type.TileType t && t.view()) {
                continue; // a view's C++ type cannot be named: `auto` at its definition
            }
            var d = declarator(v);
            // Pointers and scalars of one base type get separate lines: `float x, v;` and `float *a_data, *b_data;`.
            groups.computeIfAbsent(d.base() + (d.pointer() ? " *" : ""), x -> new ArrayList<>()).add((d.pointer() ? "*" : "") + d.name());
        }
        groups.forEach((key, declarators) -> {
            var base = key.endsWith(" *") ? key.substring(0, key.length() - 2) : key;
            var current = new StringBuilder();
            for (var d : declarators) {
                if (current.length() > 0 && base.length() + current.length() + d.length() > 96) {
                    line(1, base + " " + current + ";");
                    current.setLength(0);
                }
                current.append(current.length() > 0 ? ", " : "").append(d);
            }
            line(1, base + " " + current + ";");
        });
        var body = k.body();
        if (f.returnType() instanceof Type.VoidType && !body.isEmpty() && body.get(body.size() - 1) instanceof S.Return(var value) && value.isEmpty()) {
            body = body.subList(0, body.size() - 1); // a void function ends anyway
        }
        int declarations = out.length();
        int declarationLines = origins.size();
        statements(body, Set.of(), 1);
        if (flagJumps > 0) {
            out.insert(declarations, "  int " + jumpFlag + " = 0;\n");
            origins.add(declarationLines, null);
        }
        out.append("}\n");

        var emitted = out.toString();
        var source = new StringBuilder();
        var includes = dialect.preamble(features);
        includes.forEach(i -> source.append(i).append('\n'));
        if (!includes.isEmpty()) {
            source.append('\n');
        }
        // Each helper once, in first-use order.
        for (var helper : helpers) {
            source.append(Helpers.source(helper, target.dialect())).append('\n');
        }
        var definition = header + emitted;
        source.append(definition);
        var declaration = header.substring(0, header.length() - "\n{\n".length()) + ";\n";
        return new Result(source.toString(), entry, List.copyOf(includes), List.copyOf(helpers), definition, declaration, java.util.Collections.unmodifiableList(new ArrayList<>(origins)));
    }

    // ---------------------------------------------------------------- statements

    /** {@code fallthrough}: labels whose position is reached by falling off the end of {@code body}. */
    private void statements(List<S.Stmt> body, Set<String> fallthrough, int depth) {
        for (int i = 0; i < body.size(); i++) {
            statement(body.get(i), i == body.size() - 1 ? fallthrough : Set.of(), depth);
        }
    }

    private void statement(S.Stmt stmt, Set<String> fallthrough, int depth) {
        var enclosing = current;
        current = stmt;
        try {
            statementLines(stmt, fallthrough, depth);
        } finally {
            current = enclosing;
        }
    }

    private void statementLines(S.Stmt stmt, Set<String> fallthrough, int depth) {
        switch (stmt) {
            case S.Assign(var target, var value, var loc) -> {
                var element = elementTypes.get(target);
                var text = element != null ? "(" + dataPointer(target, element) + ") (" + expr(value).text + ")" : expr(value).text;
                var declare = target.type() instanceof Type.TileType t && t.view() ? "auto " : "";
                line(depth, declare + names.get(target) + " = " + text + ";" + comment(loc));
            }
            case S.Effect(var op, var loc) when op.inst().opcode() instanceof Opcode.Intrinsic(var name) && name.equals(DEVICE_LAUNCH) -> deviceLaunch(op.inst(), op.operands(), loc, depth);
            case S.Effect(var op, var loc) when op.inst().opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tornado.printf") && !dialect.printf() ->
                // As on the Graal path: the target has no printf, so the call is left out.
                line(depth, "// Debug.printf(\"" + cEscape(op.inst().immediates().requireString("format")) + "\"): not supported on this target" + comment(loc));
            case S.Effect(var op, var loc) when op.inst().opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tornado.printf") -> {
                // Debug.printf: every thread prints, prefixed with its global id (as on the Graal path).
                var format = cEscape(op.inst().immediates().requireString("format"));
                var args = new StringBuilder();
                var globalId = Intrinsics.lookup("tornado.thread_id.global", target.dialect(), Type.I32).orElseThrow().template();
                for (int d = 0; d < 3; d++) {
                    args.append(", ").append(Intrinsics.fillImmediates(globalId, uk.ac.manchester.tornado.tc.ir.Immediates.of("dim", d)));
                }
                op.operands().forEach(o -> args.append(", ").append(expr(o).text));
                line(depth, "printf(\"tornado[%3d,%3d,%3d]> " + format + "\"" + args + ");" + comment(loc));
            }
            case S.Effect(var op, var loc) -> line(depth, effect(op) + ";" + comment(loc));
            case S.If(var c, var t, var e) -> ifStatement(c, t, e, fallthrough, depth);
            case S.Labelled(var label, var b) when wrapped.contains(label) -> {
                var inner = new HashSet<>(fallthrough);
                inner.add(label);
                var frame = new BlockFrame(inner, new ArrayList<>());
                frames.push(frame);
                line(depth, "do {");
                statements(b, inner, depth + 1);
                line(depth, "} while (false);");
                frames.pop();
                repeatFlagged(frame.flagged(), depth);
            }
            case S.Labelled(var label, var b) -> {
                var inner = new HashSet<>(fallthrough);
                inner.add(label);
                frames.push(new LabelFrame(label));
                statements(b, inner, depth);
                frames.pop();
                if (neededLabels.contains(label)) {
                    line(depth, label + ": ;");
                }
            }
            case S.Loop(var label, var b) -> loop(label, b, fallthrough, depth);
            case S.Break(var label) -> line(depth, jump(label, true));
            case S.Continue(var label) -> line(depth, jump(label, false));
            case S.Switch(var sel, var cases, var d) -> {
                var frame = new SwitchFrame(fallthrough, new ArrayList<>());
                frames.push(frame);
                line(depth, "switch (" + expr(sel).text + ") {");
                for (var c : cases) {
                    line(depth, "case " + c.key() + ":");
                    caseBody(c.body(), depth + 1);
                }
                line(depth, "default:");
                caseBody(d, depth + 1);
                line(depth, "}");
                frames.pop();
                repeatFlagged(frame.flagged(), depth);
            }
            case S.Return(var value) -> line(depth, value.map(v -> "return " + expr(v).text + ";").orElse("return;"));
            case S.Unreachable _u -> line(depth, dialect.unreachable());
        }
    }

    private void ifStatement(S.Expr c, List<S.Stmt> t, List<S.Stmt> e, Set<String> fallthrough, int depth) {
        ifStatement(c, t, e, fallthrough, depth, false);
    }

    /** {@code swapped}: the arms were already exchanged once (the rules below must not undo each other). */
    private void ifStatement(S.Expr c, List<S.Stmt> t, List<S.Stmt> e, Set<String> fallthrough, int depth, boolean swapped) {
        if (t.isEmpty() && e.isEmpty()) {
            return;
        }
        if (!swapped && !e.isEmpty() && prefersComplement(c)) {
            // javac branches on the inverse test; print the direct comparison with the arms swapped.
            ifStatement(complement(c).orElseThrow(), e, t, fallthrough, depth, true);
            return;
        }
        if (t.isEmpty()) {
            line(depth, "if (" + negate(c) + ") {");
            statements(e, fallthrough, depth + 1);
            line(depth, "}");
            return;
        }
        if (!e.isEmpty() && endsInJump(t)) {
            // No `else` after a branch that always leaves: the other arm follows at the same level.
            line(depth, "if (" + expr(c).text + ") {");
            statements(t, fallthrough, depth + 1);
            line(depth, "}");
            statements(e, fallthrough, depth);
            return;
        }
        if (!swapped && !e.isEmpty() && endsInJump(e) && !endsInJump(t) && complement(c).isPresent()) {
            ifStatement(complement(c).orElseThrow(), e, t, fallthrough, depth, true);
            return;
        }
        line(depth, "if (" + expr(c).text + ") {");
        statements(t, fallthrough, depth + 1);
        if (!e.isEmpty()) {
            line(depth, "} else {");
            statements(e, fallthrough, depth + 1);
        }
        line(depth, "}");
    }

    private void loop(String label, List<S.Stmt> body, Set<String> fallthrough, int depth) {
        // A jump to the loop's label (a continue of an outer loop) can only come from its body: the
        // label is written at the loop's start once the body shows it is needed.
        int start = out.length();
        String condition = null;
        var inner = body;
        if (body.size() == 1 && body.get(0) instanceof S.If(var c, var t, var e) && isPlainBreakOf(e, fallthrough)) {
            condition = expr(c).text;
            inner = t;
        } else if (body.size() == 1 && body.get(0) instanceof S.If(var c, var t, var e) && isPlainBreakOf(t, fallthrough)) {
            condition = negate(c);
            inner = e;
        }
        var latch = latch(label, inner);
        LoopFrame frame;
        if (latch != null) {
            // `{ block L { ... break L; ... } i = i + step; }` is `for (; c; i = i + step) { ... continue; ... }`.
            var block = (S.Labelled) inner.get(inner.size() - latch.size() - 1);
            frame = new LoopFrame(label, fallthrough, Set.of(block.label()), new ArrayList<>());
            frames.push(frame);
            var text = new ArrayList<String>();
            for (var a : latch) {
                text.add(assignment((S.Assign) a));
            }
            line(depth, "for (; " + (condition == null ? "" : condition) + "; " + String.join(", ", text) + ") {");
            statements(inner.subList(0, inner.size() - latch.size()), Set.of(), depth + 1);
        } else {
            frame = new LoopFrame(label, fallthrough, Set.of(), new ArrayList<>());
            frames.push(frame);
            line(depth, condition == null ? "for (;;) {" : "while (" + condition + ") {");
            statements(inner, Set.of(), depth + 1);
        }
        line(depth, "}");
        frames.pop();
        if (neededLabels.contains(label)) {
            out.insert(start, "  ".repeat(depth) + label + ":\n");
        }
        repeatFlagged(frame.flagged(), depth);
    }

    /**
     * The trailing assignments of a loop body that follow a labelled block, when they can be the
     * latch of a {@code for}: plain assignments only, and no {@code continue} of this loop in the body
     * (a C {@code continue} would now run them). Null when the body has no such shape.
     */
    private List<S.Stmt> latch(String loop, List<S.Stmt> body) {
        int k = body.size();
        while (k > 0 && body.get(k - 1) instanceof S.Assign(var target, var value, var loc) && !(target.type() instanceof Type.TileType)) {
            k--;
        }
        if (k == body.size() || k == 0 || !(body.get(k - 1) instanceof S.Labelled) || continues(loop, body)) {
            return null;
        }
        return body.subList(k, body.size());
    }

    private static boolean continues(String loop, List<S.Stmt> body) {
        for (var stmt : body) {
            boolean found = switch (stmt) {
                case S.Continue(var label) -> label.equals(loop);
                case S.If(var c, var t, var e) -> continues(loop, t) || continues(loop, e);
                case S.Loop(var label, var b) -> continues(loop, b);
                case S.Labelled(var label, var b) -> continues(loop, b);
                case S.Switch(var sel, var cases, var d) -> continues(loop, d) || cases.stream().anyMatch(c -> continues(loop, c.body()));
                default -> false;
            };
            if (found) {
                return true;
            }
        }
        return false;
    }

    private String assignment(S.Assign a) {
        var element = elementTypes.get(a.target());
        var text = element != null ? "(" + dataPointer(a.target(), element) + ") (" + expr(a.value()).text + ")" : expr(a.value()).text;
        return names.get(a.target()) + " = " + text;
    }

    /** A body that only leaves the loop being entered (its break targets are {@code fallthrough}). */
    private static boolean isPlainBreakOf(List<S.Stmt> body, Set<String> fallthrough) {
        return body.size() == 1 && body.get(0) instanceof S.Break(var label) && fallthrough.contains(label);
    }

    private static boolean endsInJump(List<S.Stmt> body) {
        return !body.isEmpty() && switch (body.get(body.size() - 1)) {
            case S.Break b -> true;
            case S.Continue c -> true;
            case S.Return r -> true;
            case S.Unreachable u -> true;
            default -> false;
        };
    }

    private void caseBody(List<S.Stmt> body, int depth) {
        statements(body, Set.of(), depth);
        var last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (!(last instanceof S.Break || last instanceof S.Continue || last instanceof S.Return || last instanceof S.Unreachable)) {
            line(depth, "break;");
        }
    }

    private String jump(String label, boolean isBreak) {
        var structured = structuredJump(label, isBreak);
        if (structured != null) {
            return structured;
        }
        if (dialect.gotos()) {
            neededLabels.add(label);
            return "goto " + label + ";";
        }
        var flagged = new FlagJump(++flagJumps, label, isBreak);
        return flagBreak(flagged) ? jumpFlag + " = " + flagged.id() + "; break;" : "/* wrapped on the next emission */";
    }

    /** The jump as a plain {@code break;} or {@code continue;}, or null. */
    private String structuredJump(String label, boolean isBreak) {
        if (isBreak) {
            for (var frame : frames) {
                switch (frame) {
                    case LoopFrame(var l, var targets, var latch, var f) -> {
                        if (targets.contains(label)) {
                            return "break;";
                        }
                    }
                    case SwitchFrame(var targets, var f) -> {
                        if (targets.contains(label)) {
                            return "break;";
                        }
                    }
                    case BlockFrame(var targets, var f) -> {
                        if (targets.contains(label)) {
                            return "break;";
                        }
                    }
                    case LabelFrame l -> {
                        continue;
                    }
                }
                break; // only the innermost loop or switch can be left with a plain break
            }
            // Leaving a block that ends just before the latch of a `for` is `continue` (a switch does not catch it).
            for (var frame : frames) {
                if (frame instanceof LoopFrame(var l, var targets, var latch, var f)) {
                    if (latch.contains(label)) {
                        return "continue;";
                    }
                    break;
                }
                if (frame instanceof BlockFrame) {
                    break;
                }
            }
        } else {
            for (var frame : frames) {
                if (frame instanceof LoopFrame(var l, var targets, var latch, var f)) {
                    if (l.equals(label) && latch.isEmpty()) {
                        return "continue;";
                    }
                    break;
                }
                if (frame instanceof BlockFrame) {
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Registers {@code jump} with the innermost loop, switch or wrapped block, which it leaves with
     * {@code break}. False when the jump's target lies inside that statement (or there is none): the
     * labelled block is then wrapped on the next emission.
     */
    private boolean flagBreak(FlagJump jump) {
        for (var frame : frames) {
            switch (frame) {
                case LabelFrame(var l) -> {
                    if (l.equals(jump.label())) {
                        toWrap.add(l);
                        return false;
                    }
                }
                case LoopFrame(var l, var t, var c, var flagged) -> {
                    flagged.add(jump);
                    return true;
                }
                case SwitchFrame(var t, var flagged) -> {
                    flagged.add(jump);
                    return true;
                }
                case BlockFrame(var t, var flagged) -> {
                    flagged.add(jump);
                    return true;
                }
            }
        }
        toWrap.add(jump.label());
        return false;
    }

    /** After a statement that flagged jumps left: each one continues from here. */
    private void repeatFlagged(List<FlagJump> flagged, int depth) {
        for (var jump : flagged) {
            var test = "if (" + jumpFlag + " == " + jump.id() + ") {";
            var structured = structuredJump(jump.label(), jump.isBreak());
            if (structured != null) {
                line(depth, test);
                line(depth + 1, jumpFlag + " = 0;");
                line(depth + 1, structured);
                line(depth, "}");
            } else if (flagBreak(jump)) {
                line(depth, test);
                line(depth + 1, "break;");
                line(depth, "}");
            }
        }
    }

    /** Places a label before each loop that a goto restarts; removes the other markers. */
    /** A launch from the device ({@code KernelContext.launch}): the dialect prints it, in a block of its own. */
    private void deviceLaunch(Inst launch, List<S.Expr> operands, Loc loc, int depth) {
        var mode = launch.immediates().requireString("mode");
        var child = launch.immediates().requireString("callee");
        var e = operands.stream().map(o -> expr(o).text).toList();
        var unsigned = operands.subList(0, 3).stream().map(o -> "(unsigned) " + wrap(expr(o), UNARY)).toList();
        var shortName = child.substring(child.lastIndexOf('.', child.lastIndexOf('.') - 1) + 1);
        // --threadInfo reports the child in the host's "Task info" format, named by the task.
        var label = cEscape(task + " -> " + shortName + " (device launch, " + mode.toUpperCase(java.util.Locale.ROOT) + ")");
        var site = new CDialect.Launch(child, mode, e.subList(0, 3), unsigned, e.subList(3, 6), e.subList(6, e.size()), label, shortName);
        line(depth, "{" + comment(loc));
        dialect.deviceLaunch(site, helpers).forEach(l -> line(depth + 1, l));
        line(depth, "}");
    }

    /** A Java string as the body of a C string literal (octal escapes: at most three digits, unlike greedy hex). */
    private static String cEscape(String text) {
        var sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\042"); // octal: at most three digits, unlike greedy \\x escapes
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\%03o", (int) c & 0xff));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String comment(Loc loc) {
        return lineComments && loc.isKnown() && loc.line() >= 0 ? "  // " + loc.file() + ":" + loc.line() : "";
    }

    private void line(int depth, String text) {
        out.append("  ".repeat(depth)).append(text).append('\n');
        origins.add(current);
    }

    // ---------------------------------------------------------------- expressions

    private String effect(S.Op op) {
        var inst = op.inst();
        var ops = op.operands();
        return switch (inst.opcode()) {
            case Opcode.Store _s -> deref(ops.get(0), ops.get(1).type()).text + " = " + expr(ops.get(1)).text;
            case Opcode.Intrinsic(var name) -> intrinsic(name, inst, ops).text;
            default -> expr(op).text;
        };
    }

    private R expr(S.Expr e) {
        return switch (e) {
            case S.VarRef(var v) -> new R(names.get(v), ATOM);
            case S.Const(var inst) -> constant(inst);
            case S.AllocaRef(var alloca) -> new R("(" + pointer(alloca.kind().space(), "unsigned char") + ") " + allocaNames.get(alloca), UNARY);
            case S.ElementAddress address -> expr(address.address());
            case S.Op(var inst, var ops) -> op(inst, ops);
        };
    }

    private R op(Inst inst, List<S.Expr> ops) {
        return switch (inst.opcode()) {
            case Opcode.Binary(var op) -> wrapsLikeJava(op, inst) ? wrapping(op, inst.type(), ops.get(0), ops.get(1)) : binary(op, inst.type(), ops.get(0), ops.get(1));
            case Opcode.Unary(var op) -> switch (op) {
                // -MIN_VALUE wraps to MIN_VALUE in Java; negate in the unsigned type (see wrapsLikeJava).
                case NEG -> !SIGNED_OVERFLOW_UNDEFINED && (inst.type().equals(Type.I32) || inst.type().equals(Type.I64))
                        ? new R("(" + typeName(inst.type()) + ") ((" + unsignedName(inst.type()) + ") 0 - (" + unsignedName(inst.type()) + ") " + wrap(expr(ops.get(0)), UNARY) + ")", UNARY)
                        : new R("-" + prefixOperand(expr(ops.get(0))), UNARY);
                case FNEG -> new R("-" + prefixOperand(expr(ops.get(0))), UNARY);
                case NOT -> new R((inst.type().equals(Type.I1) ? "!" : "~") + prefixOperand(expr(ops.get(0))), UNARY);
            };
            case Opcode.ICmp(var p) -> icmp(p, ops.get(0), ops.get(1));
            case Opcode.FCmp(var p) -> fcmp(p, ops.get(0), ops.get(1));
            case Opcode.Select _s -> new R(wrap(expr(ops.get(0)), LOR) + " ? " + wrap(expr(ops.get(1)), COND) + " : " + wrap(expr(ops.get(2)), COND), COND);
            case Opcode.Convert(var op) -> convert(op, ops.get(0), inst.type());
            case Opcode.PointerOffset _p -> {
                // Offsets are in bytes: a typed data pointer is converted back before adding.
                var base = ops.get(0) instanceof S.VarRef(var v) && elementTypes.containsKey(v) ? "(" + typeName(v.type()) + ") " + names.get(v) : wrap(expr(ops.get(0)), ADD);
                yield new R(base + " + " + wrap(expr(ops.get(1)), ADD + 1), ADD);
            }
            case Opcode.Load _l -> deref(ops.get(0), inst.type());
            case Opcode.Intrinsic(var name) -> intrinsic(name, inst, ops);
            case Opcode.ArrayLoad _a -> throw unsupported(inst, "Java-level arrays must be lowered by TornadoVM ABI lowering first");
            case Opcode.ArrayStore _a -> throw unsupported(inst, "Java-level arrays must be lowered by TornadoVM ABI lowering first");
            case Opcode.ArrayLength _a -> throw unsupported(inst, "Java-level arrays must be lowered by TornadoVM ABI lowering first");
            case Opcode.FieldLoad _f -> throw unsupported(inst, "Java-level fields must be lowered by TornadoVM ABI lowering first");
            case Opcode.FieldStore _f -> throw unsupported(inst, "Java-level fields must be lowered by TornadoVM ABI lowering first");
            case Opcode.Call(var callee) -> throw unsupported(inst, "calls must be inlined (device functions come after parity)");
            default -> throw unsupported(inst, "unexpected in an expression");
        };
    }

    /** The operand of a prefix operator: parenthesised when it starts with a sign, so `-(-x)` never prints as `--x`. */
    private static String prefixOperand(R operand) {
        var text = wrap(operand, UNARY);
        return text.startsWith("-") || text.startsWith("+") ? "(" + text + ")" : text;
    }

    private R binary(BinaryOp op, Type type, S.Expr a, S.Expr b) {
        return switch (op) {
            case ADD, FADD -> infix(a, "+", b, ADD);
            case SUB, FSUB -> infix(a, "-", b, ADD);
            case MUL, FMUL -> infix(a, "*", b, MUL);
            case DIV -> javaDivision(type, a, "/", b, type.equals(Type.I64) ? "java_ldiv" : "java_idiv");
            case REM -> javaDivision(type, a, "%", b, type.equals(Type.I64) ? "java_lrem" : "java_irem");
            case FDIV -> infix(a, "/", b, MUL);
            case FREM -> new R((type.equals(Type.F32) ? "fmodf(" : "fmod(") + expr(a).text + ", " + expr(b).text + ")", ATOM);
            case AND -> infix(a, "&", b, BAND);
            case OR -> infix(a, "|", b, BOR);
            case XOR -> infix(a, "^", b, BXOR);
            // Java shifts the bits of a negative value; C++ leaves that undefined, so shift it unsigned.
            case SHL -> {
                var unsigned = unsignedName(type);
                yield new R("(" + typeName(type) + ") ((" + unsigned + ") " + wrap(expr(a), UNARY) + " << " + shiftCount(b, type) + ")", UNARY);
            }
            case SHR -> new R(wrap(expr(a), SHIFT) + " >> " + shiftCount(b, type), SHIFT);
            case USHR -> {
                var unsigned = unsignedName(type);
                yield new R("(" + typeName(type) + ") ((" + unsigned + ") " + wrap(expr(a), UNARY) + " >> " + shiftCount(b, type) + ")", UNARY);
            }
        };
    }

    /**
     * {@code /} or {@code %} with Java's result for {@code MIN_VALUE} and {@code -1}, which C leaves
     * undefined: plain when the divisor is a constant other than -1, else through a helper. Narrower
     * types are promoted to {@code int} in C, where their minimum divided by -1 is defined.
     */
    private R javaDivision(Type type, S.Expr a, String op, S.Expr b, String helper) {
        boolean wide = type.equals(Type.I32) || type.equals(Type.I64);
        if (!wide || b instanceof S.Const(var c) && c.opcode() instanceof Opcode.ConstInt(var v) && v != -1) {
            return infix(a, op, b, MUL);
        }
        if (features.contains(CDialect.Feature.TILES)) {
            // A CUDA Tile kernel cannot call __device__ functions: the same expression, inline
            // (operands are side-effect free, so repeating them is safe).
            var x = wrap(expr(a), UNARY);
            var y = wrap(expr(b), UNARY);
            var zero = op.equals("%") ? "0" : "(" + typeName(type) + ") ((" + unsignedName(type) + ") 0 - (" + unsignedName(type) + ") " + x + ")";
            return new R("(" + y + " == -1 ? " + zero + " : " + x + " " + op + " " + y + ")", ATOM);
        }
        helpers.add(helper);
        return new R(helper + "(" + expr(a).text + ", " + expr(b).text + ")", ATOM);
    }

    /**
     * Java's int and long +, - and * wrap around; in C and OpenCL C signed overflow is undefined, and
     * the device compilers use that: {@code 0 < x + x} became {@code 0 < x} for x = Long.MIN_VALUE.
     * So they compute in the unsigned type, which wraps, and convert back (two's complement on every
     * target). Arithmetic TC generated and marked {@code nsw} (grid strides, address offsets) cannot
     * overflow and stays plain, which keeps the driver compilers' loop and address optimisations.
     */
    /**
     * {@code -Dtornado.tc.signedOverflowUndefined=true} prints int and long arithmetic as plain C (as
     * Graal did): faster where the driver compiler exploits it, wrong where Java code relies on wrapping.
     */
    private static final boolean SIGNED_OVERFLOW_UNDEFINED = Boolean.getBoolean("tornado.tc.signedOverflowUndefined");

    private static boolean wrapsLikeJava(BinaryOp op, Inst inst) {
        return !SIGNED_OVERFLOW_UNDEFINED && (op == BinaryOp.ADD || op == BinaryOp.SUB || op == BinaryOp.MUL) && (inst.type().equals(Type.I32) || inst.type().equals(Type.I64)) && !inst.hasAttribute("nsw");
    }

    private R wrapping(BinaryOp op, Type type, S.Expr a, S.Expr b) {
        var unsigned = "(" + unsignedName(type) + ") ";
        var symbol = switch (op) {
            case ADD -> " + ";
            case SUB -> " - ";
            default -> " * ";
        };
        return new R("(" + typeName(type) + ") (" + unsigned + wrap(expr(a), UNARY) + symbol + unsigned + wrap(expr(b), UNARY) + ")", UNARY);
    }

    /** Java masks the shift count to the operand width. */
    private String shiftCount(S.Expr count, Type type) {
        int mask = type.equals(Type.I64) ? 63 : 31;
        if (count instanceof S.Const(var c) && c.opcode() instanceof Opcode.ConstInt(var v)) {
            return Long.toString(v & mask);
        }
        return "(" + wrap(expr(count), BAND) + " & " + mask + ")";
    }

    private R infix(S.Expr a, String op, S.Expr b, int prec) {
        return new R(wrap(expr(a), prec) + " " + op + " " + wrap(expr(b), prec + 1), prec);
    }

    private R icmp(IntPredicate p, S.Expr a, S.Expr b) {
        var symbol = switch (p) {
            case EQ -> "==";
            case NE -> "!=";
            case SLT, ULT -> "<";
            case SLE, ULE -> "<=";
            case SGT, UGT -> ">";
            case SGE, UGE -> ">=";
        };
        var prec = p == IntPredicate.EQ || p == IntPredicate.NE ? EQ : REL;
        var unsigned = switch (p) {
            case ULT, ULE, UGT, UGE -> true;
            default -> false;
        };
        if (unsigned) {
            var u = "(" + unsignedName(a.type().equals(Type.I64) ? Type.I64 : Type.I32) + ") ";
            return new R(u + wrap(expr(a), UNARY) + " " + symbol + " " + u + wrap(expr(b), UNARY), prec);
        }
        return new R(wrap(expr(a), prec) + " " + symbol + " " + wrap(expr(b), prec + 1), prec);
    }

    private R fcmp(FloatPredicate p, S.Expr a, S.Expr b) {
        var x = wrap(expr(a), REL + 1);
        var y = wrap(expr(b), REL + 1);
        if (dialect.fastMath()) {
            var fast = fastMathFcmp(p, x, y, expr(a).text, expr(b).text);
            if (fast != null) {
                return fast;
            }
        }
        return switch (p) {
            case OEQ -> new R(x + " == " + y, EQ);
            case UNE -> new R(x + " != " + y, EQ);
            case OLT -> new R(x + " < " + y, REL);
            case OLE -> new R(x + " <= " + y, REL);
            case OGT -> new R(x + " > " + y, REL);
            case OGE -> new R(x + " >= " + y, REL);
            case ONE -> new R(x + " < " + y + " || " + x + " > " + y, LOR);
            case UEQ -> new R("!(" + x + " < " + y + " || " + x + " > " + y + ")", UNARY);
            case ULT -> new R("!(" + x + " >= " + y + ")", UNARY);
            case ULE -> new R("!(" + x + " > " + y + ")", UNARY);
            case UGT -> new R("!(" + x + " <= " + y + ")", UNARY);
            case UGE -> new R("!(" + x + " < " + y + ")", UNARY);
            case ORD -> new R(x + " == " + x + " && " + y + " == " + y, LAND);
            case UNO -> new R(x + " != " + x + " || " + y + " != " + y, LOR);
        };
    }

    /**
     * The NaN-sensitive predicates under fast math, with {@code isnan} (which fast math keeps): a
     * value compared with itself, ordered/unordered tests, and the unordered forms. Null: print as usual.
     */
    private static R fastMathFcmp(FloatPredicate p, String x, String y, String a, String b) {
        var nanX = "isnan(" + a + ")";
        var nanEither = a.equals(b) ? nanX : nanX + " || isnan(" + b + ")";
        return switch (p) {
            case OEQ, OLE, OGE -> a.equals(b) ? new R("!" + nanX, UNARY) : null;
            case UNE, ULT, UGT -> a.equals(b) ? new R(nanX, ATOM) : new R(nanEither + " || " + x + (p == FloatPredicate.UNE ? " != " : p == FloatPredicate.ULT ? " < " : " > ") + y, LOR);
            case ORD -> new R("!(" + nanEither + ")", UNARY);
            case UNO -> new R(nanEither, a.equals(b) ? ATOM : LOR);
            case ULE -> new R(nanEither + " || " + x + " <= " + y, LOR);
            case UGE -> new R(nanEither + " || " + x + " >= " + y, LOR);
            case UEQ -> new R(nanEither + " || " + x + " == " + y, LOR);
            default -> null;
        };
    }

    private R convert(Opcode.ConvertOp op, S.Expr value, Type to) {
        var from = value.type();
        var v = expr(value);
        return switch (op) {
            case SEXT, TRUNC, I2F, FEXT, FTRUNC -> cast(from, to, v);
            case ZEXT -> from.equals(Type.I1) ? cast(from, to, v) : cast(from, to, new R("(" + unsignedName(from) + ") " + wrap(v, UNARY), UNARY));
            case F2I_SAT -> {
                var converted = dialect.saturatingToInt(from, to, features) + "(" + v.text + ")";
                // Java converts NaN to 0. The CUDA and OpenCL saturating conversions give MIN_VALUE for
                // NaN except from float to int (measured: __float2ll_rz, __double2int_rz and NVIDIA's
                // convert_long_sat_rtz), so NaN is tested first; Metal's helpers already do.
                if (target.dialect() == TargetCaps.Dialect.METAL || from.equals(Type.F32) && to.equals(Type.I32)) {
                    yield new R(converted, ATOM);
                }
                var x = wrap(v, UNARY);
                // On OpenCL the NaN test reads the bits: -cl-fast-relaxed-math (TornadoVM's default) folds x != x.
                var nan = target.dialect() == TargetCaps.Dialect.OPENCL
                        ? (from.equals(Type.F64) ? "(as_ulong(" + v.text + ") & 0x7fffffffffffffffUL) > 0x7ff0000000000000UL" : "(as_uint(" + v.text + ") & 0x7fffffffu) > 0x7f800000u")
                        : x + " != " + x;
                yield new R("(" + nan + " ? 0 : " + converted + ")", ATOM);
            }
            // A narrow constant prints as an int literal: give it its own width (Metal's as_type checks sizes).
            case BITCAST -> new R(dialect.bitcast(from, to, features) + "(" + (value instanceof S.Const && from instanceof Type.IntType(var bits) && bits < 32
                    ? "(" + typeName(from) + ") " + wrap(v, UNARY) : v.text) + ")", ATOM);
        };
    }

    private R cast(Type from, Type to, R value) {
        var special = dialect.convertTo(from, to, value.text, features);
        if (special.isPresent()) {
            return new R(special.get(), ATOM);
        }
        return new R("(" + typeName(to) + ") " + wrap(value, UNARY), UNARY);
    }

    private R constant(Inst inst) {
        var type = inst.type();
        return switch (inst.opcode()) {
            case Opcode.ConstInt(var value) -> {
                if (type.equals(Type.I1)) {
                    yield new R(value != 0 ? "true" : "false", ATOM);
                }
                if (type.equals(Type.I64)) {
                    var ll = dialect.longSuffix();
                    yield value == Long.MIN_VALUE ? new R("(-9223372036854775807" + ll + " - 1)", ATOM) : new R(value + ll, value < 0 ? UNARY : ATOM);
                }
                if (value == Integer.MIN_VALUE && type.equals(Type.I32)) {
                    yield new R("(-2147483647 - 1)", ATOM);
                }
                yield new R(Long.toString(value), value < 0 ? UNARY : ATOM);
            }
            case Opcode.ConstFloat(var value) -> floatConstant(value, type);
            default -> throw unsupported(inst, "not a constant");
        };
    }

    private R floatConstant(double value, Type type) {
        boolean single = !type.equals(Type.F64);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            // NaN and infinities from their bits (C has no portable literal for them).
            var bits = single ? (Double.isNaN(value) ? "0x7fc00000" : value > 0 ? "0x7f800000" : "0xff800000")
                    : (Double.isNaN(value) ? "0x7ff8000000000000" : value > 0 ? "0x7ff0000000000000" : "0xfff0000000000000") + dialect.longSuffix();
            var fn = single ? dialect.bitcast(Type.I32, Type.F32, features) : dialect.bitcast(Type.I64, Type.F64, features);
            return new R(fn + "(" + bits + ")", ATOM);
        }
        var text = single ? Float.toString((float) value) + "f" : Double.toString(value);
        return dialect.convertTo(single ? Type.F32 : Type.F64, type, text, features).map(c -> new R(c, ATOM)).orElseGet(() -> new R(text, text.startsWith("-") ? UNARY : ATOM));
    }

    private R intrinsic(String name, Inst inst, List<S.Expr> ops) {
        if (name.startsWith("tile.")) {
            // Tile values are always named variables; the result is a whole statement's expression.
            return new R(dialect.tileExpression(name, inst, ops.stream().map(o -> wrap(expr(o), UNARY)).toList()), COND);
        }
        if (name.equals("tornado.vector.make")) {
            // A vector value from its lanes (CUDA: make_float4(x, y, z, w)).
            return new R(dialect.vector(typeName(inst.type()), ops.stream().map(o -> expr(o).text).toList()), ATOM);
        }
        if (name.equals("tornado.vector.insert") && Intrinsics.lookup(name, target.dialect(), inst.type()).isEmpty()
                && inst.type() instanceof Type.VectorType(var element, var length)) {
            // No insert spelling (OpenCL C has no overloaded helper): the vector rebuilt from its lanes.
            int replaced = inst.immediates().requireInt("lane");
            var laneTemplate = Intrinsics.lookup("tornado.vector.lane", target.dialect(), element).orElseThrow().template();
            var base = wrap(expr(ops.get(0)), ATOM);
            var lanes = new ArrayList<String>();
            for (int i = 0; i < length; i++) {
                lanes.add(i == replaced ? expr(ops.get(1)).text : Intrinsics.fillImmediates(laneTemplate, uk.ac.manchester.tornado.tc.ir.Immediates.of("lane", i)).replace("$0", base));
            }
            return new R(dialect.vector(typeName(inst.type()), lanes), ATOM);
        }
        // A void intrinsic (an atomic update) is typed by its value operand, the last one.
        var type = inst.producesValue() ? inst.type() : (ops.isEmpty() ? Type.VOID : ops.get(ops.size() - 1).type());
        var spelling = Intrinsics.lookup(name, target.dialect(), type).orElseThrow(() -> new EmitException("TC-E4002", "no " + target.dialect() + " spelling for intrinsic " + name + " on " + type.text()));
        spelling.helper().map(h -> Intrinsics.fillImmediates(h, inst.immediates())).ifPresent(helpers::add);
        var template = Intrinsics.fillImmediates(spelling.template(), inst.immediates());
        if (template.contains("$&0")) {
            template = template.replace("$&0", addressOf(ops.get(0), type));
        }
        var operands = ops.stream().map(this::expr).toList();
        var text = Intrinsics.expand(template, operands.stream().map(R::text).toList(), i -> operands.get(i).prec >= UNARY);
        return new R(text, Intrinsics.isSingleCall(text) ? ATOM : COND);
    }

    /**
     * A memory access of {@code type} through {@code pointer}: an element address the structurizer
     * recognised prints as {@code a_data[i]} / {@code scratch[i]}, a typed data pointer itself as
     * {@code a_data[0]}; anything else as a cast and dereference.
     */
    private R deref(S.Expr pointer, Type type) {
        var element = element(pointer, type);
        return element != null ? new R(element, ATOM) : new R("*((" + pointerTo(pointer, type) + ") " + wrap(expr(pointer), UNARY) + ")", UNARY);
    }

    /** The address an atomic or helper takes: {@code &a_data[i]}, or a typed cast of the pointer. */
    private String addressOf(S.Expr pointer, Type type) {
        var element = element(pointer, type);
        return element != null ? "&" + element : "((" + pointerTo(pointer, type) + ") " + wrap(expr(pointer), UNARY) + ")";
    }

    /** {@code name[index]} when {@code pointer} addresses an element of {@code type} of a named array, else null. */
    private String element(S.Expr pointer, Type type) {
        if (pointer instanceof S.ElementAddress(var address, var base, var index, var element) && element.equals(type)) {
            return arrayName(base, type) + "[" + expr(index).text + "]";
        }
        var array = arrayName(pointer, type);
        return array != null ? array + "[0]" : null;
    }

    /** The C name of a typed data pointer or local array whose elements have {@code type}, else null. */
    private String arrayName(S.Expr base, Type type) {
        if (base instanceof S.VarRef(var v) && type.equals(elementTypes.get(v))) {
            return names.get(v);
        }
        if (base instanceof S.AllocaRef(var alloca) && alloca.kind().element().equals(type)) {
            return allocaNames.get(alloca);
        }
        return null;
    }


    private static String wrap(R r, int min) {
        return r.prec >= min ? r.text : "(" + r.text + ")";
    }

    /** The negation of a condition: the complement predicate for comparisons, otherwise {@code !(…)}. */
    private String negate(S.Expr condition) {
        return complement(condition).map(c -> expr(c).text).orElseGet(() -> "!" + wrap(expr(condition), UNARY));
    }

    /**
     * The exact logical complement of a comparison: integer predicates invert directly; a floating-point
     * predicate's complement swaps ordered and unordered ({@code !(x OGT y) == x ULE y}), so NaN
     * behaviour is preserved.
     */
    private static java.util.Optional<S.Expr> complement(S.Expr condition) {
        if (!(condition instanceof S.Op(var inst, var ops))) {
            return java.util.Optional.empty();
        }
        Opcode complemented = switch (inst.opcode()) {
            case Opcode.ICmp(var p) -> new Opcode.ICmp(switch (p) {
                case EQ -> IntPredicate.NE;
                case NE -> IntPredicate.EQ;
                case SLT -> IntPredicate.SGE;
                case SGE -> IntPredicate.SLT;
                case SGT -> IntPredicate.SLE;
                case SLE -> IntPredicate.SGT;
                case ULT -> IntPredicate.UGE;
                case UGE -> IntPredicate.ULT;
                case UGT -> IntPredicate.ULE;
                case ULE -> IntPredicate.UGT;
            });
            case Opcode.FCmp(var p) -> new Opcode.FCmp(switch (p) {
                case OEQ -> FloatPredicate.UNE;
                case UNE -> FloatPredicate.OEQ;
                case OGT -> FloatPredicate.ULE;
                case ULE -> FloatPredicate.OGT;
                case OGE -> FloatPredicate.ULT;
                case ULT -> FloatPredicate.OGE;
                case OLT -> FloatPredicate.UGE;
                case UGE -> FloatPredicate.OLT;
                case OLE -> FloatPredicate.UGT;
                case UGT -> FloatPredicate.OLE;
                case ONE -> FloatPredicate.UEQ;
                case UEQ -> FloatPredicate.ONE;
                case ORD -> FloatPredicate.UNO;
                case UNO -> FloatPredicate.ORD;
            });
            default -> null;
        };
        return complemented == null ? java.util.Optional.empty() : java.util.Optional.of(new S.Op(new Inst(complemented, Type.I1), ops));
    }

    /** Unordered float tests print as {@code !(…)}; their ordered complement reads as plain C. */
    private static boolean prefersComplement(S.Expr condition) {
        return condition instanceof S.Op(var inst, var ops) && inst.opcode() instanceof Opcode.FCmp(var p)
                && (p == FloatPredicate.ULT || p == FloatPredicate.ULE || p == FloatPredicate.UGT || p == FloatPredicate.UGE || p == FloatPredicate.UEQ);
    }

    // ---------------------------------------------------------------- types and names

    private String declaration(Type type, String name) {
        return type instanceof Type.PointerType ? typeName(type) + name : typeName(type) + " " + name;
    }

    /** The C core's own types; the dialect spells the rest (pointers, half, bfloat16, vectors). */
    private String typeName(Type type) {
        return switch (type) {
            case IntType(var bits) -> dialect.integerName(bits);
            case FloatType f when f.kind() == FloatType.Kind.F32 -> "float";
            case FloatType f when f.kind() == FloatType.Kind.F64 -> "double";
            case Type.PointerType(var space) -> pointer(space, "unsigned char");
            case Type.TileType t when !t.view() -> dialect.tileType(t);
            case Type.VoidType v -> "void";
            default -> dialect.typeName(type, features).orElseThrow(() -> new EmitException("TC-E4001", "type " + type.text() + " is not supported on " + target.dialect() + " yet"));
        };
    }

    /** The typed data pointer of {@code v}, whose elements are {@code element}: {@code float *}. */
    private String dataPointer(S.Var v, Type element) {
        return pointer(space(v.type()), typeName(element));
    }

    /** A pointer to {@code element} in the address space of {@code pointer}. */
    private String pointerTo(S.Expr pointer, Type element) {
        return pointer(space(pointer.type()), typeName(element));
    }

    /** A pointer type: the dialect's pointee and a star, {@code float *}. */
    private String pointer(AddressSpace space, String element) {
        return dialect.pointee(space, element) + " *";
    }

    /**
     * A local's declaration split into the part a group shares and its own declarator: {@code float}
     * and {@code *a_data} for a data pointer, {@code int} and {@code i} for a scalar.
     */
    private record Declarator(String base, boolean pointer, String name) {
    }

    private Declarator declarator(S.Var v) {
        var name = names.get(v);
        var element = elementTypes.get(v);
        if (element != null) {
            return new Declarator(dialect.pointee(space(v.type()), typeName(element)), true, name);
        }
        if (v.type() instanceof Type.PointerType(var space)) {
            return new Declarator(dialect.pointee(space, "unsigned char"), true, name);
        }
        return new Declarator(typeName(v.type()), false, name);
    }

    private static AddressSpace space(Type pointer) {
        return pointer instanceof Type.PointerType(var space) ? space : AddressSpace.GLOBAL;
    }

    /** The element types of a CUDA Tile kernel's values: their headers follow the tile prelude. */
    private void tileFeatures(Function f) {
        features.add(CDialect.Feature.TILES);
        f.blocks().stream().flatMap(b -> b.insts().stream()).forEach(i -> {
            if (i.type() instanceof Type.TileType t) {
                switch (t.dtype()) {
                    case "F16" -> features.add(CDialect.Feature.HALF);
                    case "BF16" -> features.add(CDialect.Feature.BFLOAT16);
                    case "FP8_E4M3", "FP8_E5M2" -> features.add(CDialect.Feature.FP8);
                    default -> {
                    }
                }
            }
        });
    }

    private String unsignedName(Type type) {
        return dialect.unsignedName(type instanceof IntType(var bits) ? bits : 32);
    }

    private String cIdentifier(String name, Set<String> used) {
        return Naming.of(target.dialect()).identifier(name, used);
    }

    private static EmitException unsupported(Inst inst, String why) {
        return new EmitException("TC-E4001", "'" + inst.opcode().mnemonic() + "': " + why);
    }
}
