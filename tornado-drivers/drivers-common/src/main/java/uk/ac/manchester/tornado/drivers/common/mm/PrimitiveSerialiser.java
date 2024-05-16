/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.common.mm;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PrimitiveSerialiser {

    private static void align(ByteBuffer buffer, int align) {
        while (buffer.position() % align != 0) {
            buffer.put((byte) 0);
        }
    }

    public static void put(ByteBuffer buffer, Object value) {
        put(buffer, value, 0);
    }

    public static void put(ByteBuffer buffer, Object value, int alignment) {
        switch (value) {
            case Byte byteValue -> buffer.put(byteValue);
            case Character charValue -> buffer.putChar(charValue);
            case Short shortValue -> buffer.putShort(shortValue);
            case HalfFloat halfFloat -> buffer.putShort(halfFloat.getHalfFloatValue());
            case Integer intValue -> buffer.putInt(intValue);
            case Float floatValue -> buffer.putFloat(floatValue);
            case Long longValue -> buffer.putLong(longValue);
            case Double doubleValue -> buffer.putDouble(doubleValue);
            case null, default -> new TornadoLogger().warn("unable to serialise: %s (%s)", value, value.getClass().getName());
        }

        if (alignment != 0) {
            align(buffer, alignment);
        }
    }
}
