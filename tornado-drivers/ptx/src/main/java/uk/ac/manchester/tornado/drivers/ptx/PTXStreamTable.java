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

public class PTXStreamTable {

    private final Map<PTXDevice, ThreadStreamTable> deviceStream;

    PTXStreamTable() {
        deviceStream = new ConcurrentHashMap<>();
    }

    public PTXStream get(PTXDevice device) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        if (!deviceStream.containsKey(device)) {
            ThreadStreamTable threadStreamTable = new ThreadStreamTable();
            threadStreamTable.get(Thread.currentThread().threadId());
            deviceStream.put(device, threadStreamTable);
        }
        return deviceStream.get(device).get(Thread.currentThread().threadId());
    }

    public void cleanup(PTXDevice device) {
        if (deviceStream.containsKey(device)) {
            deviceStream.get(device).cleanup(Thread.currentThread().threadId());
        }
        if (deviceStream.get(device).size() == 0) {
            deviceStream.remove(device);
        }
    }

    public int size() {
        return deviceStream.size();
    }

    private static class ThreadStreamTable {

        private final Map<Long, PTXStream> streamTable;

        ThreadStreamTable() {
            streamTable = new ConcurrentHashMap<>();
        }

        public PTXStream get(long threadId) {
            if (!streamTable.containsKey(threadId)) {
                PTXStream stream = new PTXStream();
                streamTable.put(threadId, stream);
            }
            return streamTable.get(threadId);
        }

        public void cleanup(long threadId) {
            if (streamTable.containsKey(threadId)) {
                PTXStream queue = streamTable.remove(threadId);
                queue.reset();
                queue.cuDestroyStream();
            }
        }

        public int size() {
            return streamTable.size();
        }

    }
}
