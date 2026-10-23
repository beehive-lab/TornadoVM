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

import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.CountedLoop;

/**
 * Marks the increments of counted loops that cannot overflow {@code nsw}. The emitter prints int and
 * long arithmetic with Java's wrap-around unless it is marked; a wrapping loop increment keeps the
 * device compiler from computing trip counts, unrolling and strength-reducing (euler: 9.5% slower).
 *
 * <p>A loop body runs only while {@code iv < bound} (javac's {@code if (iv >= bound) exit}, or an
 * {@code iv < bound} test that stays), so {@code iv + 1} is at most {@code bound <= MAX_VALUE}: no
 * overflow. A larger constant step {@code k} is safe when the bound is a constant at most
 * {@code MAX_VALUE - k + 1}; {@code iv <= last} likewise needs {@code last + k <= MAX_VALUE}.</p>
 */
public final class NoWrapInference implements Pass {

    @Override
    public String name() {
        return "no-wrap";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var loop : Analyses.loops(function).loops()) {
            var header = loop.header();
            for (int a = 0; a < header.args().size(); a++) {
                var counted = CountedLoop.of(loop, a);
                if (counted.isEmpty() || counted.get().increment().hasAttribute("nsw")) {
                    continue;
                }
                var c = counted.get();
                if (safe(c)) {
                    c.increment().addAttribute(Attribute.of("nsw"));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean safe(CountedLoop c) {
        var type = c.iv().type();
        if (!type.equals(Type.I32) && !type.equals(Type.I64) || c.exit().isEmpty() || c.exit().get().compare().operand(0) != c.iv()) {
            return false;
        }
        var step = c.constantStep();
        if (step.isEmpty() || step.getAsLong() < 1) {
            return false;
        }
        long k = step.getAsLong();
        long max = type.equals(Type.I32) ? Integer.MAX_VALUE : Long.MAX_VALUE;
        var exit = c.exit().get();
        // The predicate under which the body runs.
        var stays = exit.stay() == 0 ? exit.predicate() : negate(exit.predicate());
        var bound = c.constantBound();
        return switch (stays) {
            case SLT -> k == 1 || bound.isPresent() && bound.getAsLong() <= max - k + 1;
            case SLE -> bound.isPresent() && bound.getAsLong() <= max - k;
            default -> false;
        };
    }

    private static IntPredicate negate(IntPredicate p) {
        return switch (p) {
            case EQ -> IntPredicate.NE;
            case NE -> IntPredicate.EQ;
            case SLT -> IntPredicate.SGE;
            case SGE -> IntPredicate.SLT;
            case SLE -> IntPredicate.SGT;
            case SGT -> IntPredicate.SLE;
            case ULT -> IntPredicate.UGE;
            case UGE -> IntPredicate.ULT;
            case ULE -> IntPredicate.UGT;
            case UGT -> IntPredicate.ULE;
        };
    }
}
