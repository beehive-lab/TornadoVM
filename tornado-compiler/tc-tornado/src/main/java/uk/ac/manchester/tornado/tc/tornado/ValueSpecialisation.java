package uk.ac.manchester.tornado.tc.tornado;

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Value specialisation (optimisation list item T1): folds what is known about the actual task
 * arguments into the kernel, as TornadoVM's task specialisation does on the Graal path. A kernel
 * compiled this way is valid only for those argument facts, so they are part of the compilation-cache
 * key.
 *
 * <ul>
 * <li>{@link Fact.Length}: {@code array.length} of that parameter becomes a constant. Required for
 * correctness, not just speed: on the device, the header of an array that is not copied in is not
 * initialised (C3 finding).</li>
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

        record Scalar(Number value) implements Fact {
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
                default -> new Unknown();
            };
        }
    }

    private ValueSpecialisation() {
    }

    /** {@code facts.get(i)} describes parameter {@code i} of {@code function} (Java order, before ABI lowering). */
    public static int run(Function function, List<Fact> facts) {
        int folded = 0;
        var params = function.params();
        for (int i = 0; i < Math.min(params.size(), facts.size()); i++) {
            var param = params.get(i);
            switch (facts.get(i)) {
                case Fact.Length(var elements) when param.type() instanceof Type.ArrayType -> {
                    for (var user : List.copyOf(param.users())) {
                        if (user.opcode() instanceof Opcode.ArrayLength) {
                            var constant = new IrBuilder(user.block().orElseThrow()).before(user).at(user.loc()).constInt(Type.I32, elements);
                            constant.setName(user.name());
                            user.replaceAllUsesWith(constant);
                            user.erase();
                            folded++;
                        }
                    }
                }
                case Fact.Scalar(var value) when param.hasUses() && (param.type().isInteger() || param.type().isFloat()) -> {
                    var entry = function.entry();
                    var b = new IrBuilder(entry);
                    entry.terminator().ifPresent(b::before);
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
