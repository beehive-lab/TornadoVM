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
 */
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The FdLibm bodies TC translates for {@code StrictMath} functions a JDK declares {@code native}:
 * OpenJ9 keeps sixteen of them native, OpenJDK none. Every JDK this builds for ships the FdLibm
 * classes, so the lookup is checked against the running one, whichever VM it is.
 */
public class StrictMathBodyTest {

    private static final Frontend.BytecodeSource JDK = Frontend.BytecodeSource::jrt;

    /** The functions OpenJ9's StrictMath declares native, with their FdLibm class. */
    private static final List<String[]> NATIVE_ON_OPENJ9 = List.of( //
            new String[] { "sin", "(D)D", "Sin" }, new String[] { "cos", "(D)D", "Cos" }, new String[] { "tan", "(D)D", "Tan" }, //
            new String[] { "asin", "(D)D", "Asin" }, new String[] { "acos", "(D)D", "Acos" }, new String[] { "atan", "(D)D", "Atan" }, //
            new String[] { "log", "(D)D", "Log" }, new String[] { "log10", "(D)D", "Log10" }, new String[] { "sqrt", "(D)D", "Sqrt" }, //
            new String[] { "IEEEremainder", "(DD)D", "IEEEremainder" }, new String[] { "atan2", "(DD)D", "Atan2" }, //
            new String[] { "sinh", "(D)D", "Sinh" }, new String[] { "cosh", "(D)D", "Cosh" }, new String[] { "tanh", "(D)D", "Tanh" }, //
            new String[] { "expm1", "(D)D", "Expm1" }, new String[] { "log1p", "(D)D", "Log1p" });

    @Test
    public void everyFunctionOpenJ9KeepsNativeHasAnFdLibmBody() {
        for (var f : NATIVE_ON_OPENJ9) {
            var body = Frontend.strictMathBody(JDK, "java/lang/StrictMath", f[0], f[1]);
            assertTrue(body.isPresent(), "no FdLibm body for StrictMath." + f[0]);
            assertEquals("java/lang/FdLibm$" + f[2], body.get().getKey().name());
            assertEquals("compute", body.get().getValue().name());
            assertEquals(f[1], body.get().getValue().descriptor());
            assertTrue(body.get().getValue().code().isPresent());
        }
    }

    @Test
    public void onlyStrictMathIsRedirected() {
        assertTrue(Frontend.strictMathBody(JDK, "java/lang/Math", "sin", "(D)D").isEmpty());
        assertTrue(Frontend.strictMathBody(JDK, "java/lang/StrictMath", "noSuchFunction", "(D)D").isEmpty());
        // FdLibm.Sin.compute takes a double: a float overload has no body to take.
        assertTrue(Frontend.strictMathBody(JDK, "java/lang/StrictMath", "sin", "(F)F").isEmpty());
    }
}
