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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Pure-JVM (no-GPU) tests for {@link ReflectionMembers}, the constant-pool-descriptor to
 * reflective-member resolver: it must follow the JVM resolution rules (declared, then up the
 * superclass chain, then interfaces) because the {@link ReflectionConstantPool} feeds it raw
 * {@code CONSTANT_Methodref}/{@code Fieldref} holder + name + descriptor triples during graph
 * building — a wrong walk order or a miss surfaces as a sketch-time unresolved invoke.
 */
public class ReflectionMembersTest {

    private static final ClassLoader LOADER = ReflectionMembersTest.class.getClassLoader();

    @Test
    public void declaredMethodByDescriptor() {
        Executable m = ReflectionMembers.findMethod(ReflectionTestSample.class, "compute", "(I[I)J", LOADER);
        assertTrue(m instanceof Method);
        assertEquals("compute", m.getName());
        assertEquals(ReflectionTestSample.class, m.getDeclaringClass());
    }

    @Test
    public void inheritedMethodResolvesThroughSuperclass() {
        // scale(int) is declared on the Base class but resolved against the subclass holder.
        Executable m = ReflectionMembers.findMethod(ReflectionFieldSample.class, "scale", "(I)I", LOADER);
        assertTrue(m instanceof Method);
        assertEquals(ReflectionFieldSampleBase.class, m.getDeclaringClass());
    }

    @Test
    public void interfaceDefaultMethodResolvesAfterSuperclassChain() {
        Executable m = ReflectionMembers.findMethod(ReflectionFieldSample.class, "op", "(I)I", LOADER);
        assertTrue(m instanceof Method);
        assertEquals(ReflectionFieldSampleOps.class, m.getDeclaringClass());
        assertTrue(((Method) m).isDefault());
    }

    @Test
    public void constructorByInitName() {
        Executable ctor = ReflectionMembers.findMethod(ReflectionFieldSample.class, "<init>", "(I)V", LOADER);
        assertTrue(ctor instanceof Constructor);
        assertEquals(ReflectionFieldSample.class, ctor.getDeclaringClass());
    }

    @Test
    public void missReturnsNullNotThrow() {
        assertNull(ReflectionMembers.findMethod(ReflectionTestSample.class, "noSuchMethod", "()V", LOADER));
        // Same name, wrong descriptor is also a miss.
        assertNull(ReflectionMembers.findMethod(ReflectionTestSample.class, "compute", "()J", LOADER));
        assertNull(ReflectionMembers.findField(ReflectionTestSample.class, "noSuchField"));
    }

    @Test
    public void resolutionIsCachedAndStable() {
        Executable first = ReflectionMembers.findMethod(ReflectionTestSample.class, "compute", "(I[I)J", LOADER);
        Executable second = ReflectionMembers.findMethod(ReflectionTestSample.class, "compute", "(I[I)J", LOADER);
        // The cache must hand back the same Executable so universe canonicalization holds upstream.
        assertSame(first, second);
    }

    @Test
    public void fieldResolvesDeclaredAndInherited() {
        Field declared = ReflectionMembers.findField(ReflectionFieldSample.class, "gamma");
        assertEquals(ReflectionFieldSample.class, declared.getDeclaringClass());

        // baseValue is declared on the Base class but resolved against the subclass holder.
        Field inherited = ReflectionMembers.findField(ReflectionFieldSample.class, "baseValue");
        assertEquals(ReflectionFieldSampleBase.class, inherited.getDeclaringClass());
    }

    @Test
    public void signaturePolymorphicByNameOnly() {
        // MethodHandle.invokeExact is the canonical signature-polymorphic member: declared
        // (Object...) but matched by name, the call-site descriptor comes from the constant pool.
        Executable m = ReflectionMembers.findPolymorphic(MethodHandle.class, "invokeExact");
        assertTrue(m instanceof Method);
        assertEquals("invokeExact", m.getName());
        assertEquals(MethodHandle.class, m.getDeclaringClass());
    }
}
