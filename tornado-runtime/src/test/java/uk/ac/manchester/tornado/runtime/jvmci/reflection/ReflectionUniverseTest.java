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
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Pure-JVM (no-GPU) tests for {@link ReflectionUniverse}'s shared helpers: JVM type descriptors
 * (both directions), classfile-byte caching, and the canonicalization maps that keep type/method
 * identity stable across lookups — the property Graal's maps and {@code InvocationPlugins}
 * depend on.
 */
public class ReflectionUniverseTest {

    private final ReflectionUniverse universe = new ReflectionUniverse();

    @Test
    public void descriptorForms() {
        assertEquals("I", ReflectionUniverse.descriptor(int.class));
        assertEquals("J", ReflectionUniverse.descriptor(long.class));
        assertEquals("V", ReflectionUniverse.descriptor(void.class));
        assertEquals("Ljava/lang/Object;", ReflectionUniverse.descriptor(Object.class));
        assertEquals("[I", ReflectionUniverse.descriptor(int[].class));
        assertEquals("[[D", ReflectionUniverse.descriptor(double[][].class));
        assertEquals("[Ljava/lang/String;", ReflectionUniverse.descriptor(String[].class));
    }

    @Test
    public void classForDescriptorRoundTrips() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Class<?>[] samples = { boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class, void.class, Object.class, String[].class, int[].class,
                double[][].class };
        for (Class<?> c : samples) {
            assertSame(c, ReflectionUniverse.classForDescriptor(ReflectionUniverse.descriptor(c), loader));
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void unknownDescriptorThrows() throws Exception {
        ReflectionUniverse.classForDescriptor("Q", getClass().getClassLoader());
    }

    @Test
    public void typeLookupIsCanonicalAndNullSafe() {
        assertSame(universe.lookupType(String.class), universe.lookupType(String.class));
        assertNotSame(universe.lookupType(String.class), universe.lookupType(Object.class));
        assertNull(universe.lookupType(null));
        // Two universes are independent registries (each backend factory owns one).
        assertNotSame(universe.lookupType(String.class), new ReflectionUniverse().lookupType(String.class));
    }

    @Test
    public void classfileBytesAreRealAndCached() {
        byte[] first = universe.classfileBytes(ReflectionTestSample.class);
        // A real classfile: 0xCAFEBABE magic.
        assertEquals((byte) 0xCA, first[0]);
        assertEquals((byte) 0xFE, first[1]);
        assertEquals((byte) 0xBA, first[2]);
        assertEquals((byte) 0xBE, first[3]);
        // One read per declaring class — the cache must return the same array, not re-read.
        assertSame(first, universe.classfileBytes(ReflectionTestSample.class));
    }

    @Test
    public void polymorphicMethodsCanonicalizeOnDescriptor() throws Exception {
        Method invokeExact = (Method) ReflectionMembers.findPolymorphic(java.lang.invoke.MethodHandle.class, "invokeExact");
        ReflectionResolvedJavaMethod intShape = universe.lookupPolymorphicMethod(invokeExact, "(I)I");
        ReflectionResolvedJavaMethod intShapeAgain = universe.lookupPolymorphicMethod(invokeExact, "(I)I");
        ReflectionResolvedJavaMethod longShape = universe.lookupPolymorphicMethod(invokeExact, "(J)J");
        // Same (method, call-site descriptor) -> same instance; different descriptor -> different
        // instance (each call-site shape is its own InvocationPlugins key).
        assertSame(intShape, intShapeAgain);
        assertNotSame(intShape, longShape);
        // The signature reflects the call-site descriptor, not the declared (Object...) shape.
        assertEquals("(I)I", intShape.getSignature().toMethodDescriptor());
    }
}
