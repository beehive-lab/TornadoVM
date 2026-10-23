/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
