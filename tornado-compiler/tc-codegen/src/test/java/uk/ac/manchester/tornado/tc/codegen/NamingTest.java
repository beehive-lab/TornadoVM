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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/** Naming: one rule set, a reserved table per dialect. */
public class NamingTest {

    private static String name(Dialect d, String javaName) {
        return Naming.of(d).identifier(javaName, new HashSet<>());
    }

    @Test
    public void rulesApplyInEveryDialect() {
        for (var d : Dialect.values()) {
            assertEquals("a_b", name(d, "a$b"));
            assertEquals("v1x", name(d, "1x"));
            assertEquals("v_kernel_context", name(d, "_kernel_context"));
            assertEquals("max_", name(d, "max"));
            assertEquals("tc_x_", name(d, "tc_x"));
            assertEquals("float4_", name(d, "float4"));
            assertEquals("plain", name(d, "plain"));
        }
    }

    @Test
    public void dialectTablesDiffer() {
        assertEquals("and_", name(Dialect.CUDA, "and"));
        assertEquals("threadIdx_", name(Dialect.CUDA, "threadIdx"));
        assertEquals("and", name(Dialect.OPENCL, "and")); // OpenCL C is C, not C++
        assertEquals("kernel_", name(Dialect.OPENCL, "kernel"));
        assertEquals("get_global_id_", name(Dialect.OPENCL, "get_global_id"));
        assertEquals("convert_int_", name(Dialect.OPENCL, "convert_int"));
        assertEquals("device_", name(Dialect.METAL, "device"));
        assertEquals("simd_sum_", name(Dialect.METAL, "simd_sum"));
        assertEquals("and_", name(Dialect.METAL, "and"));
    }

    @Test
    public void duplicatesAreNumbered() {
        var used = new HashSet<String>();
        var n = Naming.of(Dialect.CUDA);
        assertEquals("x", n.identifier("x", used));
        assertEquals("x_1", n.identifier("x", used));
        assertEquals("max_", n.identifier("max", used));
        assertEquals("max__1", n.identifier("max_", used));
    }

    @Test
    public void helpersAreInAReservedNamespace() {
        for (var helper : Helpers.cudaNames()) {
            assertTrue(Naming.of(Dialect.CUDA).isReserved(helper), helper);
        }
    }
}
