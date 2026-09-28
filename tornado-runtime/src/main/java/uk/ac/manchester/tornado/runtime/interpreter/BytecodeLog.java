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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.runtime.common.BytecodeLogMode;
import uk.ac.manchester.tornado.runtime.common.ColoursTerminal;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Accumulates the bytecode log of a single interpreter execution: the header naming the task-graph and
 * device, one rendered line per bytecode, and a closing summary of what the execution actually did.
 *
 * <p>
 * The device is declared once in the header and referred to by a short stable identifier
 * ({@code D0}, {@code D1}, ...) rather than repeated on every line, which is where most of the width of
 * the historical format went.
 * </p>
 */
final class BytecodeLog {

    private static final Map<String, String> DEVICE_IDS = new ConcurrentHashMap<>();
    private static final AtomicInteger DEVICE_COUNTER = new AtomicInteger();

    private final BytecodeLogMode mode;
    private final boolean colour;
    private final String graphName;
    private final long execution;
    private final String threadName;
    private final String deviceId;
    private final StringBuilder text = new StringBuilder(4096);
    private final List<BytecodeLogEntry> entries;
    private final boolean transfersOnly;

    private String indent = "";
    private int sequence;
    private int allocations;
    private int deallocations;
    private int launches;
    private int skipped;
    private int hostToDevice;
    private int hostToDeviceExecuted;
    private int deviceToHost;
    private int persisted;
    private long bytesToDevice;
    private long bytesToHost;

    /**
     * @param transfersOnly
     *     true when the interpreter is walking the bytecode for its data transfers only, as
     *     {@code TornadoExecutionPlan.transferToDevice()} does. Without saying so, such a pass reads
     *     as an execution that mysteriously ran no kernels.
     */
    BytecodeLog(BytecodeLogMode mode, String graphName, long execution, TornadoXPUDevice device, boolean transfersOnly) {
        this.mode = mode;
        this.colour = InterpreterUtilities.isColourEnabled();
        this.graphName = graphName;
        this.execution = execution;
        this.threadName = Thread.currentThread().getName();
        this.deviceId = deviceIdFor(device);
        this.entries = mode == BytecodeLogMode.DOT ? new ArrayList<>() : null;
        this.transfersOnly = transfersOnly;

        if (mode.isTextual()) {
            String header = String.format("== graph '%s' | exec #%d | %s=%s | thread %s%s", graphName, execution, deviceId, String.valueOf(device).trim(), threadName,
                    transfersOnly ? " | transfers-only (no task runs)" : "");
            text.append(colour ? ColoursTerminal.BLUE + header + ColoursTerminal.RESET : header).append("\n");
        }
    }

    private static String deviceIdFor(TornadoXPUDevice device) {
        return DEVICE_IDS.computeIfAbsent(String.valueOf(device), key -> "D" + DEVICE_COUNTER.getAndIncrement());
    }

    void add(BytecodeLogEntry entry) {
        count(entry);
        if (entries != null) {
            entries.add(entry);
            return;
        }
        text.append(BytecodeRenderer.line(entry, mode, colour, sequence, indent)).append("\n");
        sequence++;
    }

    /**
     * Records a bytecode the interpreter deliberately did not perform. Only {@link BytecodeLogMode#TRACE}
     * prints these, since on a steady-state loop they are the majority of the log.
     */
    void addSkipped(BytecodeLogEntry entry) {
        skipped++;
        if (mode == BytecodeLogMode.TRACE) {
            text.append(BytecodeRenderer.line(entry.withStatus(false, entry.status()), mode, colour, sequence, indent)).append("\n");
            sequence++;
        }
    }

    private void count(BytecodeLogEntry entry) {
        switch (entry.op()) {
            case "ALLOC" -> allocations++;
            case "DEALLOC" -> deallocations++;
            case "LAUNCH" -> launches++;
            case "PERSIST" -> persisted++;
            default -> {
                if (entry.op().startsWith("TRANSFER_HOST_TO_DEVICE")) {
                    hostToDevice++;
                    if (entry.executed()) {
                        hostToDeviceExecuted++;
                        bytesToDevice += entry.size();
                    }
                } else if (entry.op().startsWith("TRANSFER_DEVICE_TO_HOST")) {
                    deviceToHost++;
                    bytesToHost += entry.size();
                }
            }
        }
    }

    void setIndent(String newIndent) {
        this.indent = newIndent;
    }

    void end() {
        if (!mode.isTextual()) {
            return;
        }
        // h2d is reported as executed/total: a TRANSFER_HOST_TO_DEVICE_ONCE whose buffer is already on
        // the device is a no-op, and on a steady-state loop those are the majority.
        String summary = String.format("== END   graph '%s' | exec #%d | %d alloc | %d dealloc | %d launch | h2d %d/%d (%s) | d2h %d (%s) | %d persist | %d skipped%s", //
                graphName, execution, allocations, deallocations, launches, //
                hostToDeviceExecuted, hostToDevice, RuntimeUtilities.humanReadableByteCount(bytesToDevice, false), //
                deviceToHost, RuntimeUtilities.humanReadableByteCount(bytesToHost, false), //
                persisted, skipped, transfersOnly ? " | transfers-only" : "");
        text.append(colour ? ColoursTerminal.BLUE + summary + ColoursTerminal.RESET : summary).append("\n");
    }

    /** Writes the log to the console and/or to the dump directory, honouring the selected mode. */
    void flush() {
        if (mode == BytecodeLogMode.DOT) {
            BytecodeDotWriter.write(graphName, execution, entries);
            return;
        }
        if (TornadoOptions.PRINT_BYTECODES) {
            System.out.println(text);
        }
        if (!TornadoOptions.DUMP_BYTECODES.isBlank()) {
            RuntimeUtilities.writeBytecodeToFile(text);
        }
    }

    @Override
    public String toString() {
        return text.toString();
    }
}
