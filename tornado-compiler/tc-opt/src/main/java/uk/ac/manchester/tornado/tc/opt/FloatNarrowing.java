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
package uk.ac.manchester.tornado.tc.opt;

import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Float arithmetic that Java writes in double, done in float where the result is the same.
 * {@code java.lang.Math} takes and returns double, so {@code (float) Math.floor(f)} is a widen, a
 * double floor and a narrow; such code needs a device with double precision and is slower everywhere.
 * Only exact rewrites are made, one operation deep:
 * <ul>
 *   <li>{@code ftrunc(op(fext a, fext b))} to {@code op(a, b)} in f32 for {@code fadd}, {@code fsub},
 *       {@code fmul} and {@code fdiv}: double has more than 2 x 24 + 2 bits, so rounding to double and
 *       then to float equals rounding to float once (Figueroa 1995);</li>
 *   <li>{@code ftrunc(f(fext a))} to {@code f(a)} for {@code sqrt} (the same argument), {@code floor},
 *       {@code ceil}, {@code rint}, {@code abs} and negation (results representable in float);</li>
 *   <li>{@code fcmp(fext a, fext b)} to {@code fcmp(a, b)}: widening is exact and keeps order.</li>
 * </ul>
 * A constant operand counts as widened when its value is exactly a float. Nested operations stay in
 * double: rounding an intermediate result to float would change it.
 */
public final class FloatNarrowing implements Pass {

    private final boolean divideAndSqrtExact;

    /** For a target whose float division and square root round correctly (CUDA; tests). */
    public FloatNarrowing() {
        this(true);
    }

    /**
     * @param divideAndSqrtExact whether the target's float division and sqrt round correctly; OpenCL
     *     allows 2.5 and 3 ulp, so in float they would no longer give Java's (double-rounded) result.
     *     Pass true as well for a device without double precision: there the alternative is no kernel.
     */
    public FloatNarrowing(boolean divideAndSqrtExact) {
        this.divideAndSqrtExact = divideAndSqrtExact;
    }

    private static final List<String> EXACT_UNARY = List.of("math.sqrt", "math.floor", "math.ceil", "math.rint", "math.abs");

    @Override
    public String name() {
        return "float-narrowing";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (inst.block().isEmpty()) {
                    continue;
                }
                if (inst.opcode() instanceof Opcode.Convert(var op) && op == ConvertOp.FTRUNC && inst.type().equals(Type.F32) && inst.operand(0).type().equals(Type.F64)
                        && inst.operand(0) instanceof Inst wide) {
                    var narrowed = narrow(wide, inst);
                    if (narrowed.isPresent()) {
                        narrowed.get().setName(inst.name());
                        inst.replaceAllUsesWith(narrowed.get());
                        inst.erase();
                        changed = true;
                    }
                } else if (inst.opcode() instanceof Opcode.FCmp && inst.operand(0).type().equals(Type.F64)) {
                    var a = asFloat(inst.operand(0), inst);
                    var b = asFloat(inst.operand(1), inst);
                    if (a.isPresent() && b.isPresent() && (isWidened(inst.operand(0)) || isWidened(inst.operand(1)))) {
                        inst.setOperand(0, a.get());
                        inst.setOperand(1, b.get());
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** The f32 form of a one-operation f64 value whose operands are widened floats; inserted before {@code at}. */
    private Optional<Inst> narrow(Inst wide, Inst at) {
        var b = IrBuilder.insertingBefore(at);
        switch (wide.opcode()) {
            case Opcode.Binary(var op) when op == BinaryOp.FADD || op == BinaryOp.FSUB || op == BinaryOp.FMUL || op == BinaryOp.FDIV && divideAndSqrtExact -> {
                if (!isWidened(wide.operand(0)) && !isWidened(wide.operand(1))) {
                    return Optional.empty();
                }
                var x = asFloat(wide.operand(0), at);
                var y = asFloat(wide.operand(1), at);
                if (x.isEmpty() || y.isEmpty()) {
                    return Optional.empty();
                }
                var result = b.binary(op, x.get(), y.get());
                wide.attributes().forEach(result::addAttribute);
                return Optional.of(result);
            }
            case Opcode.Unary(var op) when op == UnaryOp.FNEG -> {
                return asFloat(wide.operand(0), at).map(x -> b.unary(UnaryOp.FNEG, x));
            }
            case Opcode.Intrinsic(var name) when EXACT_UNARY.contains(name) && wide.operandCount() == 1 && (divideAndSqrtExact || !name.equals("math.sqrt")) -> {
                // A constant operand that is a float is as exact as a widened one (javac widens float literals).
                return asFloat(wide.operand(0), at).map(x -> b.intrinsic(name, Type.F32, List.of(x)));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private static boolean isWidened(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.Convert(var op) && op == ConvertOp.FEXT && i.operand(0).type().equals(Type.F32);
    }

    /** {@code v} as an f32 value when that is exact: the operand of a widening, or a constant that is a float. */
    private static Optional<Value> asFloat(Value v, Inst at) {
        if (isWidened(v)) {
            return Optional.of(((Inst) v).operand(0));
        }
        if (v instanceof Inst c && c.opcode() instanceof Opcode.ConstFloat(var value) && (Double.isNaN(value) || (double) (float) value == value)) {
            return Optional.of(IrBuilder.insertingBefore(at).constFloat(Type.F32, (float) value));
        }
        return Optional.empty();
    }
}
