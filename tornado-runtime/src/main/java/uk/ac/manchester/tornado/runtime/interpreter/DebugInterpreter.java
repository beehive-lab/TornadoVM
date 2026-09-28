/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

import java.util.List;

import uk.ac.manchester.tornado.api.common.SchedulableTask;

/**
 * Records what the interpreter did, one {@link BytecodeLogEntry} per bytecode.
 *
 * <p>
 * These methods used to build finished, coloured strings. They now only collect facts: the device is
 * named once in the {@link BytecodeLog} header rather than on every line, and {@link BytecodeRenderer}
 * decides what to show at the requested verbosity.
 * </p>
 */
class DebugInterpreter {

    static void logAllocObject(Object object, long size, long sizeBatch, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("ALLOC", object, size, sizeBatch));
    }

    static void logDeallocObject(Object object, BytecodeLog log, boolean materializeDealloc) {
        log.add(BytecodeLogEntry.of("DEALLOC", object, 0, 0).withStatus(materializeDealloc, materializeDealloc ? "Freed" : "Persisted"));
    }

    /**
     * An allocation the interpreter skipped because an execution (CUDA) graph already owns the
     * buffers. Logged for the same reason the matching deallocation is: a bytecode that silently
     * does nothing is what makes a log hard to trust.
     */
    static void logAllocSkipped(Object object, BytecodeLog log) {
        log.addSkipped(BytecodeLogEntry.of("ALLOC", object, 0, 0).withStatus(false, "Skipped: execution graph active"));
    }

    /** A deallocation the interpreter skipped because an execution (CUDA) graph still owns the buffer. */
    static void logDeallocSkipped(Object object, BytecodeLog log) {
        log.addSkipped(BytecodeLogEntry.of("DEALLOC", object, 0, 0).withStatus(false, "Skipped: execution graph active"));
    }

    static void logOnDeviceObject(Object object, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("ON_DEVICE", object, 0, 0));
    }

    static void logPersistedObject(Object object, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("PERSIST", object, 0, 0));
    }

    static void logTransferToDeviceOnce(List<Integer> allEvents, Object object, long sizeObject, long sizeBatch, long offset, final int eventList, BytecodeLog log) {
        // A null event list means the buffer was already on the device, so nothing was transferred.
        boolean executed = allEvents != null;
        log.add(BytecodeLogEntry.of("TRANSFER_HOST_TO_DEVICE_ONCE", object, sizeObject, sizeBatch) //
                .withTransfer(offset, eventList, null) //
                .withStatus(executed, executed ? "Transferred" : "Present"));
    }

    static void logTransferToDeviceAlways(Object object, long sizeObject, long sizeBatch, long offset, final int eventList, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("TRANSFER_HOST_TO_DEVICE_ALWAYS", object, sizeObject, sizeBatch).withTransfer(offset, eventList, null));
    }

    static void logTransferToHostAlways(Object object, long sizeObject, long sizeBatch, long offset, final int eventList, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("TRANSFER_DEVICE_TO_HOST_ALWAYS", object, sizeObject, sizeBatch).withTransfer(offset, eventList, null));
    }

    static void logTransferToHostAlwaysBlocking(Object object, BytecodeLog log, long sizeObject, long sizeBatch, long offset, int eventId) {
        log.add(BytecodeLogEntry.of("TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING", object, sizeObject, sizeBatch).withTransfer(offset, eventId, null));
    }

    static void logLaunchTask(SchedulableTask task, long numBatchThreads, long offset, int eventId, BytecodeLog log) {
        String note = numBatchThreads > 0 ? "threads=" + numBatchThreads : null;
        log.add(new BytecodeLogEntry("LAUNCH", true, null, task.getFullName(), 0, 0, offset, eventId, null, null, note));
    }

    static void logStreamInAtomic(Object bufferAtomics, int eventId, BytecodeLog log) {
        log.add(BytecodeLogEntry.of("STREAM_IN", bufferAtomics, 0, 0).withTransfer(0, eventId, null).withStatus(true, "Atomics"));
    }

    /** Execution-graph (CUDA graph) capture, replay and teardown bytecodes. */
    static void logExecutionGraph(BytecodeLog log, String op, int graphId) {
        log.add(BytecodeLogEntry.control(op, "graphId=" + graphId));
    }

    static void logAddDependency(int lastEvent, int eventId, BytecodeLog log) {
        log.add(BytecodeLogEntry.control("ADD_DEPENDENCY", String.format("event %d to event list %d", lastEvent, eventId)));
    }

    static void logBarrier(int eventId, int[] waitList, BytecodeLog log) {
        log.add(new BytecodeLogEntry("BARRIER", true, null, null, 0, 0, 0, eventId, waitList, null, null));
    }
}
