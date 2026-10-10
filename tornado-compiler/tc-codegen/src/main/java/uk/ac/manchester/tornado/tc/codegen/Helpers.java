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
package uk.ac.manchester.tornado.tc.codegen;

import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * Small device functions that give Java semantics where the target's built-ins differ. The emitter
 * adds a helper to the kernel preamble only when the kernel uses it, so simple kernels stay free of
 * boilerplate. Each helper is written from the Java Language Specification.
 */
final class Helpers {

    private Helpers() {
    }

    // Math.min/max (JLS, java.lang.Math): NaN if either argument is NaN; -0.0 is smaller than +0.0.
    private static final Map<String, String> CUDA = Map.of(
            "java_fminf", """
                    __device__ static inline float java_fminf(float a, float b) {
                      if (a != a || b != b) return __int_as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return __int_as_float(__float_as_int(a) | __float_as_int(b));
                      return a < b ? a : b;
                    }
                    """,
            "java_fmaxf", """
                    __device__ static inline float java_fmaxf(float a, float b) {
                      if (a != a || b != b) return __int_as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return __int_as_float(__float_as_int(a) & __float_as_int(b));
                      return a > b ? a : b;
                    }
                    """,
            "java_fmin", """
                    __device__ static inline double java_fmin(double a, double b) {
                      if (a != a || b != b) return __longlong_as_double(0x7ff8000000000000LL);
                      if (a == 0.0 && b == 0.0) return __longlong_as_double(__double_as_longlong(a) | __double_as_longlong(b));
                      return a < b ? a : b;
                    }
                    """,
            "tc_warp_sum", """
                    __device__ static inline float tc_warp_sum(float v) {
                      for (int lane = 16; lane > 0; lane >>= 1) v += __shfl_xor_sync(0xffffffffu, v, lane);
                      return v;
                    }
                    """,
            "java_fmax", """
                    __device__ static inline double java_fmax(double a, double b) {
                      if (a != a || b != b) return __longlong_as_double(0x7ff8000000000000LL);
                      if (a == 0.0 && b == 0.0) return __longlong_as_double(__double_as_longlong(a) & __double_as_longlong(b));
                      return a > b ? a : b;
                    }
                    """);

    static String source(String helper, Dialect dialect) {
        var text = switch (dialect) {
            case CUDA -> CUDA.get(helper);
            case OPENCL, METAL -> null;
        };
        if (text == null) {
            throw new IllegalArgumentException("no " + dialect + " definition for helper " + helper);
        }
        return text;
    }
}
