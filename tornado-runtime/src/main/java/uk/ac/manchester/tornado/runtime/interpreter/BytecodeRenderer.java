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
package uk.ac.manchester.tornado.runtime.interpreter;

import java.util.Arrays;

import uk.ac.manchester.tornado.runtime.common.BytecodeLogMode;
import uk.ac.manchester.tornado.runtime.common.ColoursTerminal;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

/**
 * Turns a {@link BytecodeLogEntry} into one line of text.
 *
 * <p>
 * {@link BytecodeLogMode#COMPACT} shortens the bytecode names, prints human-readable sizes and drops
 * fields that carry no information. {@link BytecodeLogMode#FULL} keeps the full bytecode names and the
 * raw byte counts of the historical format, so dumps stay comparable and greppable, but aligns the
 * columns and prints the device once in the header instead of on every line.
 * </p>
 */
final class BytecodeRenderer {

    /** Width of the bytecode-name column, the longest name being TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING. */
    private static final int OP_WIDTH_FULL = 39;
    private static final int OP_WIDTH_COMPACT = 11;
    private static final int LABEL_WIDTH = 38;

    private BytecodeRenderer() {
    }

    static String shortOp(String op) {
        return switch (op) {
            case "TRANSFER_HOST_TO_DEVICE_ONCE" -> "H2D_ONCE";
            case "TRANSFER_HOST_TO_DEVICE_ALWAYS" -> "H2D";
            case "TRANSFER_DEVICE_TO_HOST_ALWAYS" -> "D2H";
            case "TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING" -> "D2H_SYNC";
            case "EXECUTION_GRAPH_BEGIN_CAPTURE" -> "GRAPH_CAP{";
            case "EXECUTION_GRAPH_END_CAPTURE" -> "GRAPH_CAP}";
            case "EXECUTION_GRAPH_LAUNCH" -> "GRAPH_RUN";
            case "EXECUTION_GRAPH_DESTROY" -> "GRAPH_FREE";
            case "ADD_DEPENDENCY" -> "ADD_DEP";
            default -> op;
        };
    }

    /**
     * Uniform object label: simple type name, its dimensions when the type prints them, and always the
     * identity hash. Replaces the historical mix of {@code ImageFloat <320 x 240>} and
     * {@code uk.ac.manchester.tornado.api.types.arrays.FloatArray@3efe7086}.
     */
    static String objectLabel(Object object) {
        if (object == null) {
            return "-";
        }
        String hash = String.format("@%x", object.hashCode());
        String simpleName = object.getClass().getSimpleName();
        String asString = String.valueOf(object);
        if (asString.startsWith(object.getClass().getName() + "@")) {
            return simpleName + hash;
        }
        // Types with a meaningful toString, e.g. "ImageFloat <320 x 240>" -> "ImageFloat<320x240>"
        return asString.replace(" ", "") + hash;
    }

    static String line(BytecodeLogEntry entry, BytecodeLogMode mode, boolean colour, int sequence, String indent) {
        StringBuilder line = new StringBuilder(160);
        line.append(indent);
        line.append(String.format("%4d ", sequence));

        boolean compact = mode.isTerse();
        String name = compact ? shortOp(entry.op()) : entry.op();
        String padded = String.format("%-" + (compact ? OP_WIDTH_COMPACT : OP_WIDTH_FULL) + "s", name);
        line.append(colour ? colourFor(entry) + padded + ColoursTerminal.RESET : padded);
        line.append(' ');

        // Control bytecodes (barriers, execution-graph operations) have no object: their note is the label.
        boolean noteIsLabel = entry.taskName() == null && entry.object() == null && entry.note() != null;
        String label = entry.taskName() != null ? entry.taskName() : noteIsLabel ? entry.note() : objectLabel(entry.object());
        line.append(String.format("%-" + LABEL_WIDTH + "s", label));

        if (compact) {
            appendCompactDetails(line, entry);
        } else {
            appendFullDetails(line, entry);
        }

        if (mode == BytecodeLogMode.TRACE && entry.waitEvents() != null) {
            line.append(" waits=").append(Arrays.toString(entry.waitEvents()));
        }
        if (entry.note() != null && !noteIsLabel) {
            line.append(' ').append(entry.note());
        }
        if (entry.status() != null) {
            String status = "[" + entry.status() + "]";
            line.append(' ').append(colour ? ColoursTerminal.YELLOW + status + ColoursTerminal.RESET : status);
        }
        return line.toString().stripTrailing();
    }

    private static void appendCompactDetails(StringBuilder line, BytecodeLogEntry entry) {
        if (entry.size() > 0) {
            line.append(String.format(" %10s", RuntimeUtilities.humanReadableByteCount(entry.size(), false)));
        } else {
            line.append(String.format(" %10s", ""));
        }
        if (entry.batchSize() > 0) {
            line.append(" batch=").append(RuntimeUtilities.humanReadableByteCount(entry.batchSize(), false));
        }
        if (entry.offset() > 0) {
            line.append(" offset=").append(RuntimeUtilities.humanReadableByteCount(entry.offset(), false));
        }
        if (entry.hasEventList() && entry.eventList() >= 0) {
            line.append(" ev").append(entry.eventList());
        }
    }

    private static void appendFullDetails(StringBuilder line, BytecodeLogEntry entry) {
        StringBuilder details = new StringBuilder();
        if (entry.size() > 0 || entry.batchSize() > 0) {
            details.append(String.format("size=%d, batchSize=%d", entry.size(), entry.batchSize()));
        }
        if (entry.hasEventList()) {
            if (!details.isEmpty()) {
                details.append(", ");
            }
            details.append(String.format("offset=%d [event list=%d]", entry.offset(), entry.eventList()));
        }
        if (!details.isEmpty()) {
            line.append(' ').append(details);
        }
    }

    private static String colourFor(BytecodeLogEntry entry) {
        if (!entry.executed()) {
            return ColoursTerminal.YELLOW;
        }
        return switch (entry.op()) {
            case "LAUNCH" -> ColoursTerminal.GREEN;
            case "ALLOC", "DEALLOC" -> ColoursTerminal.PURPLE;
            case "BARRIER", "ADD_DEPENDENCY", "END" -> ColoursTerminal.BLUE;
            default -> entry.op().startsWith("TRANSFER") ? ColoursTerminal.CYAN : ColoursTerminal.RED;
        };
    }
}
