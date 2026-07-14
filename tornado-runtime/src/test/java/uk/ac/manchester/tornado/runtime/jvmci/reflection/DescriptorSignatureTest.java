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

import org.junit.Test;

import jdk.vm.ci.meta.Signature;

/**
 * Pure-JVM (no-GPU) tests for {@link DescriptorSignature}: parse a raw JVM method descriptor
 * (the signature-polymorphic call-site path) into parameter/return types.
 */
public class DescriptorSignatureTest {

    private final ReflectionUniverse universe = new ReflectionUniverse();

    @Test
    public void parsesMixedDescriptor() {
        Signature signature = new DescriptorSignature(universe, getClass().getClassLoader(), "(IF[J)Ljava/lang/String;");
        assertEquals(3, signature.getParameterCount(false));
        assertEquals("I", signature.getParameterType(0, null).getName());
        assertEquals("F", signature.getParameterType(1, null).getName());
        assertEquals("[J", signature.getParameterType(2, null).getName());
        assertEquals("Ljava/lang/String;", signature.getReturnType(null).getName());
    }

    @Test
    public void parsesVoidNoArgs() {
        Signature signature = new DescriptorSignature(universe, getClass().getClassLoader(), "()V");
        assertEquals(0, signature.getParameterCount(false));
        assertEquals("V", signature.getReturnType(null).getName());
    }
}
