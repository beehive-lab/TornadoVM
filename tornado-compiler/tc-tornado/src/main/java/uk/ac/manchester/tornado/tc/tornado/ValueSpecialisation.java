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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Value specialisation: folds what is known about the actual task
 * arguments into the kernel, as TornadoVM's task specialisation does on the Graal path. A kernel
 * compiled this way is valid only for those argument facts, so they are part of the compilation-cache
 * key.
 *
 * <ul>
 * <li>{@link Fact.Length}: {@code array.length} of that parameter becomes a constant. Required for
 * correctness, not just speed: on the device, the header of an array that is not copied in is not
 * initialised.</li>
 * <li>{@link Fact.Scalar}: uses of a primitive parameter become the constant; the parameter stays in
 * the signature, as on the Graal path.</li>
 * </ul>
 */
public final class ValueSpecialisation {

    /** What is known about one argument. */
    public sealed interface Fact {
        record Unknown() implements Fact {
        }

        record Length(long elements) implements Fact {
        }

        /**
         * Not an argument: in batch {@code n} of a batched task, the {@code @Parallel} index of this
         * chunk is {@code offset + i} (offset = n x batch size) wherever it is used as a value; array
         * indices stay chunk-local. Given after the argument facts.
         */
        record IndexOffset(long offset) implements Fact {
        }

        /**
         * Not an argument: the task runs with a worker grid the user gave ({@code GridScheduler}), so
         * its {@code @Parallel} loops need no launch domain: they stride over whatever grid runs, and
         * their bounds may be values only the device knows (array contents). Given after the argument facts.
         */
        record WorkerGrid() implements Fact {
        }

        /** A {@code @Reduce} array: the runtime gives the kernel one partial-result slot per work-group (see {@link ReductionLowering}). */
        record Reduce(long elements) implements Fact {
        }

        record Scalar(Number value) implements Fact {
        }

        /**
         * An object argument: the values of its final primitive fields (they fold, as on the Graal
         * path) and the layouts of its device copy and of the objects it references.
         */
        record Instance(Map<String, Number> finalFields, List<ObjectLayout> layouts, Map<String, Long> arrayLengths) implements Fact {
            public Instance(Map<String, Number> finalFields, List<ObjectLayout> layouts) {
                this(finalFields, layouts, Map.of());
            }
        }

        static Fact of(Object value) {
            return switch (value) {
                case null -> new Unknown();
                case Fact f -> f;
                case Integer i -> new Scalar(i);
                case Long l -> new Scalar(l);
                case Float f -> new Scalar(f);
                case Double d -> new Scalar(d);
                case Short s -> new Scalar(s.intValue());
                case Byte b -> new Scalar(b.intValue());
                case Character c -> new Scalar((int) c.charValue());
                case Boolean b -> new Scalar(b ? 1 : 0);
                // Java arrays: like native arrays, the device copy of the header is not initialised.
                case Object o when o.getClass().isArray() -> new Length(java.lang.reflect.Array.getLength(o));
                default -> new Unknown();
            };
        }
    }

    private ValueSpecialisation() {
    }

    /** {@code facts.get(i)} describes parameter {@code i} of {@code function} (Java order, before ABI lowering). */
    public static int run(Function function, List<Fact> facts) {
        int folded = foldParameterFacts(function, facts);
        return folded + foldMergedLengths(function, facts);
    }

    /**
     * The length of an array chosen at run time ({@code x = c ? a : b}, a loop-carried array) folds
     * when every array that can reach it has the same known length: the device header of an array the
     * kernel only writes is not initialised, so a length read there would be garbage.
     */
    private static int foldMergedLengths(Function function, List<Fact> facts) {
        var lengths = new java.util.IdentityHashMap<Value, Long>();
        var params = function.params();
        for (int i = 0; i < Math.min(params.size(), facts.size()); i++) {
            if (facts.get(i) instanceof Fact.Length(var elements)) {
                lengths.put(params.get(i), elements);
            }
        }
        int folded = 0;
        for (var block : function.blocks()) {
            if (block == function.entry()) {
                continue;
            }
            for (var arg : block.args()) {
                if (!(arg.type() instanceof Type.ArrayType)) {
                    continue;
                }
                var roots = new java.util.HashSet<Long>();
                if (!collectLengths(arg, lengths, roots, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())) || roots.size() != 1) {
                    continue;
                }
                long elements = roots.iterator().next();
                for (var user : List.copyOf(arg.users())) {
                    if (user.opcode() instanceof Opcode.ArrayLength && (user.type().equals(Type.I64) || elements <= Integer.MAX_VALUE)) {
                        var constant = IrBuilder.insertingBefore(user).constInt(user.type(), elements);
                        constant.setName(user.name());
                        user.replaceAllUsesWith(constant);
                        user.erase();
                        folded++;
                    }
                }
            }
        }
        return folded;
    }

    /** The known lengths of the arrays that reach {@code v}; false when one of them is unknown. */
    private static boolean collectLengths(Value v, Map<Value, Long> lengths, java.util.Set<Long> out, java.util.Set<Value> seen) {
        if (!seen.add(v)) {
            return true;
        }
        if (lengths.containsKey(v)) {
            out.add(lengths.get(v));
            return true;
        }
        if (!(v instanceof uk.ac.manchester.tornado.tc.ir.BlockArg arg) || arg.block() == arg.block().function().entry()) {
            return false;
        }
        int index = arg.block().args().indexOf(arg);
        for (var block : arg.block().function().blocks()) {
            var term = block.terminator().orElse(null);
            if (term == null) {
                continue;
            }
            for (var s : term.successors()) {
                if (s.target() == arg.block() && !collectLengths(s.args().get(index), lengths, out, seen)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int foldParameterFacts(Function function, List<Fact> facts) {
        int folded = 0;
        var params = function.params();
        for (int i = 0; i < Math.min(params.size(), facts.size()); i++) {
            var param = params.get(i);
            switch (facts.get(i)) {
                case Fact.Length(var elements) when param.type() instanceof Type.ArrayType -> {
                    for (var user : List.copyOf(param.users())) {
                        // getSize() is i32 and throws beyond Integer.MAX_VALUE in Java: only getSizeLong() folds then.
                        if (user.opcode() instanceof Opcode.ArrayLength && (user.type().equals(Type.I64) || elements <= Integer.MAX_VALUE)) {
                            var constant = IrBuilder.insertingBefore(user).constInt(user.type(), elements);
                            constant.setName(user.name());
                            user.replaceAllUsesWith(constant);
                            user.erase();
                            folded++;
                        }
                    }
                }
                case Fact.Instance(var finals, var layouts, var lengths) when param.type() instanceof Type.ObjectType -> {
                    // Lengths of native arrays held in fields (output.getSize() in an instance-method task).
                    for (var user : List.copyOf(param.users())) {
                        if (user.opcode() instanceof Opcode.FieldLoad(var field) && lengths.containsKey(field)) {
                            for (var lengthUser : List.copyOf(user.users())) {
                                if (lengthUser.opcode() instanceof Opcode.ArrayLength && (lengthUser.type().equals(Type.I64) || lengths.get(field) <= Integer.MAX_VALUE)) {
                                    var constant = IrBuilder.insertingBefore(lengthUser).constInt(lengthUser.type(), lengths.get(field));
                                    constant.setDerivedName(field + "_len");
                                    lengthUser.replaceAllUsesWith(constant);
                                    lengthUser.erase();
                                    folded++;
                                }
                            }
                        }
                    }
                    for (var user : List.copyOf(param.users())) {
                        if (user.opcode() instanceof Opcode.FieldLoad(var field) && finals.containsKey(field) && (user.type().isInteger() || user.type().isFloat())) {
                            var b = IrBuilder.insertingBefore(user);
                            var value = finals.get(field);
                            var constant = user.type().isFloat() ? b.constFloat(user.type(), value.doubleValue()) : b.constInt(user.type(), value.longValue());
                            constant.setName(user.name().isEmpty() || user.name().chars().allMatch(Character::isDigit) ? field : user.name());
                            user.replaceAllUsesWith(constant);
                            user.erase();
                            folded++;
                        }
                    }
                }
                case Fact.Scalar(var value) when param.hasUses() && (param.type().isInteger() || param.type().isFloat()) -> {
                    // At the start of the entry block, so it dominates every use.
                    var entry = function.entry();
                    var b = new IrBuilder(entry);
                    if (!entry.insts().isEmpty()) {
                        b.before(entry.insts().get(0));
                    }
                    Inst constant = param.type().isFloat() ? b.constFloat(param.type(), value.doubleValue()) : b.constInt(param.type(), value.longValue());
                    constant.setName(param.name());
                    param.replaceAllUsesWith(constant);
                    folded++;
                }
                default -> {
                }
            }
        }
        return folded;
    }

    /** All facts unknown: no specialisation. */
    public static List<Fact> none() {
        return List.of();
    }
}
