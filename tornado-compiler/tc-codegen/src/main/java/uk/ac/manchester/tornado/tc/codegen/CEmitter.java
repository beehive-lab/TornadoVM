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
    public record Result(String source, String entryName) {
    }

    private static final Set<String> RESERVED = Set.of(
            // C/C++ keywords that are plausible Java identifiers.
            "auto", "bool", "char", "const", "default", "delete", "double", "enum", "extern", "float", "goto", "inline", "int", "long", "operator", "register",
            "restrict", "short", "signed", "sizeof", "static", "struct", "template", "typedef", "typename", "union", "unsigned", "volatile", "half",
            // CUDA built-ins and the TornadoVM ABI region names.
            "threadIdx", "blockIdx", "blockDim", "gridDim", "warpSize", "_kernel_context", "_constant_region", "_local_region", "_atomics");

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
        var used = new HashSet<String>();
        k.params().forEach(v -> names.put(v, cIdentifier(v.name(), used)));
        k.locals().forEach(v -> names.put(v, cIdentifier(v.name(), used)));
        k.allocas().forEach(a -> allocaNames.put(a, cIdentifier(a.name(), used)));

        var f = k.source();
        var entry = entryName(f);
        var header = new StringBuilder();
        if (f.kind() == Function.Kind.KERNEL) {
            header.append("extern \"C\" __global__ void ").append(entry).append("(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics");
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
            line(1, (a.space() == AddressSpace.SHARED ? "__shared__ " : "") + typeName(a.element()) + " " + allocaNames.get(alloca) + "[" + a.count() + "];");
        }
        for (var v : k.locals()) {
            line(1, declaration(v.type(), names.get(v)) + ";");
        }
        statements(k.body(), Set.of(), 1);
        out.append("}\n");

        var body = resolveLoopLabels(out.toString());
        var source = new StringBuilder();
        if (usesHalf) {
            source.append("#include <cuda_fp16.h>\n");
        }
        if (usesBFloat16) {
            source.append("#include <cuda_bf16.h>\n");
        }
        if (usesHalf || usesBFloat16) {
            source.append('\n');
        }
        for (var helper : helpers) {
            source.append(Helpers.source(helper, target.dialect())).append('\n');
        }
        source.append(header).append(body);
        return new Result(source.toString(), entry);
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
            case S.Assign(var target, var value, var loc) -> line(depth, names.get(target) + " = " + expr(value).text + ";" + comment(loc));
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
        if (t.isEmpty() && e.isEmpty()) {
            return;
        }
        if (!e.isEmpty() && prefersComplement(c)) {
            // javac branches on the inverse test; print the direct comparison with the arms swapped.
            ifStatement(complement(c).orElseThrow(), e, t, fallthrough, depth);
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
        if (!e.isEmpty() && endsInJump(e) && !endsInJump(t) && complement(c).isPresent()) {
            ifStatement(complement(c).orElseThrow(), e, t, fallthrough, depth);
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
            case Opcode.Store _s -> deref(ops.get(0), ops.get(1).type()) + " = " + expr(ops.get(1)).text;
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
                case NEG, FNEG -> new R("-" + wrap(expr(ops.get(0)), UNARY), UNARY);
                case NOT -> new R((inst.type().equals(Type.I1) ? "!" : "~") + wrap(expr(ops.get(0)), UNARY), UNARY);
            };
            case Opcode.ICmp(var p) -> icmp(p, ops.get(0), ops.get(1));
            case Opcode.FCmp(var p) -> fcmp(p, ops.get(0), ops.get(1));
            case Opcode.Select _s -> new R(wrap(expr(ops.get(0)), LOR) + " ? " + wrap(expr(ops.get(1)), COND) + " : " + wrap(expr(ops.get(2)), COND), COND);
            case Opcode.Convert(var op) -> convert(op, ops.get(0), inst.type());
            case Opcode.PointerOffset _p -> new R(wrap(expr(ops.get(0)), ADD) + " + " + wrap(expr(ops.get(1)), ADD + 1), ADD);
            case Opcode.Load _l -> new R(deref(ops.get(0), inst.type()), UNARY);
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
                var fn = switch (from.text() + "->" + to.text()) {
                    case "f32->i32" -> "__float_as_int";
                    case "i32->f32" -> "__int_as_float";
                    case "f64->i64" -> "__double_as_longlong";
                    case "i64->f64" -> "__longlong_as_double";
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
        var type = inst.producesValue() ? inst.type() : (ops.isEmpty() ? Type.VOID : ops.get(0).type());
        var spelling = Intrinsics.lookup(name, target.dialect(), type).orElseThrow(() -> new EmitException("TC-E4002", "no " + target.dialect() + " spelling for intrinsic " + name + " on " + type.text()));
        spelling.helper().ifPresent(helpers::add);
        var text = Intrinsics.expand(spelling.template(), ops.stream().map(o -> expr(o).text).toList());
        return new R(text, text.startsWith("(") ? UNARY : ATOM);
    }

    private String deref(S.Expr pointer, Type type) {
        return "*((" + typeName(type) + " *) " + wrap(expr(pointer), UNARY) + ")";
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
                case 8 -> "char";
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

    private static String cIdentifier(String name, Set<String> used) {
        var base = RESERVED.contains(name) ? "_" + name : name;
        var candidate = base;
        for (int k = 1; !used.add(candidate); k++) {
            candidate = base + "_" + k;
        }
        return candidate;
    }

    private static EmitException unsupported(Inst inst, String why) {
        return new EmitException("TC-E4001", "'" + inst.opcode().mnemonic() + "': " + why);
    }
}
