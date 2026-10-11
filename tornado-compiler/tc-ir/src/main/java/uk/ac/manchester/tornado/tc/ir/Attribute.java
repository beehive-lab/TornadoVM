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
package uk.ac.manchester.tornado.tc.ir;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named annotation on an instruction, block or function, with typed values written like an
 * instruction's immediates: {@code [parallel<iv = 0>]}, {@code [contract]}. Every attribute is declared
 * in {@link #DEFS} with where it may appear and the values it needs; the verifier checks both
 * (TC-E2118).
 */
public record Attribute(String name, Immediates values) {

    /** Where an attribute may appear. */
    public enum Scope {
        FUNCTION, BLOCK, INST
    }

    /** A declared attribute: its scope, required values and meaning. */
    public record Def(String name, Scope scope, List<String> values, String description) {
    }

    private static final Map<String, Def> DEFS = Map.ofEntries(
            def("parallel", Scope.BLOCK, "a @Parallel loop header; iv is the index of its induction-variable block argument", "iv"),
            def("grid", Scope.BLOCK, "a parallel loop lowered to a grid-stride loop over dimension dim", "dim"),
            def("domain", Scope.FUNCTION, "the launch domain of one dimension: offset, step and exclusive upper bound", "dim", "offset", "step", "upper"),
            def("atomic_slot", Scope.FUNCTION, "an AtomicInteger slot of the atomics region and its initial value", "slot", "init"),
            def("atomic_param", Scope.FUNCTION, "an AtomicInteger parameter and the slot it uses", "param", "slot"),
            def("contract", Scope.INST, "a floating-point op that may be contracted (FMA)"),
            def("nsw", Scope.INST, "integer arithmetic TC generated that cannot overflow (printed as plain signed C)"));

    private static Map.Entry<String, Def> def(String name, Scope scope, String description, String... values) {
        return Map.entry(name, new Def(name, scope, List.of(values), description));
    }

    public static Optional<Def> lookup(String name) {
        return Optional.ofNullable(DEFS.get(name));
    }

    public static Attribute of(String name) {
        return new Attribute(name, Immediates.NONE);
    }

    /** {@code of("domain", "dim", 0, "offset", 0, "step", 1, "upper", 128)}. */
    public static Attribute of(String name, Object... keysAndValues) {
        return new Attribute(name, Immediates.of(keysAndValues));
    }

    public int integer(String key) {
        return values.requireInt(key);
    }

    public long longValue(String key) {
        return values.integer(key).orElseThrow(() -> new IllegalStateException("attribute " + name + " has no integer '" + key + "'"));
    }

    public String text() {
        return name + values.text();
    }
}
