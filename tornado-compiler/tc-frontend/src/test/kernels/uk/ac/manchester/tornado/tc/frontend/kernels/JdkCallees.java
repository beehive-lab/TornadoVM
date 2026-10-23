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
package uk.ac.manchester.tornado.tc.frontend.kernels;

/** Kernels that call JDK methods: on a class path the JDK's classes come from the jrt:/ image (TC-7). */
public final class JdkCallees {

    private JdkCallees() {
    }

    public static int isNaN(float x) {
        return Float.isNaN(x) ? 1 : 0;
    }

    public static int floatCompare(float a, float b) {
        return Float.compare(a, b);
    }

    public static int doubleCompare(double a, double b) {
        return Double.compare(a, b);
    }

    public static int intCompare(int a, int b) {
        return Integer.compare(a, b);
    }

    public static int floatToIntBits(float x) {
        return Float.floatToIntBits(x);
    }

    public static long roundDouble(double x) {
        return Math.round(x);
    }

    public static float copySign(float a, float b) {
        return Math.copySign(a, b);
    }

    public static float signum(float x) {
        return Math.signum(x);
    }

    public static int getExponent(double x) {
        return Math.getExponent(x);
    }
}
