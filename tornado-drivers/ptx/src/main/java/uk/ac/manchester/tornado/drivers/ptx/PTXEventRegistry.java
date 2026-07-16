/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a plan-private mapping of {@link PTXEvent}s to {@link PTXStream}s to
 * facilitate cross-stream synchronization. Each {@link PTXExecutionStreamSet} (i.e.
 * each execution plan on a device) owns exactly one registry, which is what keeps
 * concurrent execution plans isolated from one another.
 *
 * <p>In multi-stream mode, {@link PTXEvent}s live in per-stream {@link PTXEventPool}s
 * and are addressed by a <em>local</em> event id. This registry pairs that local id
 * with its owning {@link PTXStreamType} as a {@link PTXEventEntry} and assigns a unique
 * <em>global</em> event id, so an event recorded on one stream can be referenced from
 * another. The global id flows through the interpreter's {@code ADD_DEPENDENCY}/waitList
 * mechanism and is resolved back here for (a) profiling (read the {@link PTXEvent}'s
 * timing) and (b) cross-stream sync (insert {@code cuStreamWaitEvent} on a target stream).
 *
 * <p>In single-stream mode this registry is unused - local event ids are passed directly.
 */
public final class PTXEventRegistry {

    /** Unique/global id for a {@link PTXEvent} across all {@link PTXEventPool} instances of one plan. */
    private final AtomicInteger globalIdCounter = new AtomicInteger(0);

    /** The core registry data structure. */
    private final Map<Integer, PTXEventEntry> registry = new ConcurrentHashMap<>();

    /**
     * Represents a (PTXStreamType, streamIndex, localId) triple.
     *
     * @param streamType the role of the stream that owns the event
     * @param streamIndex the stream's index within its role pool (0 for single-instance roles;
     *     0..N-1 for the COMPUTE pool) - required so an event recorded on compute-stream <i>i</i>
     *     can be distinguished from one on compute-stream <i>j</i>
     * @param localEventId the event's id within that stream's {@link PTXEventPool}
     */
    public record PTXEventEntry(PTXStreamType streamType, int streamIndex, int localEventId) {
    }

    /** Registers an event owned by the index-0 stream of {@code streamType}. */
    public int register(PTXStreamType streamType, int localEventId) {
        return register(streamType, 0, localEventId);
    }

    public int register(PTXStreamType streamType, int streamIndex, int localEventId) {
        int globalId = globalIdCounter.getAndIncrement();
        registry.put(globalId, new PTXEventEntry(streamType, streamIndex, localEventId));
        return globalId;
    }

    public PTXEventEntry resolve(int globalEventId) {
        return registry.get(globalEventId);
    }

    public void reset() {
        registry.clear();
        globalIdCounter.set(0);
    }
}
