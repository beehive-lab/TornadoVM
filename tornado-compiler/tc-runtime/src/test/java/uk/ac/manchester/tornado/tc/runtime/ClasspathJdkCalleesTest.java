/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
