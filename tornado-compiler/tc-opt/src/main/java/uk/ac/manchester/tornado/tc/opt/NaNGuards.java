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

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;

/**
 * Guards comparisons of a value whose sign is known except for NaN against a value that is not a
 * constant: {@code a < b} becomes {@code (a < b) && ord(a, b)}, and an unordered comparison becomes
 * {@code (a <u b) || uno(a, b)}. Both are identities, since an ordered comparison is already false when
 * either operand is NaN.
 *
 * <p>The Canonicalizer decides such comparisons when the other side is a constant. When it is an
 * expression the device compiler evaluates itself, such as {@code -(float) log(3.4e38)}, NVIDIA's
 * compilers fold {@code -88.7 < fabsf(x)} to true and lose the NaN case (tornado-fuzz seed 301998).
 * The guard keeps the NaN test outside anything they fold. Run once, late: the guarded comparison
 * would match again.</p>
 */
public final class NaNGuards implements Pass {

    @Override
    public String name() {
        return "nan-guards";
    }

    /** Whether the function has a comparison to guard (the pipeline skips the step otherwise). */
    public static boolean applies(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> guarded(i) != null);
    }

    /** The sign-known operand of a comparison to guard, or null. */
    private static Value guarded(uk.ac.manchester.tornado.tc.ir.Inst cmp) {
        if (!(cmp.opcode() instanceof Opcode.FCmp(var p)) || ordered(p) == null) {
            return null;
        }
        var a = cmp.operand(0);
        var b = cmp.operand(1);
        return knownSign(a) && !isConstant(b) ? a : knownSign(b) && !isConstant(a) ? b : null;
    }

    /** TRUE for ordered relations, FALSE for unordered ones; null for equalities, ord and uno. */
    private static Boolean ordered(FloatPredicate p) {
        return switch (p) {
            case OGT, OGE, OLT, OLE -> Boolean.TRUE;
            case UGT, UGE, ULT, ULE -> Boolean.FALSE;
            default -> null;
        };
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            for (var cmp : List.copyOf(block.insts())) {
                if (cmp.block().isEmpty()) {
                    continue;
                }
                Value known = guarded(cmp);
                if (known == null) {
                    continue;
                }
                var p = ((Opcode.FCmp) cmp.opcode()).predicate();
                boolean ordered = ordered(p);
                var a = cmp.operand(0);
                var b = cmp.operand(1);
                var builder = IrBuilder.insertingBefore(cmp);
                var fresh = builder.fcmp(p, a, b);
                // ord/uno of both operands: either may be the NaN (both sides can be sign-known)
                var test = builder.fcmp(ordered ? FloatPredicate.ORD : FloatPredicate.UNO, a, b);
                var guarded = builder.binary(ordered ? BinaryOp.AND : BinaryOp.OR, fresh, test);
                cmp.replaceAllUsesWith(guarded);
                cmp.erase();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean knownSign(Value v) {
        return !isConstant(v) && Canonicalizer.sign(v, 0) != Canonicalizer.Sign.UNKNOWN;
    }

    private static boolean isConstant(Value v) {
        return Constants.floating(v).isPresent();
    }
}
