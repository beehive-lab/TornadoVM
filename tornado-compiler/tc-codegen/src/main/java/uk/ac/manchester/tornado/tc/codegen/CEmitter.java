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
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;
import uk.ac.manchester.tornado.tc.lower.S;

/**
 * Prints TC-S (post-ABI) as C-family source for a target dialect (path A of the architecture).
 *
 * <p>Readability rules: expressions use C precedence instead of full parenthesisation; a jump that C
 * can express structurally becomes {@code break;} or {@code continue;}, otherwise {@code goto}; a
 * loop whose body is a single test-and-exit becomes {@code while}. All locals are declared at the top
 * of the function, so a {@code goto} never skips an initialisation (required by CUDA C++).</p>
 *
 * <p>Java semantics: shift counts are masked, {@code >>>} shifts an unsigned value, float-to-integer
 * conversions saturate (CUDA {@code __float2int_rz} family), and {@code Math.min/max} on floating
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
    public record Result(String source, String entryName, List<String> includes, List<String> helpers, String definition, String declaration) {
    }

    /** Prefix of a device-side kernel launch ({@code KernelContext.launch}): {@code <prefix><mode>.<Owner>.<method>}. */
    public static final String DEVICE_LAUNCH = "tornado.device_launch.";

    /** The entry point of a kernel launched from the device ({@code Owner.method}): {@code Owner_method_dp}. */
    public static String deviceKernelEntry(String ownerDotMethod) {
        return (ownerDotMethod + "_dp").replace('.', '_').replace('$', '_');
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

    private record LoopFrame(String label, Set<String> breakTargets) implements Frame {
    }

    private record SwitchFrame(Set<String> breakTargets) implements Frame {
    }

    private final TargetCaps target;
    private final boolean lineComments;
    private final StringBuilder out = new StringBuilder();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Set<String> neededLabels = new HashSet<>();
    private final Set<String> helpers = new LinkedHashSet<>();
    private final Map<S.Var, String> names = new IdentityHashMap<>();
    private final Map<S.Alloca, String> allocaNames = new IdentityHashMap<>();
    private Map<S.Var, Type> elementTypes = Map.of();
    private boolean usesHalf;
    private boolean usesBFloat16;

    private CEmitter(TargetCaps target, boolean lineComments) {
        if (target.dialect() != Dialect.CUDA) {
            throw new EmitException("TC-E4001", "dialect " + target.dialect() + " is not implemented yet (CUDA first, then OpenCL and Metal)");
        }
        this.target = target;
        this.lineComments = lineComments;
    }

    public static Result emit(S.Kernel kernel, TargetCaps target) {
        return emit(kernel, target, true);
    }

    public static Result emit(S.Kernel kernel, TargetCaps target, boolean lineComments) {
        return new CEmitter(target, lineComments).kernel(kernel);
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

        var f = k.source();
        var entry = entryName(f);
        var header = new StringBuilder();
        var tiles = f.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof Opcode.Intrinsic(var n) && n.startsWith("tile."));
        if (f.kind() == Function.Kind.KERNEL) {
            // A TileContext kernel is CUDA Tile C++, compiled by nvcc (the runtime looks for __tile_global__).
            header.append(tiles ? "extern \"C\" __tile_global__ void " : "extern \"C\" __global__ void ").append(entry).append("(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics");
            for (var p : k.params()) {
                header.append(", ").append(declaration(p.type(), names.get(p)));
            }
        } else {
            header.append("__device__ static ").append(typeName(f.returnType())).append(' ').append(entry).append('(');
            header.append(k.params().stream().map(p -> declaration(p.type(), names.get(p))).collect(Collectors.joining(", ")));
        }
        header.append(")\n{\n");

        for (var alloca : k.allocas()) {
            var a = alloca.kind();
            // Java zero-fills `new T[n]` (private arrays); KernelContext local arrays start undefined, as on Graal.
            var init = a.space() == AddressSpace.PRIVATE ? " = {}" : "";
            line(1, (a.space() == AddressSpace.SHARED ? "__shared__ " : "") + typeName(a.element()) + " " + allocaNames.get(alloca) + "[" + a.count() + "]" + init + ";");
        }
        // Locals of one type share a declaration (`float x, v;`, `float *a_data, *b_data;`), in first-use order.
        var groups = new java.util.LinkedHashMap<String, List<String>>();
        for (var v : k.locals()) {
            if (v.type() instanceof Type.TileType t && t.view()) {
                continue; // a view's C++ type cannot be named: `auto` at its definition
            }
            var element = elementTypes.get(v);
            var name = names.get(v);
            var full = element != null ? typeName(element) + " *" + name : declaration(v.type(), name);
            var prefix = full.substring(0, full.length() - name.length());
            var base = prefix.replaceAll("[\\s*]+$", "");
            var stars = prefix.substring(base.length()).replace(" ", "");
            // Pointers and scalars of one base type get separate lines: `float x, v;` and `float *a_data, *b_data;`.
            groups.computeIfAbsent(base + (stars.isEmpty() ? "" : " *"), x -> new ArrayList<>()).add(stars + name);
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
        statements(body, Set.of(), 1);
        out.append("}\n");

        var emitted = resolveLoopLabels(out.toString());
        var source = new StringBuilder();
        var includes = new ArrayList<String>();
        var text = header + emitted;
        var fp8 = tiles && text.contains("__nv_fp8");
        if (tiles) {
            includes.add(TileSpellings.PRELUDE.strip());
        }
        // Tile element types are spelled by TileSpellings: their headers follow cuda_tile.h (fp8 builds on fp16).
        if (usesHalf || tiles && text.contains("__half") || fp8) {
            includes.add("#include <cuda_fp16.h>");
        }
        if (!usesBFloat16 && tiles && text.contains("__nv_bfloat16")) {
            includes.add("#include <cuda_bf16.h>");
        }
        if (fp8) {
            includes.add("#include <cuda_fp8.h>");
        }
        if (usesBFloat16) {
            includes.add("#include <cuda_bf16.h>");
        }
        includes.forEach(i -> source.append(i).append('\n'));
        if (!includes.isEmpty()) {
            source.append('\n');
        }
        // Dependencies first (a block reduction calls java_fmaxf), each helper once.
        var ordered = new LinkedHashSet<String>();
        for (var helper : helpers) {
            ordered.addAll(Helpers.dependencies(helper));
            ordered.add(helper);
        }
        for (var helper : ordered) {
            source.append(Helpers.source(helper, target.dialect())).append('\n');
        }
        var definition = header + emitted;
        source.append(definition);
        var declaration = header.substring(0, header.length() - "\n{\n".length()) + ";\n";
        return new Result(source.toString(), entry, List.copyOf(includes), List.copyOf(ordered), definition, declaration);
    }

    // ---------------------------------------------------------------- statements

    /** {@code fallthrough}: labels whose position is reached by falling off the end of {@code body}. */
    private void statements(List<S.Stmt> body, Set<String> fallthrough, int depth) {
        for (int i = 0; i < body.size(); i++) {
            statement(body.get(i), i == body.size() - 1 ? fallthrough : Set.of(), depth);
        }
    }

    private void statement(S.Stmt stmt, Set<String> fallthrough, int depth) {
        switch (stmt) {
            case S.Assign(var target, var value, var loc) -> {
                var element = elementTypes.get(target);
                var text = element != null ? "(" + typeName(element) + " *) (" + expr(value).text + ")" : expr(value).text;
                var declare = target.type() instanceof Type.TileType t && t.view() ? "auto " : "";
                line(depth, declare + names.get(target) + " = " + text + ";" + comment(loc));
            }
            case S.Effect(var op, var loc) when op.inst().opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(DEVICE_LAUNCH) -> deviceLaunch(name, op.operands(), loc, depth);
            case S.Effect(var op, var loc) when op.inst().opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tornado.printf") -> {
                // Debug.printf: every thread prints, prefixed with its global id (as on the Graal path).
                var format = cEscape(op.inst().immediates().requireString("format"));
                var args = new StringBuilder();
                for (var d : List.of("x", "y", "z")) {
                    args.append(", (int) (blockIdx.").append(d).append(" * blockDim.").append(d).append(" + threadIdx.").append(d).append(")");
                }
                op.operands().forEach(o -> args.append(", ").append(expr(o).text));
                line(depth, "printf(\"tornado[%3d,%3d,%3d]> " + format + "\"" + args + ");" + comment(loc));
            }
            case S.Effect(var op, var loc) -> line(depth, effect(op) + ";" + comment(loc));
            case S.If(var c, var t, var e) -> ifStatement(c, t, e, fallthrough, depth);
            case S.Labelled(var label, var b) -> {
                var inner = new HashSet<>(fallthrough);
                inner.add(label);
                statements(b, inner, depth);
                if (neededLabels.contains(label)) {
                    line(depth, label + ": ;");
                }
            }
            case S.Loop(var label, var b) -> loop(label, b, fallthrough, depth);
            case S.Break(var label) -> line(depth, jump(label, true));
            case S.Continue(var label) -> line(depth, jump(label, false));
            case S.Switch(var sel, var cases, var d) -> {
                frames.push(new SwitchFrame(fallthrough));
                line(depth, "switch (" + expr(sel).text + ") {");
                for (var c : cases) {
                    line(depth, "case " + c.key() + ":");
                    caseBody(c.body(), depth + 1);
                }
                line(depth, "default:");
                caseBody(d, depth + 1);
                line(depth, "}");
                frames.pop();
            }
            case S.Return(var value) -> line(depth, value.map(v -> "return " + expr(v).text + ";").orElse("return;"));
            case S.Unreachable _u -> line(depth, "__builtin_unreachable();");
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
        frames.push(new LoopFrame(label, fallthrough));
        line(depth, "@@loop:" + label);
        if (body.size() == 1 && body.get(0) instanceof S.If(var c, var t, var e) && isPlainBreak(e)) {
            line(depth, "while (" + expr(c).text + ") {");
            statements(t, Set.of(), depth + 1);
        } else if (body.size() == 1 && body.get(0) instanceof S.If(var c, var t, var e) && isPlainBreak(t)) {
            line(depth, "while (" + negate(c) + ") {");
            statements(e, Set.of(), depth + 1);
        } else {
            line(depth, "for (;;) {");
            statements(body, Set.of(), depth + 1);
        }
        line(depth, "}");
        frames.pop();
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

    private boolean isPlainBreak(List<S.Stmt> body) {
        return body.size() == 1 && body.get(0) instanceof S.Break(var label) && frames.peek() instanceof LoopFrame(var l, var targets) && targets.contains(label);
    }

    private void caseBody(List<S.Stmt> body, int depth) {
        statements(body, Set.of(), depth);
        var last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (!(last instanceof S.Break || last instanceof S.Continue || last instanceof S.Return || last instanceof S.Unreachable)) {
            line(depth, "break;");
        }
    }

    private String jump(String label, boolean isBreak) {
        if (isBreak) {
            for (var frame : frames) {
                switch (frame) {
                    case LoopFrame(var l, var targets) -> {
                        if (targets.contains(label)) {
                            return "break;";
                        }
                    }
                    case SwitchFrame(var targets) -> {
                        if (targets.contains(label)) {
                            return "break;";
                        }
                    }
                }
                break; // only the innermost loop or switch can be left with a plain break
            }
        } else {
            for (var frame : frames) {
                if (frame instanceof LoopFrame(var l, var targets)) {
                    if (l.equals(label)) {
                        return "continue;";
                    }
                    break;
                }
            }
        }
        neededLabels.add(label);
        return "goto " + label + ";";
    }

    /** Places a label before each loop that a goto restarts; removes the other markers. */
    private String resolveLoopLabels(String body) {
        var sb = new StringBuilder();
        for (var l : body.split("\n", -1)) {
            var marker = l.indexOf("@@loop:");
            if (marker >= 0) {
                var label = l.substring(marker + "@@loop:".length()).trim();
                if (neededLabels.contains(label)) {
                    sb.append(l, 0, marker).append(label).append(":\n");
                }
                continue;
            }
            sb.append(l).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }

    /**
     * A launch from the device: grid = ceil(global / local) per dimension, the child gets the four
     * ABI regions of its parent and the launch arguments; a failed launch is reported (as on the
     * host, the child then does not run). TAIL and FIRE_AND_FORGET use the CUDA named streams.
     */
    private void deviceLaunch(String name, List<S.Expr> operands, Loc loc, int depth) {
        var rest = name.substring(DEVICE_LAUNCH.length());
        var mode = rest.substring(0, rest.indexOf('.'));
        var child = rest.substring(rest.indexOf('.') + 1);
        var e = operands.stream().map(o -> expr(o).text).toList();
        var stream = switch (mode) {
            case "tail" -> ", 0, cudaStreamTailLaunch";
            case "fire_and_forget" -> ", 0, cudaStreamFireAndForget";
            default -> "";
        };
        var args = new StringBuilder("_kernel_context, _constant_region, _local_region, _atomics");
        e.subList(6, e.size()).forEach(a -> args.append(", ").append(a));
        var shortName = child.substring(child.lastIndexOf('.', child.lastIndexOf('.') - 1) + 1);
        helpers.add("tc_device_launch_check");
        line(depth, "{" + comment(loc));
        line(depth + 1, "dim3 _dp_block(" + e.get(3) + ", " + e.get(4) + ", " + e.get(5) + ");");
        var grid = new ArrayList<String>();
        for (int d = 0; d < 3; d++) {
            var c = "xyz".charAt(d);
            // Whole blocks covering the global size; a global size of 1 is one block.
            grid.add(e.get(d).equals("1") ? "1" : "((unsigned) " + wrap(expr(operands.get(d)), UNARY) + " + _dp_block." + c + " - 1) / _dp_block." + c);
        }
        line(depth + 1, "dim3 _dp_grid(" + String.join(", ", grid) + ");");
        line(depth + 1, deviceKernelEntry(child) + "<<<_dp_grid, _dp_block" + stream + ">>>(" + args + ");");
        line(depth + 1, "tc_device_launch_check(_kernel_context, \"" + shortName + "\", _dp_grid, _dp_block);");
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
            case S.AllocaRef(var alloca) -> new R("(unsigned char *) " + allocaNames.get(alloca), UNARY);
            case S.Op(var inst, var ops) -> op(inst, ops);
        };
    }

    private R op(Inst inst, List<S.Expr> ops) {
        return switch (inst.opcode()) {
            case Opcode.Binary(var op) -> binary(op, inst.type(), ops.get(0), ops.get(1));
            case Opcode.Unary(var op) -> switch (op) {
                case NEG, FNEG -> new R("-" + prefixOperand(expr(ops.get(0))), UNARY);
                case NOT -> new R((inst.type().equals(Type.I1) ? "!" : "~") + prefixOperand(expr(ops.get(0))), UNARY);
            };
            case Opcode.ICmp(var p) -> icmp(p, ops.get(0), ops.get(1));
            case Opcode.FCmp(var p) -> fcmp(p, ops.get(0), ops.get(1));
            case Opcode.Select _s -> new R(wrap(expr(ops.get(0)), LOR) + " ? " + wrap(expr(ops.get(1)), COND) + " : " + wrap(expr(ops.get(2)), COND), COND);
            case Opcode.Convert(var op) -> convert(op, ops.get(0), inst.type());
            case Opcode.PointerOffset _p -> {
                // Offsets are in bytes: a typed data pointer is converted back before adding.
                var base = ops.get(0) instanceof S.VarRef(var v) && elementTypes.containsKey(v) ? "(unsigned char *) " + names.get(v) : wrap(expr(ops.get(0)), ADD);
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
            case DIV, FDIV -> infix(a, "/", b, MUL);
            case REM -> infix(a, "%", b, MUL);
            case FREM -> new R((type.equals(Type.F32) ? "fmodf(" : "fmod(") + expr(a).text + ", " + expr(b).text + ")", ATOM);
            case AND -> infix(a, "&", b, BAND);
            case OR -> infix(a, "|", b, BOR);
            case XOR -> infix(a, "^", b, BXOR);
            case SHL -> new R(wrap(expr(a), SHIFT) + " << " + shiftCount(b, type), SHIFT);
            case SHR -> new R(wrap(expr(a), SHIFT) + " >> " + shiftCount(b, type), SHIFT);
            case USHR -> {
                var unsigned = type.equals(Type.I64) ? "unsigned long long" : "unsigned int";
                yield new R("(" + typeName(type) + ") ((" + unsigned + ") " + wrap(expr(a), UNARY) + " >> " + shiftCount(b, type) + ")", UNARY);
            }
        };
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
            var u = a.type().equals(Type.I64) ? "(unsigned long long) " : "(unsigned int) ";
            return new R(u + wrap(expr(a), UNARY) + " " + symbol + " " + u + wrap(expr(b), UNARY), prec);
        }
        return new R(wrap(expr(a), prec) + " " + symbol + " " + wrap(expr(b), prec + 1), prec);
    }

    private R fcmp(FloatPredicate p, S.Expr a, S.Expr b) {
        var x = wrap(expr(a), REL + 1);
        var y = wrap(expr(b), REL + 1);
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

    private R convert(Opcode.ConvertOp op, S.Expr value, Type to) {
        var from = value.type();
        var v = expr(value);
        return switch (op) {
            case SEXT, TRUNC, I2F, FEXT, FTRUNC -> cast(to, v);
            case ZEXT -> from.equals(Type.I1) ? cast(to, v) : cast(to, new R("(" + unsignedName(from) + ") " + wrap(v, UNARY), UNARY));
            case F2I_SAT -> {
                var fn = (from.equals(Type.F64) ? "__double2" : "__float2") + (to.equals(Type.I64) ? "ll_rz" : "int_rz");
                if (from.equals(Type.F16)) {
                    usesHalf = true;
                    fn = to.equals(Type.I64) ? "__half2ll_rz" : "__half2int_rz";
                }
                yield new R(fn + "(" + v.text + ")", ATOM);
            }
            case BITCAST -> {
                usesHalf |= from.equals(Type.F16) || to.equals(Type.F16);
                var fn = switch (from.text() + "->" + to.text()) {
                    case "f32->i32" -> "__float_as_int";
                    case "i32->f32" -> "__int_as_float";
                    case "f64->i64" -> "__double_as_longlong";
                    case "i64->f64" -> "__longlong_as_double";
                    case "f16->i16" -> "__half_as_short";
                    case "i16->f16" -> "__short_as_half";
                    default -> throw new EmitException("TC-E4001", "bitcast " + from.text() + " to " + to.text() + " is not supported on CUDA yet");
                };
                yield new R(fn + "(" + v.text + ")", ATOM);
            }
        };
    }

    private R cast(Type to, R value) {
        if (to.equals(Type.F16)) {
            usesHalf = true;
            return new R("__float2half_rn(" + value.text + ")", ATOM);
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
                    yield value == Long.MIN_VALUE ? new R("(-9223372036854775807LL - 1)", ATOM) : new R(value + "LL", value < 0 ? UNARY : ATOM);
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
        if (Double.isNaN(value)) {
            return new R(single ? "__int_as_float(0x7fc00000)" : "__longlong_as_double(0x7ff8000000000000LL)", ATOM);
        }
        if (Double.isInfinite(value)) {
            var bits = single ? (value > 0 ? "__int_as_float(0x7f800000)" : "__int_as_float(0xff800000)") : (value > 0 ? "__longlong_as_double(0x7ff0000000000000LL)" : "__longlong_as_double(0xfff0000000000000LL)");
            return new R(bits, ATOM);
        }
        var text = single ? Float.toString((float) value) + "f" : Double.toString(value);
        var literal = new R(text, text.startsWith("-") ? UNARY : ATOM);
        if (type.equals(Type.F16)) {
            usesHalf = true;
            return new R("__float2half_rn(" + text + ")", ATOM);
        }
        if (type.equals(Type.BF16)) {
            usesBFloat16 = true;
            return new R("__float2bfloat16_rn(" + text + ")", ATOM);
        }
        return literal;
    }

    private R intrinsic(String name, Inst inst, List<S.Expr> ops) {
        if (name.startsWith("tile.")) {
            // Tile values are always named variables; the result is a whole statement's expression.
            return new R(TileSpellings.expression(name, inst, ops.stream().map(o -> wrap(expr(o), UNARY)).toList()), COND);
        }
        if (name.equals("tornado.vector.make")) {
            // A vector value from its lanes: make_float4(x, y, z, w).
            return new R("make_" + typeName(inst.type()) + "(" + String.join(", ", ops.stream().map(o -> expr(o).text).toList()) + ")", ATOM);
        }
        // A void intrinsic (an atomic update) is typed by its value operand, the last one.
        var type = inst.producesValue() ? inst.type() : (ops.isEmpty() ? Type.VOID : ops.get(ops.size() - 1).type());
        var spelling = Intrinsics.lookup(name, target.dialect(), type).orElseThrow(() -> new EmitException("TC-E4002", "no " + target.dialect() + " spelling for intrinsic " + name + " on " + type.text()));
        spelling.helper().ifPresent(helpers::add);
        var template = Intrinsics.fillImmediates(spelling.template(), inst.immediates());
        if (template.contains("$&0")) {
            // The address of the element operand 0 points to: `&a_data[i]`, or a typed cast of the pointer.
            var element = deref(ops.get(0), type);
            var address = element.text.startsWith("*((") ? "((" + typeName(type) + " *) " + wrap(expr(ops.get(0)), UNARY) + ")" : "&" + element.text;
            template = template.replace("$&0", address);
        }
        var operands = ops.stream().map(this::expr).toList();
        var text = Intrinsics.expand(template, operands.stream().map(R::text).toList(), i -> operands.get(i).prec >= UNARY);
        return new R(text, Intrinsics.isSingleCall(text) ? ATOM : COND);
    }

    /**
     * A memory access of {@code type} through {@code pointer}. Element accesses through a typed data
     * pointer or a local array print as {@code a_data[i]} / {@code scratch[i]}; anything else as a cast
     * and dereference.
     */
    private R deref(S.Expr pointer, Type type) {
        int size = sizeOf(type);
        if (pointer instanceof S.Op(var inst, var ops) && inst.opcode() instanceof Opcode.PointerOffset) {
            var array = arrayName(ops.get(0), type);
            var index = index(ops.get(1), size);
            if (array != null && index != null) {
                return new R(array + "[" + expr(index).text + "]", ATOM);
            }
        }
        var array = arrayName(pointer, type);
        if (array != null) {
            return new R(array + "[0]", ATOM);
        }
        return new R("*((" + typeName(type) + " *) " + wrap(expr(pointer), UNARY) + ")", UNARY);
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

    /** The element index of a byte offset {@code sext(i) * size} (or {@code i * size}, or {@code sext(i)} for bytes). */
    private static S.Expr index(S.Expr offset, int size) {
        if (offset instanceof S.Op(var inst, var ops) && inst.opcode() instanceof Opcode.Binary(var op) && op == BinaryOp.MUL && ops.get(1) instanceof S.Const(var c)
                && c.opcode() instanceof Opcode.ConstInt(var s) && s == size) {
            return stripSext(ops.get(0));
        }
        if (size == 1 && offset instanceof S.Op(var inst, var ops) && inst.opcode() instanceof Opcode.Convert(var op) && op == Opcode.ConvertOp.SEXT) {
            return ops.get(0);
        }
        // A constant byte offset (an unrolled index): `r + 8` with 4-byte elements is r[2].
        if (offset instanceof S.Const(var c) && c.opcode() instanceof Opcode.ConstInt(var bytes) && bytes % size == 0) {
            return new S.Const(new Inst(new Opcode.ConstInt(bytes / size), Type.I32));
        }
        return null;
    }

    private static S.Expr stripSext(S.Expr e) {
        return e instanceof S.Op(var inst, var ops) && inst.opcode() instanceof Opcode.Convert(var op) && op == Opcode.ConvertOp.SEXT ? ops.get(0) : e;
    }

    private static int sizeOf(Type type) {
        return switch (type.text()) {
            case "i8", "i1" -> 1;
            case "i16", "f16", "bf16" -> 2;
            case "i64", "f64" -> 8;
            default -> 4;
        };
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
        var t = typeName(type);
        return t.endsWith("*") ? t + name : t + " " + name;
    }

    private String typeName(Type type) {
        return switch (type) {
            case IntType(var bits) -> switch (bits) {
                case 1 -> "bool";
                case 8 -> "signed char"; // Java byte; plain char's signedness is implementation-defined
                case 16 -> "short";
                case 32 -> "int";
                default -> "long long";
            };
            case FloatType f -> switch (f.kind()) {
                case F16 -> {
                    usesHalf = true;
                    yield "half";
                }
                case BF16 -> {
                    usesBFloat16 = true;
                    yield "__nv_bfloat16";
                }
                case F32 -> "float";
                case F64 -> "double";
            };
            case Type.PointerType p -> "unsigned char *";
            case Type.TileType t when !t.view() -> TileSpellings.tileType(t);
            case Type.VectorType(var element, var length) when element.equals(Type.F16) && length == 2 -> {
                usesHalf = true;
                yield "half2";
            }
            // Register vectors (tensor-core fragments): the CUDA built-in vector types.
            case Type.VectorType(var element, var length) when length >= 2 && length <= 4 && !element.equals(Type.F16) && !element.equals(Type.BF16) && !element.equals(Type.I1) ->
                (element.equals(Type.I8) ? "char" : typeName(element).replace("long long", "longlong")) + length; // CUDA charN lanes are signed char
            case Type.VoidType v -> "void";
            default -> throw new EmitException("TC-E4001", "type " + type.text() + " is not supported on CUDA yet");
        };
    }

    private static String unsignedName(Type type) {
        return switch (type.text()) {
            case "i8" -> "unsigned char";
            case "i16" -> "unsigned short";
            case "i64" -> "unsigned long long";
            default -> "unsigned int";
        };
    }

    private String cIdentifier(String name, Set<String> used) {
        return Naming.of(target.dialect()).identifier(name, used);
    }

    private static EmitException unsupported(Inst inst, String why) {
        return new EmitException("TC-E4001", "'" + inst.opcode().mnemonic() + "': " + why);
    }
}
