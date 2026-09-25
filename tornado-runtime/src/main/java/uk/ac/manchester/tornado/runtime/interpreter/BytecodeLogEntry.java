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

/**
 * One entry of the TornadoVM bytecode log, holding the raw facts of a single interpreted bytecode.
 *
 * <p>
 * Formatting is deliberately not done here: {@link BytecodeRenderer} turns an entry into text at the
 * requested verbosity and {@link BytecodeDotWriter} turns the same entries into a dependency graph.
 * Fields that do not apply to a given bytecode are left at their neutral value
 * ({@code null}, {@code 0} or {@link #NO_EVENT}).
 * </p>
 *
 * @param op
 *     bytecode name, e.g. {@code ALLOC}, {@code LAUNCH}, {@code TRANSFER_HOST_TO_DEVICE_ONCE}
 * @param executed
 *     false when the interpreter logged the bytecode but did not perform the operation (buffer
 *     already present, deallocation skipped because an execution graph is active, ...)
 * @param object
 *     the data object the bytecode operates on, or null for task/control bytecodes
 * @param taskName
 *     fully qualified task name for {@code LAUNCH}, otherwise null
 * @param size
 *     size in bytes of the object or transfer, 0 when not applicable
 * @param batchSize
 *     batch size in bytes, 0 when the object is not batched
 * @param offset
 *     offset in bytes into the object, 0 when not applicable
 * @param eventList
 *     index of the wait-event list this bytecode signals into, {@link #NO_EVENT} when none
 * @param waitEvents
 *     event identifiers this bytecode waits on, null when it waits on nothing
 * @param status
 *     short outcome word, e.g. {@code Transferred}, {@code Present}, {@code Freed}, {@code Persisted}
 * @param note
 *     free-form detail, e.g. {@code graphId=0} or the reason a bytecode was skipped
 */
record BytecodeLogEntry(String op, boolean executed, Object object, String taskName, long size, long batchSize, long offset, int eventList, int[] waitEvents, String status, String note) {

    static final int NO_EVENT = Integer.MIN_VALUE;

    static BytecodeLogEntry of(String op, Object object, long size, long batchSize) {
        return new BytecodeLogEntry(op, true, object, null, size, batchSize, 0, NO_EVENT, null, null, null);
    }

    static BytecodeLogEntry control(String op, String note) {
        return new BytecodeLogEntry(op, true, null, null, 0, 0, 0, NO_EVENT, null, null, note);
    }

    BytecodeLogEntry withStatus(boolean isExecuted, String newStatus) {
        return new BytecodeLogEntry(op, isExecuted, object, taskName, size, batchSize, offset, eventList, waitEvents, newStatus, note);
    }

    BytecodeLogEntry withTransfer(long transferOffset, int list, int[] waits) {
        return new BytecodeLogEntry(op, executed, object, taskName, size, batchSize, transferOffset, list, waits, status, note);
    }

    boolean hasEventList() {
        return eventList != NO_EVENT;
    }
}
