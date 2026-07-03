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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The set of role streams owned by a single execution plan on a device, together
 * with that plan's private {@link EventRegistry}. This is the per-execution binding
 * from the CUDA-streams design: streams are addressed by {@link PTXStreamType} role
 * (H2D / COMPUTE / D2H / DEFAULT) rather than by host thread.
 *
 * <p>Replaces the former thread-keyed {@code ThreadStreamTable}: there is no
 * {@code threadId} dimension. A plan executes on one issuing thread at a time and
 * the {@code CUcontext} is made current on whichever thread issues, so binding role
 * streams to the plan (not the thread) is both sufficient and what enables later
 * inter-plan concurrency.
 */
public final class ExecutionStreamSet {

    /**
     * Number of CUDA streams in the COMPUTE pool. DAG-independent kernels are round-robined
     * across these so they can execute concurrently (CUDA serialises within a single stream).
     * Configurable via {@code -Dtornado.ptx.compute.streams}; defaults to 4.
     */
    public static final int COMPUTE_POOL_SIZE = Math.max(1, Integer.getInteger("tornado.ptx.compute.streams", 4));

    /** Single-instance role streams (DEFAULT / H2D / D2H). COMPUTE is handled by {@link #computeStreams}. */
    private final Map<PTXStreamType, PTXStream> streams = new EnumMap<>(PTXStreamType.class);
    /** The COMPUTE stream pool, grown lazily up to {@link #COMPUTE_POOL_SIZE}. */
    private final List<PTXStream> computeStreams = new ArrayList<>();
    /** Round-robin cursor for assigning kernels to compute streams. */
    private int nextCompute = 0;
    private final EventRegistry eventRegistry = new EventRegistry();

    /** Returns the role stream, creating it lazily on first use. For COMPUTE, returns pool index 0. */
    public synchronized PTXStream acquire(PTXStreamType type) {
        if (type == PTXStreamType.COMPUTE) {
            return acquireCompute(0);
        }
        return streams.computeIfAbsent(type, PTXStream::new);
    }

    /** Returns the COMPUTE stream at {@code index} in the pool, creating intermediate streams lazily. */
    public synchronized PTXStream acquireCompute(int index) {
        while (computeStreams.size() <= index) {
            computeStreams.add(new PTXStream(PTXStreamType.COMPUTE, computeStreams.size()));
        }
        return computeStreams.get(index);
    }

    /** Round-robin index of the next COMPUTE stream to assign a kernel to (0..COMPUTE_POOL_SIZE-1). */
    public synchronized int nextComputeIndex() {
        int idx = nextCompute % COMPUTE_POOL_SIZE;
        nextCompute = (nextCompute + 1) % COMPUTE_POOL_SIZE;
        return idx;
    }

    /** Returns the role stream if it has already been created, otherwise {@code null}. For COMPUTE, pool index 0. */
    public synchronized PTXStream getIfExists(PTXStreamType type) {
        if (type == PTXStreamType.COMPUTE) {
            return computeStreams.isEmpty() ? null : computeStreams.get(0);
        }
        return streams.get(type);
    }

    public EventRegistry eventRegistry() {
        return eventRegistry;
    }

    /** Snapshot of the currently-created streams (single-instance roles plus the whole COMPUTE pool). */
    public synchronized Collection<PTXStream> activeStreams() {
        List<PTXStream> all = new ArrayList<>(streams.values());
        all.addAll(computeStreams);
        return all;
    }

    public synchronized boolean isEmpty() {
        return streams.isEmpty() && computeStreams.isEmpty();
    }

    /** Destroys all role streams and clears the plan-private event registry. */
    public synchronized void cleanup() {
        for (PTXStream stream : activeStreams()) {
            if (!stream.isDestroy()) {
                stream.reset();
                stream.cuDestroyStream();
            }
        }
        streams.clear();
        computeStreams.clear();
        nextCompute = 0;
        eventRegistry.reset();
    }
}
