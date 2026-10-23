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
