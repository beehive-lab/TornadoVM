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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Typed compile-time parameters of an instruction (MLIR calls them properties): the lane of a
 * vector read, a launch mode, a callee, an element type, an address space. They are part of what an
 * instruction means (value numbering compares them) and print as {@code <lane = 3, mode = "tail">}
 * after the mnemonic. Values are integers or strings; keys are sorted, so equal sets print equally.
 */
public record Immediates(Map<String, Object> values) {

    public static final Immediates NONE = new Immediates(Map.of());

    public Immediates {
        var sorted = new TreeMap<String, Object>();
        for (var e : values.entrySet()) {
            var v = e.getValue();
            if (v instanceof Integer i) {
                v = (long) i;
            }
            if (!(v instanceof Long || v instanceof String)) {
                throw new IllegalArgumentException("immediate " + e.getKey() + " must be an integer or a string: " + v);
            }
            sorted.put(e.getKey(), v);
        }
        values = Collections.unmodifiableMap(sorted);
    }

    /** {@code of("lane", 3, "mode", "tail")}. */
    public static Immediates of(Object... keysAndValues) {
        var map = new TreeMap<String, Object>();
        for (int k = 0; k + 1 < keysAndValues.length; k += 2) {
            map.put((String) keysAndValues[k], keysAndValues[k + 1]);
        }
        return new Immediates(map);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Optional<Long> integer(String key) {
        return values.get(key) instanceof Long l ? Optional.of(l) : Optional.empty();
    }

    public Optional<String> string(String key) {
        return values.get(key) instanceof String s ? Optional.of(s) : Optional.empty();
    }

    /** An integer immediate that must be present (a malformed instruction is a compiler bug). */
    public int requireInt(String key) {
        return integer(key).map(Long::intValue).orElseThrow(() -> new IllegalStateException("missing integer immediate '" + key + "' in " + text()));
    }

    /** A string immediate that must be present. */
    public String requireString(String key) {
        return string(key).orElseThrow(() -> new IllegalStateException("missing string immediate '" + key + "' in " + text()));
    }

    /** {@code <lane = 3, mode = "tail">}, or empty when there are none. */
    public String text() {
        if (values.isEmpty()) {
            return "";
        }
        return values.entrySet().stream().map(e -> e.getKey() + " = " + (e.getValue() instanceof String s ? quote(s) : e.getValue().toString()))
                .collect(Collectors.joining(", ", "<", ">"));
    }

    /** A string as IR text: quoted, with backslash, quote and control characters escaped (the parser reads it back). */
    public static String quote(String s) {
        var sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\x%02x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** The inverse of {@link #quote}: the body of a quoted IR string, unescaped. */
    public static String unquote(String quoted) {
        var body = quoted.substring(1, quoted.length() - 1);
        var sb = new StringBuilder();
        for (int k = 0; k < body.length(); k++) {
            char c = body.charAt(k);
            if (c != '\\' || k + 1 >= body.length()) {
                sb.append(c);
                continue;
            }
            char e = body.charAt(++k);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'x' -> {
                    sb.append((char) Integer.parseInt(body.substring(k + 1, k + 3), 16));
                    k += 2;
                }
                default -> sb.append(e);
            }
        }
        return sb.toString();
    }
}
