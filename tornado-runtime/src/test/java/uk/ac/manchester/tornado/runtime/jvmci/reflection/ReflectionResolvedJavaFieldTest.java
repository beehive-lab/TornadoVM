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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Pure-JVM (no-GPU) tests for {@link ReflectionResolvedJavaField}: name/type/modifiers, the
 * {@code Unsafe}-sourced memory offset the object marshallers ({@code OCLFieldBuffer},
 * {@code MetalFieldBuffer}) and the kernel field-access lowering both rely on, RUNTIME annotation
 * visibility (the {@code @SegmentElementSize} guard in {@code TornadoNativeTypeElimination} reads
 * annotations off the declaring class), and universe canonicalization.
 */
public class ReflectionResolvedJavaFieldTest {

    private final ReflectionUniverse universe = new ReflectionUniverse();

    private ResolvedJavaField lookup(Class<?> holder, String name) throws NoSuchFieldException {
        return universe.lookupField(holder.getDeclaredField(name));
    }

    @Test
    public void nameTypeAndModifiers() throws Exception {
        ResolvedJavaField gamma = lookup(ReflectionFieldSample.class, "gamma");
        assertEquals("gamma", gamma.getName());
        assertEquals("D", gamma.getType().getName());
        assertFalse(Modifier.isStatic(gamma.getModifiers()));
        assertEquals(ReflectionFieldSample.class.getName(), gamma.getDeclaringClass().toJavaName());
        assertFalse(gamma.isInternal());
        assertFalse(gamma.isSynthetic());
    }

    @Test
    public void instanceOffsetMatchesUnsafeAndIsPlausible() throws Exception {
        ResolvedJavaField gamma = lookup(ReflectionFieldSample.class, "gamma");
        Field reflected = ReflectionFieldSample.class.getDeclaredField("gamma");
        // The offset IS the Unsafe field offset — the host marshaller writes at the same
        // displacement the kernel's field lowering emits, so these must agree exactly.
        assertEquals(ReflectionUniverse.UNSAFE.objectFieldOffset(reflected), gamma.getOffset());
        // And it must be past the object header (never 0) for an instance field.
        assertTrue("instance field offset must be positive", gamma.getOffset() > 0);
    }

    @Test
    public void staticOffsetUsesStaticBase() throws Exception {
        ResolvedJavaField staticTotal = lookup(ReflectionFieldSample.class, "staticTotal");
        Field reflected = ReflectionFieldSample.class.getDeclaredField("staticTotal");
        assertTrue(Modifier.isStatic(staticTotal.getModifiers()));
        assertEquals(ReflectionUniverse.UNSAFE.staticFieldOffset(reflected), staticTotal.getOffset());
    }

    @Test
    public void distinctFieldsHaveDistinctOffsets() throws Exception {
        ResolvedJavaField gamma = lookup(ReflectionFieldSample.class, "gamma");
        ResolvedJavaField baseValue = lookup(ReflectionFieldSampleBase.class, "baseValue");
        assertNotEquals(gamma.getOffset(), baseValue.getOffset());
    }

    @Test
    public void runtimeAnnotationsAreVisible() throws Exception {
        ResolvedJavaField gamma = lookup(ReflectionFieldSample.class, "gamma");
        ReflectionFieldSample.Marker marker = gamma.getAnnotation(ReflectionFieldSample.Marker.class);
        assertEquals("payload", marker.value());
        assertEquals(1, gamma.getDeclaredAnnotations().length);
        // No annotation -> null, not an exception.
        assertEquals(null, lookup(ReflectionFieldSampleBase.class, "baseValue").getAnnotation(ReflectionFieldSample.Marker.class));
    }

    @Test
    public void canonicalizationAndEquality() throws Exception {
        ResolvedJavaField first = lookup(ReflectionFieldSample.class, "gamma");
        ResolvedJavaField second = lookup(ReflectionFieldSample.class, "gamma");
        // Same Field must resolve to the SAME instance (Graal maps key on ResolvedJavaField).
        assertSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, lookup(ReflectionFieldSampleBase.class, "baseValue"));
    }

    @Test
    public void toStringNamesTheField() throws Exception {
        String s = lookup(ReflectionFieldSample.class, "gamma").toString();
        assertTrue(s, s.startsWith("ReflectionField<"));
        assertTrue(s, s.contains("gamma"));
    }
}
