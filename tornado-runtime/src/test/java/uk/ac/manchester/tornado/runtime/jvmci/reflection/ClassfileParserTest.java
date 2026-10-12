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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

/**
 * Pure-JVM (no-GPU) tests for {@link ClassfileParser}: recover the {@code Code} attribute and field
 * metadata from a real compiled classfile read via {@code getResourceAsStream}.
 */
public class ClassfileParserTest {

    private static byte[] sampleClassfile() throws Exception {
        String resource = ReflectionTestSample.class.getName().replace('.', '/') + ".class";
        try (InputStream in = ReflectionTestSample.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "sample classfile must be on the test classpath");
            return in.readAllBytes();
        }
    }

    @Test
    public void recoversCodeAttribute() throws Exception {
        ClassfileParser.MethodCode code = ClassfileParser.parse(sampleClassfile(), "compute", "(I[I)J");
        assertNotNull(code);
        assertTrue(code.code().length > 0, "compute must have bytecode");
        assertTrue(code.maxLocals() > 0, "compute must reserve locals");
        assertTrue(code.maxStack() > 0, "compute must reserve stack");
    }

    @Test
    public void findsFieldInfo() throws Exception {
        byte[] bytes = sampleClassfile();
        ClassfileParser.FieldInfo alpha = ClassfileParser.findFieldInfo(bytes, "alpha");
        assertNotNull(alpha);
        assertEquals("I", alpha.descriptor());

        ClassfileParser.FieldInfo beta = ClassfileParser.findFieldInfo(bytes, "beta");
        assertNotNull(beta);
        assertEquals("J", beta.descriptor());
    }

    @Test
    public void missingFieldReturnsNull() throws Exception {
        assertNull(ClassfileParser.findFieldInfo(sampleClassfile(), "doesNotExist"));
    }
}
