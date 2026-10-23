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
package uk.ac.manchester.tornado.tc.ir.analysis;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Value;

/** Constant values, asked one way everywhere. */
public final class Constants {

    private Constants() {
    }

    /** The value of an integer constant. */
    public static OptionalLong integer(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.ConstInt(var c) ? OptionalLong.of(c) : OptionalLong.empty();
    }

    /** The value of a floating-point constant. */
    public static OptionalDouble floating(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.ConstFloat(var c) ? OptionalDouble.of(c) : OptionalDouble.empty();
    }

    /** The value of an integer or floating-point constant: {@link Long} or {@link Double}. */
    public static Optional<Number> number(Value v) {
        var i = integer(v);
        if (i.isPresent()) {
            return Optional.of(i.getAsLong());
        }
        var f = floating(v);
        return f.isPresent() ? Optional.of(f.getAsDouble()) : Optional.empty();
    }

    /** Whether {@code v} is the integer constant {@code value}. */
    public static boolean isInteger(Value v, long value) {
        var i = integer(v);
        return i.isPresent() && i.getAsLong() == value;
    }
}
