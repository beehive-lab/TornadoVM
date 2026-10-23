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

    /** TC-4: a Java local named like a function the emitter prints must not hide it. */
    @Test
    public void builtinsTheEmitterPrintsAreReserved() {
        for (var d : new Dialect[] { Dialect.OPENCL, Dialect.METAL }) {
            assertEquals("sinpi_", name(d, "sinpi"));
            assertEquals("cospi_", name(d, "cospi"));
        }
        for (var f : new String[] { "sinpif", "cospif", "llmin", "llmax" }) {
            assertEquals(f + "_", name(Dialect.CUDA, f));
        }
        assertEquals("as_type_", name(Dialect.METAL, "as_type"));
    }

    /**
     * Every name the emitter sources print in call position ({@code "fmodf("}, {@code "f" + "("}) is
     * reserved in the dialects that print it, so a new spelling cannot reintroduce TC-4. The intrinsic
     * and helper tables are covered by construction (Naming reads them).
     */
    @Test
    public void namesPrintedByTheEmitterSourcesAreReserved() throws java.io.IOException {
        var dir = java.nio.file.Path.of("src/main/java/uk/ac/manchester/tornado/tc/codegen");
        // Words in messages, comments and attributes, not calls.
        var notCalls = java.util.Set.of("for", "if", "while", "switch", "inlined", "kernels", "lanes", "buffer", "threadgroup");
        var openclOnly = java.util.Set.of("as_uint", "as_ulong");
        var literal = java.util.regex.Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        var concatenated = java.util.regex.Pattern.compile("\"([A-Za-z][A-Za-z0-9_]*)\"\\s*\\+\\s*\"\\(");
        var files = java.util.Map.of("CEmitter.java", Dialect.values(), "CDialect.java", Dialect.values(), "CudaDialect.java", new Dialect[] { Dialect.CUDA }, "OpenClDialect.java",
                new Dialect[] { Dialect.OPENCL }, "MetalDialect.java", new Dialect[] { Dialect.METAL });
        int checked = 0;
        for (var entry : files.entrySet()) {
            var source = java.nio.file.Files.readString(dir.resolve(entry.getKey()));
            var names = new HashSet<String>();
            var m = literal.matcher(source);
            while (m.find()) {
                names.addAll(Naming.calledNames(m.group(1)));
            }
            var c = concatenated.matcher(source);
            while (c.find()) {
                names.add(c.group(1));
            }
            names.removeAll(notCalls);
            for (var n : names) {
                for (var d : entry.getValue()) {
                    if (openclOnly.contains(n) && d != Dialect.OPENCL) {
                        continue;
                    }
                    assertTrue(Naming.of(d).isReserved(n), entry.getKey() + " prints " + n + "( but " + d + " does not reserve it");
                    checked++;
                }
            }
        }
        assertTrue(checked > 10, "the scan found the emitter's spellings");
    }

    /** TC-10: entry names are C identifiers, and names that only differ in mangled characters stay apart. */
    @Test
    public void entryNamesAreIdentifiersAndDistinct() {
        assertEquals("VectorAdd_add", CEmitter.symbol("VectorAdd.add"));
        var nonAscii = CEmitter.symbol("Größe.kernelÄ");
        assertTrue(nonAscii.matches("[A-Za-z_][A-Za-z0-9_]*"), nonAscii);
        assertTrue(!CEmitter.symbol("A$B.m").equals(CEmitter.symbol("A_B.m")), CEmitter.symbol("A$B.m"));
        assertTrue(!CEmitter.symbol("Gr\u00f6\u00dfe.m").equals(CEmitter.symbol("Gr\u00f6\u00e9e.m")));
        assertEquals(CEmitter.symbol("A$B.m"), CEmitter.symbol("A$B.m"));
    }
}
