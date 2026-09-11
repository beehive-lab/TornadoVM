/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.common;

/**
 * Verbosity of the TornadoVM bytecode log, selected with
 * {@code -Dtornado.print.bytecodes=<mode>} (or {@code --printBytecodes <mode>}).
 *
 * <p>
 * The legacy boolean values are still accepted: {@code True} maps to {@link #FULL} and {@code False}
 * to {@link #OFF}, so existing scripts keep working unchanged.
 * </p>
 */
public enum BytecodeLogMode {

    /** No bytecode logging. */
    OFF,

    /** One aligned line per bytecode, zero-valued fields elided, per-execution summary. */
    COMPACT,

    /** Every field of every executed bytecode. This is what {@code =True} selects. */
    FULL,

    /** As {@link #FULL} plus skipped bytecodes, resolved wait-lists and thread identity. */
    TRACE,

    /** Emit a Graphviz DOT dependency graph per execution instead of text. */
    DOT;

    public static BytecodeLogMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        return switch (value.trim().toLowerCase()) {
            case "false", "off", "no", "0" -> OFF;
            case "true", "on", "yes", "1", "full" -> FULL;
            case "compact" -> COMPACT;
            case "trace" -> TRACE;
            case "dot", "graph" -> DOT;
            default -> {
                System.err.println("[TornadoVM] unknown tornado.print.bytecodes value '" + value + "', using 'full'. " //
                        + "Valid values: false, compact, full, trace, dot.");
                yield FULL;
            }
        };
    }

    /** True when this mode produces the textual log (as opposed to a DOT file). */
    public boolean isTextual() {
        return this == COMPACT || this == FULL || this == TRACE;
    }

    /** True when fields that carry no information (zero batch size, empty event list) are elided. */
    public boolean isTerse() {
        return this == COMPACT;
    }
}
