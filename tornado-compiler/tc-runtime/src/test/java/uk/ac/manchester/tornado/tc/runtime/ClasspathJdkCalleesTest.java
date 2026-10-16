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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * TC-7: compiling from a class path (the {@code tc} tool) resolves JDK methods (Float.isNaN,
 * Math.round, ...) from the running JDK's jrt:/ image when the class path has no such class.
 */
public class ClasspathJdkCalleesTest {

    @ParameterizedTest
    @ValueSource(strings = { "isNaN", "floatCompare", "doubleCompare", "intCompare", "floatToIntBits", "roundDouble", "copySign", "signum", "getExponent" })
    public void jdkCalleesResolveFromAClassPath(String method) {
        var source = Frontend.BytecodeSource.classpath("../tc-frontend/target/test-classes");
        var c = TcCompiler.compileJava(source, "uk.ac.manchester.tornado.tc.frontend.kernels.JdkCallees", method, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
        assertTrue(c.succeeded(), method + ": " + c.diagnostics());
    }
}
