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
package uk.ac.manchester.tornado.tc.opt;

import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;

/**
 * Constant folding and algebraic simplification (optimisation list items O2, O3) with Java
 * semantics (JLS §15, §5.1): integer arithmetic wraps, division truncates toward zero and
 * {@code MIN_VALUE / -1 == MIN_VALUE}, shift counts are masked to the width, float-to-integer
 * conversion saturates with NaN becoming 0, and floating-point folding uses Java's own IEEE
 * arithmetic. Only exact identities are applied: {@code x + 0.0} is not folded because
 * {@code -0.0 + 0.0 == +0.0}, while {@code x + (-0.0)} is. Division by a constant zero is left alone
 * (Java throws).
 */
public final class Canonicalizer implements Pass {

    @Override
    public String name() {
        return "canonicalize";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (var block : function.blocks()) {
                for (var inst : List.copyOf(block.insts())) {
                    if (inst.block().isEmpty() || !inst.producesValue()) {
                        continue;
                    }
                    var replacement = simplify(inst);
                    if (replacement.isPresent() && replacement.get() != inst) {
                        inst.replaceAllUsesWith(replacement.get());
                        inst.erase();
                        progress = true;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private Optional<Value> simplify(Inst inst) {
        var ops = inst.operands();
        var type = inst.type();
        return switch (inst.opcode()) {
            case Opcode.Binary(var op) -> {
                var a = ops.get(0);
                var b = ops.get(1);
                var ca = Constants.number(a);
                var cb = Constants.number(b);
                if (ca.isPresent() && cb.isPresent()) {
                    yield fold(op, type, ca.get(), cb.get()).map(v -> make(inst, type, v));
                }
                yield identity(op, type, a, b, ca, cb);
            }
            case Opcode.Unary(var op) -> Constants.number(ops.get(0)).map(c -> switch (op) {
                case NEG -> make(inst, type, wrap(-c.longValue(), type));
                case NOT -> make(inst, type, wrap(~c.longValue(), type));
                case FNEG -> make(inst, type, -c.doubleValue());
            });
            case Opcode.ICmp(var p) -> {
                var ca = Constants.number(ops.get(0));
                var cb = Constants.number(ops.get(1));
                if (ca.isPresent() && cb.isPresent()) {
                    yield Optional.of(make(inst, Type.I1, compare(p, ca.get().longValue(), cb.get().longValue(), ops.get(0).type()) ? 1L : 0L));
                }
                if (ops.get(0) == ops.get(1)) {
                    yield Optional.of(make(inst, Type.I1, reflexive(p) ? 1L : 0L));
                }
                yield signFlippedCompare(inst, p, ops.get(0), ops.get(1));
            }
            case Opcode.FCmp(var p) -> {
                var ca = Constants.number(ops.get(0));
                var cb = Constants.number(ops.get(1));
                if (ca.isPresent() && cb.isPresent()) {
                    yield Optional.of(make(inst, Type.I1, compare(p, ca.get().doubleValue(), cb.get().doubleValue()) ? 1L : 0L));
                }
                yield Optional.empty();
            }
            case Opcode.Select s -> {
                var c = Constants.number(ops.get(0));
                if (c.isPresent()) {
                    yield Optional.of(c.get().longValue() != 0 ? ops.get(1) : ops.get(2));
                }
                yield ops.get(1) == ops.get(2) ? Optional.of(ops.get(1)) : Optional.empty();
            }
            case Opcode.Convert(var op) -> {
                var folded = Constants.number(ops.get(0)).flatMap(c -> convert(op, ops.get(0).type(), type, c)).map(v -> make(inst, type, v));
                // trunc(sext x) and trunc(zext x) back to x's own type are x: the extension only added high bits.
                if (folded.isEmpty() && op == ConvertOp.TRUNC && ops.get(0) instanceof Inst ext && ext.opcode() instanceof Opcode.Convert(var inner)
                        && (inner == ConvertOp.SEXT || inner == ConvertOp.ZEXT) && ext.operand(0).type().equals(type)) {
                    yield Optional.of(ext.operand(0));
                }
                yield folded;
            }
            case Opcode.PointerOffset p -> Constants.number(ops.get(1)).filter(c -> c.longValue() == 0).map(c -> ops.get(0));
            case Opcode.Intrinsic(var name) when name.equals(INSERT) && type instanceof Type.VectorType vec -> wholeVector(inst, vec);
            case Opcode.Intrinsic(var name) when name.equals(LANE) -> laneOf(inst, ops.get(0), inst.immediates().requireInt("lane"));
            // A lane by an index that became a constant (an unrolled Float8.add) is an ordinary lane access.
            case Opcode.Intrinsic(var name) when name.equals("tornado.vector.lane_at") && Constants.integer(ops.get(1)).isPresent() ->
                Optional.of(IrBuilder.insertingBefore(inst).intrinsic(LANE, Immediates.of("lane", Constants.integer(ops.get(1)).getAsLong()), type, List.of(ops.get(0))));
            case Opcode.Intrinsic(var name) when name.equals("tornado.vector.insert_at") && Constants.integer(ops.get(1)).isPresent() ->
                Optional.of(IrBuilder.insertingBefore(inst).intrinsic(INSERT, Immediates.of("lane", Constants.integer(ops.get(1)).getAsLong()), type, List.of(ops.get(0), ops.get(2))));
            default -> Optional.empty();
        };
    }

    private static final String INSERT = "tornado.vector.insert";
    private static final String LANE = "tornado.vector.lane";

    /**
     * Lane {@code lane} of a vector made from lanes, or of an insert chain that sets it, is that lane's
     * value; inserts of other lanes are skipped (the lane is read from the vector they insert into).
     */
    private static Optional<Value> laneOf(Inst read, Value vector, int lane) {
        Value current = vector;
        if (!(vector.type() instanceof Type.VectorType vec) || lane < 0 || lane >= vec.length()) {
            return Optional.empty(); // malformed: left for the verifier (IR-3)
        }
        while (current instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name)) {
            if (name.equals("tornado.vector.make")) {
                return Optional.of(i.operand(lane));
            }
            if (!name.equals(INSERT)) {
                break;
            }
            if (i.immediates().requireInt("lane") == lane) {
                return Optional.of(i.operand(1));
            }
            current = i.operand(0);
        }
        if (current == vector) {
            return Optional.empty();
        }
        var direct = new uk.ac.manchester.tornado.tc.ir.IrBuilder(read.block().orElseThrow()).before(read).at(read.loc()).intrinsic(LANE, uk.ac.manchester.tornado.tc.ir.Immediates.of("lane", lane), read.type(), List.of(current));
        direct.setName(read.name());
        return Optional.of(direct);
    }

    /**
     * A chain of lane inserts that sets every lane (a vector built field by field, {@code new Float2()}
     * then {@code setX}, {@code setY}) is the vector of those lanes: {@code vector.make}. The later
     * insert of a lane wins; lanes not inserted come from a {@code vector.make} at the chain's root.
     */
    private Optional<Value> wholeVector(Inst inst, Type.VectorType vec) {
        var lanes = new Value[vec.length()];
        Value current = inst;
        while (current instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(INSERT)) {
            int lane = i.immediates().requireInt("lane");
            if (lane < 0 || lane >= lanes.length) {
                return Optional.empty(); // malformed: left for the verifier (IR-3)
            }
            if (lanes[lane] == null) {
                lanes[lane] = i.operand(1);
            }
            current = i.operand(0);
        }
        if (current instanceof Inst root && root.opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tornado.vector.make")) {
            for (int l = 0; l < lanes.length; l++) {
                if (lanes[l] == null) {
                    lanes[l] = root.operand(l);
                }
            }
        }
        if (java.util.Arrays.stream(lanes).anyMatch(java.util.Objects::isNull)) {
            return Optional.empty();
        }
        var made = new uk.ac.manchester.tornado.tc.ir.IrBuilder(inst.block().orElseThrow()).before(inst).at(inst.loc()).intrinsic("tornado.vector.make", vec, List.of(lanes));
        made.setName(inst.name());
        return Optional.of(made);
    }

    /** Exact algebraic identities with one constant operand. */
    private Optional<Value> identity(BinaryOp op, Type type, Value a, Value b, Optional<Number> ca, Optional<Number> cb) {
        long ia = ca.map(Number::longValue).orElse(Long.MIN_VALUE);
        long ib = cb.map(Number::longValue).orElse(Long.MIN_VALUE);
        boolean isInt = type.isInteger();
        int mask = type.equals(Type.I64) ? 63 : 31;
        return switch (op) {
            case ADD -> isInt && cb.isPresent() && ib == 0 ? Optional.of(a) : isInt && ca.isPresent() && ia == 0 ? Optional.of(b) : Optional.empty();
            case SUB -> isInt && cb.isPresent() && ib == 0 ? Optional.of(a) : Optional.empty();
            case MUL -> isInt && cb.isPresent() && ib == 1 ? Optional.of(a) : isInt && ca.isPresent() && ia == 1 ? Optional.of(b) : Optional.empty();
            case AND -> cb.isPresent() && ib == -1 ? Optional.of(a) : ca.isPresent() && ia == -1 ? Optional.of(b) : Optional.empty();
            case OR, XOR -> cb.isPresent() && ib == 0 ? Optional.of(a) : ca.isPresent() && ia == 0 ? Optional.of(b) : Optional.empty();
            case SHL, SHR, USHR -> cb.isPresent() && (ib & mask) == 0 ? Optional.of(a) : Optional.empty();
            case FMUL -> isExactly(cb, 1.0) ? Optional.of(a) : isExactly(ca, 1.0) ? Optional.of(b) : Optional.empty();
            case FADD -> isNegativeZero(cb) ? Optional.of(a) : isNegativeZero(ca) ? Optional.of(b) : Optional.empty();
            case FSUB -> isPositiveZero(cb) ? Optional.of(a) : Optional.empty();
            case FDIV -> isExactly(cb, 1.0) ? Optional.of(a) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static boolean isExactly(Optional<Number> c, double v) {
        return c.isPresent() && c.get() instanceof Double d && d == v;
    }

    private static boolean isNegativeZero(Optional<Number> c) {
        return c.isPresent() && c.get() instanceof Double d && Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0.0);
    }

    private static boolean isPositiveZero(Optional<Number> c) {
        return c.isPresent() && c.get() instanceof Double d && Double.doubleToRawLongBits(d) == 0L;
    }

    private static Optional<Number> fold(BinaryOp op, Type type, Number x, Number y) {
        if (op.isFloat) {
            if (type.equals(Type.F32)) {
                float a = x.floatValue();
                float b = y.floatValue();
                return Optional.of((double) switch (op) {
                    case FADD -> a + b;
                    case FSUB -> a - b;
                    case FMUL -> a * b;
                    case FDIV -> a / b;
                    default -> a % b;
                });
            }
            if (type.equals(Type.F64)) {
                double a = x.doubleValue();
                double b = y.doubleValue();
                return Optional.of(switch (op) {
                    case FADD -> a + b;
                    case FSUB -> a - b;
                    case FMUL -> a * b;
                    case FDIV -> a / b;
                    default -> a % b;
                });
            }
            return Optional.empty();
        }
        long a = x.longValue();
        long b = y.longValue();
        boolean wide = type.equals(Type.I64);
        int mask = wide ? 63 : 31;
        long result = switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV, REM -> {
                if (b == 0) {
                    yield Long.MIN_VALUE; // marker, rejected below
                }
                if (wide) {
                    yield op == BinaryOp.DIV ? a / b : a % b;
                }
                int ia = (int) a;
                int ib = (int) b;
                yield op == BinaryOp.DIV ? ia / ib : ia % ib;
            }
            case AND -> a & b;
            case OR -> a | b;
            case XOR -> a ^ b;
            case SHL -> wide ? a << (b & mask) : (int) a << (b & mask);
            case SHR -> wide ? a >> (b & mask) : (int) a >> (b & mask);
            case USHR -> wide ? a >>> (b & mask) : (int) a >>> (b & mask);
            default -> throw new IllegalStateException();
        };
        if ((op == BinaryOp.DIV || op == BinaryOp.REM) && b == 0) {
            return Optional.empty();
        }
        return Optional.of(wrap(result, type));
    }

    private static Optional<Number> convert(ConvertOp op, Type from, Type to, Number c) {
        return switch (op) {
            case SEXT -> Optional.of(wrap(c.longValue(), to));
            case ZEXT -> Optional.of(c.longValue() & widthMask(from));
            case TRUNC -> Optional.of(wrap(c.longValue(), to));
            case I2F -> to.equals(Type.F32) ? Optional.of((double) (float) c.longValue()) : to.equals(Type.F64) ? Optional.of((double) c.longValue()) : Optional.empty();
            case F2I_SAT -> to.equals(Type.I32) ? Optional.of((long) (int) c.doubleValue()) : to.equals(Type.I64) ? Optional.of((long) c.doubleValue()) : Optional.empty();
            case FEXT -> Optional.of(c.doubleValue());
            case FTRUNC -> to.equals(Type.F32) ? Optional.of((double) (float) c.doubleValue()) : Optional.empty();
            case BITCAST -> {
                if (from.equals(Type.F32) && to.equals(Type.I32)) {
                    yield Optional.of((long) Float.floatToRawIntBits(c.floatValue()));
                }
                // A NaN stays a bit cast of its integer bits: a float constant is carried as a double,
                // and widening a signalling NaN quietens it (0x7f800001 would become 0x7fc00001).
                if (from.equals(Type.I32) && to.equals(Type.F32) && !Float.isNaN(Float.intBitsToFloat((int) c.longValue()))) {
                    yield Optional.of((double) Float.intBitsToFloat((int) c.longValue()));
                }
                if (from.equals(Type.F64) && to.equals(Type.I64)) {
                    yield Optional.of(Double.doubleToRawLongBits(c.doubleValue()));
                }
                if (from.equals(Type.I64) && to.equals(Type.F64) && !Double.isNaN(Double.longBitsToDouble(c.longValue()))) {
                    yield Optional.of(Double.longBitsToDouble(c.longValue()));
                }
                yield Optional.empty();
            }
        };
    }

    private static boolean compare(IntPredicate p, long a, long b, Type type) {
        long ua = a & widthMask(type);
        long ub = b & widthMask(type);
        return switch (p) {
            case EQ -> a == b;
            case NE -> a != b;
            case SLT -> a < b;
            case SLE -> a <= b;
            case SGT -> a > b;
            case SGE -> a >= b;
            case ULT -> Long.compareUnsigned(ua, ub) < 0;
            case ULE -> Long.compareUnsigned(ua, ub) <= 0;
            case UGT -> Long.compareUnsigned(ua, ub) > 0;
            case UGE -> Long.compareUnsigned(ua, ub) >= 0;
        };
    }

    /**
     * {@code (x + MIN) <s (y + MIN)} is {@code x <u y}, and the other way round: adding (or
     * subtracting) the sign bit to both sides turns a signed comparison into the unsigned one. This
     * is how the JDK implements {@code Integer.compareUnsigned} and {@code Long.compareUnsigned}.
     * Comparing the operands directly also matters for correctness on the device: C and OpenCL C
     * treat signed overflow as undefined, so driver compilers fold {@code x + c < y + c} to
     * {@code x < y}, which is wrong when the additions wrap. A constant side is flipped in place.
     */
    private static Optional<Value> signFlippedCompare(Inst cmp, IntPredicate p, Value a, Value b) {
        var type = a.type();
        if (!type.equals(Type.I32) && !type.equals(Type.I64)) {
            return Optional.empty();
        }
        long min = type.equals(Type.I32) ? Integer.MIN_VALUE : Long.MIN_VALUE;
        var x = signFlipped(a, min);
        var y = signFlipped(b, min);
        var ca = Constants.integer(a);
        var cb = Constants.integer(b);
        if (x.isEmpty() && y.isEmpty() || x.isEmpty() && ca.isEmpty() || y.isEmpty() && cb.isEmpty()) {
            return Optional.empty();
        }
        var builder = IrBuilder.insertingBefore(cmp);
        Value lhs = x.isPresent() ? x.get() : builder.constInt(type, wrap(ca.getAsLong() ^ min, type));
        Value rhs = y.isPresent() ? y.get() : builder.constInt(type, wrap(cb.getAsLong() ^ min, type));
        var flipped = switch (p) {
            case EQ, NE -> p;
            case SLT -> IntPredicate.ULT;
            case SLE -> IntPredicate.ULE;
            case SGT -> IntPredicate.UGT;
            case SGE -> IntPredicate.UGE;
            case ULT -> IntPredicate.SLT;
            case ULE -> IntPredicate.SLE;
            case UGT -> IntPredicate.SGT;
            case UGE -> IntPredicate.SGE;
        };
        var result = builder.icmp(flipped, lhs, rhs);
        result.setName(cmp.name());
        return Optional.of(result);
    }

    /** {@code x} when {@code v} is {@code x + MIN} or {@code x - MIN} (the same value). */
    private static Optional<Value> signFlipped(Value v, long min) {
        if (v instanceof Inst inst && inst.opcode() instanceof Opcode.Binary(var op) && (op == BinaryOp.ADD || op == BinaryOp.SUB)) {
            var c = Constants.integer(inst.operand(1));
            if (c.isPresent() && c.getAsLong() == min) {
                return Optional.of(inst.operand(0));
            }
            if (op == BinaryOp.ADD && Constants.integer(inst.operand(0)).isPresent() && Constants.integer(inst.operand(0)).getAsLong() == min) {
                return Optional.of(inst.operand(1));
            }
        }
        return Optional.empty();
    }

    private static boolean reflexive(IntPredicate p) {
        return switch (p) {
            case EQ, SLE, SGE, ULE, UGE -> true;
            default -> false;
        };
    }

    private static boolean compare(FloatPredicate p, double a, double b) {
        boolean unordered = Double.isNaN(a) || Double.isNaN(b);
        return switch (p) {
            case OEQ -> !unordered && a == b;
            case ONE -> !unordered && a != b;
            case OLT -> !unordered && a < b;
            case OLE -> !unordered && a <= b;
            case OGT -> !unordered && a > b;
            case OGE -> !unordered && a >= b;
            case UEQ -> unordered || a == b;
            case UNE -> unordered || a != b;
            case ULT -> unordered || a < b;
            case ULE -> unordered || a <= b;
            case UGT -> unordered || a > b;
            case UGE -> unordered || a >= b;
            case ORD -> !unordered;
            case UNO -> unordered;
        };
    }

    private static long widthMask(Type type) {
        return switch (type.text()) {
            case "i1" -> 1L;
            case "i8" -> 0xffL;
            case "i16" -> 0xffffL;
            case "i32" -> 0xffff_ffffL;
            default -> -1L;
        };
    }

    /** Wraps {@code v} to the width of {@code type} with sign extension (two's complement). */
    private static long wrap(long v, Type type) {
        return switch (type.text()) {
            case "i1" -> v & 1;
            case "i8" -> (byte) v;
            case "i16" -> (short) v;
            case "i32" -> (int) v;
            default -> v;
        };
    }

    private static Value make(Inst at, Type type, Number value) {
        var b = IrBuilder.insertingBefore(at);
        Inst c = type.isFloat() ? b.constFloat(type, value.doubleValue()) : b.constInt(type, value.longValue());
        c.setName(at.name());
        return c;
    }
}
