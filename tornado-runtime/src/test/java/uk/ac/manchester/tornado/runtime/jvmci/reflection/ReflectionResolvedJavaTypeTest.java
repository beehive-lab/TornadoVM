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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Pure-JVM (no-GPU) tests for {@link ReflectionResolvedJavaType}: descriptor naming, array/component,
 * instance-field enumeration with {@code Unsafe} offsets, and field identity canonicalization (the
 * behaviour locked in by routing {@code getInstanceFields} through {@code universe.lookupField}).
 */
public class ReflectionResolvedJavaTypeTest {

    private final ReflectionUniverse universe = new ReflectionUniverse();

    @Test
    public void descriptorNameForm() {
        ReflectionResolvedJavaType type = universe.lookupType(ReflectionTestSample.class);
        assertEquals("L" + ReflectionTestSample.class.getName().replace('.', '/') + ";", type.getName());
    }

    @Test
    public void typeCanonicalization() {
        assertSame(universe.lookupType(ReflectionTestSample.class), universe.lookupType(ReflectionTestSample.class));
    }

    @Test
    public void arrayAndComponent() {
        ReflectionResolvedJavaType arrayType = universe.lookupType(int[].class);
        assertTrue(arrayType.isArray());
        assertEquals("[I", arrayType.getName());
        assertEquals(JavaKind.Int, arrayType.getComponentType().getJavaKind());
        assertSame(universe.lookupType(int.class), arrayType.getComponentType());
    }

    @Test
    public void instanceFieldsWithUnsafeOffsets() throws Exception {
        ReflectionResolvedJavaType type = universe.lookupType(ReflectionTestSample.class);
        ResolvedJavaField[] fields = type.getInstanceFields(false);
        // alpha (int) + beta (long); static staticCounter must be excluded.
        assertEquals(2, fields.length);

        long previousOffset = -1;
        for (ResolvedJavaField field : fields) {
            Field reflected = ReflectionTestSample.class.getDeclaredField(field.getName());
            assertEquals("offset must match Unsafe", ReflectionUniverse.UNSAFE.objectFieldOffset(reflected), field.getOffset());
            assertTrue("instance fields must be sorted by offset", field.getOffset() > previousOffset);
            previousOffset = field.getOffset();
        }
    }

    @Test
    public void instanceFieldsAreCachedAndCanonical() throws Exception {
        ReflectionResolvedJavaType type = universe.lookupType(ReflectionTestSample.class);
        assertSame("getInstanceFields must be memoized", type.getInstanceFields(false), type.getInstanceFields(false));

        Field alpha = ReflectionTestSample.class.getDeclaredField("alpha");
        ResolvedJavaField canonical = universe.lookupField(alpha);
        ResolvedJavaField fromType = null;
        for (ResolvedJavaField f : type.getInstanceFields(false)) {
            if (f.getName().equals("alpha")) {
                fromType = f;
            }
        }
        assertNotNull(fromType);
        // Field identity canonicalization: the enumerated field is the SAME instance as lookupField's.
        assertSame(canonical, fromType);
    }

    @Test
    public void staticFieldsSeparated() {
        ReflectionResolvedJavaType type = universe.lookupType(ReflectionTestSample.class);
        ResolvedJavaField[] staticFields = type.getStaticFields();
        boolean found = false;
        for (ResolvedJavaField f : staticFields) {
            if (f.getName().equals("staticCounter")) {
                found = true;
            }
        }
        assertTrue("staticCounter must appear among static fields", found);
    }

    @Test
    public void findInstanceFieldWithOffset() throws Exception {
        ReflectionResolvedJavaType type = universe.lookupType(ReflectionTestSample.class);
        Field beta = ReflectionTestSample.class.getDeclaredField("beta");
        long offset = ReflectionUniverse.UNSAFE.objectFieldOffset(beta);
        ResolvedJavaField field = type.findInstanceFieldWithOffset(offset, JavaKind.Long);
        assertNotNull(field);
        assertEquals("beta", field.getName());
    }
}
