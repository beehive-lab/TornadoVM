/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.Test;

import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.memory.NativeArrayLayouts;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Keeps the shared native-array layout table honest. The backends used to answer "how wide is an
 * element of this array type" with an {@code instanceof} chain each, and the chains drifted; the
 * table replaced them, so the table is what has to be pinned.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestNativeArrayLayouts
 * </code>
 */
public class TestNativeArrayLayouts extends TornadoTestBase {

    /** Every sealed subtype of {@link TornadoNativeArray} has an entry: no type can fall through. */
    @Test
    public void testEveryNativeArrayTypeIsRegistered() {
        Set<Class<?>> registered = new HashSet<>();
        NativeArrayLayouts.registeredTypes().forEach(registered::add);

        Class<?>[] permitted = TornadoNativeArray.class.getPermittedSubclasses();
        assertTrue("TornadoNativeArray should be sealed", permitted != null && permitted.length > 0);

        for (Class<?> subtype : permitted) {
            if (!registered.contains(subtype)) {
                fail(subtype.getSimpleName() + " has no entry in NativeArrayLayouts, so a backend would "
                        + "hand it to a field buffer and copy the wrong bytes. Add it to the table.");
            }
        }
    }

    /**
     * The table agrees with each type's own {@code getElementSize()} — with one deliberate exception
     * that is asserted rather than hidden.
     */
    @Test
    public void testTableAgreesWithTheApiTypes() throws ReflectiveOperationException {
        for (Class<?> type : NativeArrayLayouts.registeredTypes()) {
            TornadoNativeArray instance = (TornadoNativeArray) type.getConstructor(int.class).newInstance(4);
            OptionalInt tableSize = NativeArrayLayouts.elementSizeOf(instance);
            assertTrue(type.getSimpleName() + " must be in the table", tableSize.isPresent());

            if (type == CharArray.class) {
                // Pre-existing disagreement, pinned on purpose: the API type reports 2 bytes, every
                // backend has always sized the buffer with its 1-byte character kind. If either side
                // changes, this assertion is where the decision gets made.
                assertEquals("CharArray.getElementSize() is expected to disagree with the table", 2, instance.getElementSize());
                assertEquals("backends size a CharArray element as one byte", 1, tableSize.getAsInt());
            } else {
                assertEquals(type.getSimpleName() + ": table width must match the type", instance.getElementSize(), tableSize.getAsInt());
            }
        }
    }

    /** A plain object is not a native array and must not be given a width. */
    @Test
    public void testUnknownObjectHasNoLayout() {
        assertTrue(NativeArrayLayouts.elementSizeOf(new Object()).isEmpty());
        assertTrue(NativeArrayLayouts.elementSizeOf(null).isEmpty());
    }
}
