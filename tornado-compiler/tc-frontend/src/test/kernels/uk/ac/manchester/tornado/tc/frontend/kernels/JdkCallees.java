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
