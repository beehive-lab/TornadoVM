/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A device-specific table of ptx streams.
 * The device belongs to an execution plan.
 * Each device has its own thread-specific stream table.
 * Each thread has its own stream table entry.
 * Each stream table entry has its own set of streams.
 * Each set of streams contains:
 * <list>
 *     <li>A default stream ({@link PTXStreamType#DEFAULT})</li>
 *     <li>A stream for host-to-device transfers ({@link PTXStreamType#DATA_TRANSFER_H2D})</li>
 *     <li>A stream for kernel execution ({@link PTXStreamType#COMPUTE})</li>
 *     <li>A stream for device-to-host transfers ({@link PTXStreamType#DATA_TRANSFER_D2H})</li>
 * </list>
 *
 */
public class PTXStreamTable {

    /**
     * Maps a device of a specific execution plan to a thread-specific ptx stream table.
     */
    private final Map<PTXDevice, ThreadStreamTable> deviceThreads;

    PTXStreamTable() {
        deviceThreads = new ConcurrentHashMap<>();
    }

    /**
     * Backward-compatible accessor.
     * Returns the DEFAULT stream for the current thread.
     */
    public PTXStream get(PTXDevice device) {
        return get(device, PTXStreamType.DEFAULT);
    }

    /**
     * Get a ptx stream of {@link PTXStreamType} for the current thread on a device.
     */
    public PTXStream get(PTXDevice device, PTXStreamType type) {
        final long tid = Thread.currentThread().threadId();

        if (tid == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }

        ThreadStreamTable deviceThreadStreamTable =
                deviceThreads.computeIfAbsent(device, d -> new ThreadStreamTable());

        return deviceThreadStreamTable.get(tid, type);
    }

    public PTXStream getIfExists(PTXDevice device, PTXStreamType type) {
        final long tid = Thread.currentThread().threadId();
        if (tid == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        ThreadStreamTable threadStreamTable = deviceThreads.get(device);
        if (threadStreamTable == null) return null;
        return threadStreamTable.getIfExists(tid, type);
    }

    /**
     * Cleanup all streams for the current thread on a device.
     */
    public void cleanup(PTXDevice device) {
        long tid = Thread.currentThread().threadId();

        ThreadStreamTable threadStreamTable = deviceThreads.get(device);
        if (threadStreamTable == null) {
            return;
        }
        threadStreamTable.cleanup(tid);
        if (threadStreamTable.size() == 0) {
            deviceThreads.remove(device);
        }
    }

    public int size() {
        return deviceThreads.size();
    }

    private static class ThreadStreamTable {

        private final Map<Long, Map<PTXStreamType, PTXStream>> streamTable;

        ThreadStreamTable() {
            streamTable = new ConcurrentHashMap<>();
        }

        public PTXStream get(long threadId, PTXStreamType type) {
            // Get or create the per-device thread stream map
            Map<PTXStreamType, PTXStream> threadStreams =
                streamTable.computeIfAbsent(threadId, k -> new ConcurrentHashMap<>());
            // Get or create the specific stream type
            return threadStreams.computeIfAbsent(type, t -> new PTXStream(t));
        }

        public PTXStream getIfExists(long threadId, PTXStreamType type) {
            Map<PTXStreamType, PTXStream> threadStreams = streamTable.get(threadId);
            return threadStreams == null ? null : threadStreams.get(type);
        }

        public void cleanup(long threadId) {
            Map<PTXStreamType, PTXStream> threadStreams = streamTable.remove(threadId);
            if (threadStreams != null) {
                for (PTXStream stream : threadStreams.values()) {
                    stream.reset();
                    stream.cuDestroyStream();
                }
            }
        }

        public int size() {
            return streamTable.size();
        }

    }
}
