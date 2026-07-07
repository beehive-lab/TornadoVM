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

/**
 * Owned by a {@link PTXDeviceContext}, this maps each execution plan to its own
 * {@link PTXExecutionStreamSet} (its role streams and plan-private {@link PTXEventRegistry}).
 * It is the device context's entry point for obtaining the {@link PTXStream} that should run a
 * given operation: callers ask for a stream by execution plan and {@link PTXStreamType} role
 * rather than by host thread.
 *
 * <p>Replaces the former thread-keyed {@code PTXStreamTable}: pooling per plan (not per thread)
 * is what lets independent plans hold isolated streams. The owning device context is already
 * per-device, so this pool carries no device dimension.
 */
public final class PTXStreamPool {

    private final Map<Long, PTXExecutionStreamSet> streamSets = new ConcurrentHashMap<>();

    /**
     * Returns the role stream for a plan, creating the {@link PTXExecutionStreamSet} and the
     * stream lazily.
     *
     * @return the role stream, or {@code null} when called on the JVM shutdown-hook thread
     *         (mirrors the prior {@code PTXStreamTable} behaviour so shutdown cleanup is safe).
     */
    public PTXStream acquire(long executionPlanId, PTXStreamType type) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        return streamSets.computeIfAbsent(executionPlanId, k -> new PTXExecutionStreamSet()).acquire(type);
    }

    /** Returns the COMPUTE stream at {@code index} in the plan's compute pool. */
    public PTXStream acquireCompute(long executionPlanId, int index) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        return streamSets.computeIfAbsent(executionPlanId, k -> new PTXExecutionStreamSet()).acquireCompute(index);
    }

    /** Round-robin index of the next COMPUTE stream for this plan. */
    public int nextComputeIndex(long executionPlanId) {
        return streamSets.computeIfAbsent(executionPlanId, k -> new PTXExecutionStreamSet()).nextComputeIndex();
    }

    /** Snapshot of all currently-created streams for a plan (or empty if the plan is unknown). */
    public java.util.Collection<PTXStream> activeStreams(long executionPlanId) {
        PTXExecutionStreamSet set = streamSets.get(executionPlanId);
        return set == null ? java.util.List.of() : set.activeStreams();
    }

    /** Returns the role stream only if its {@link PTXExecutionStreamSet} and the stream already exist. */
    public PTXStream getIfExists(long executionPlanId, PTXStreamType type) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        PTXExecutionStreamSet set = streamSets.get(executionPlanId);
        return set == null ? null : set.getIfExists(type);
    }

    /** The plan-private event registry, creating the plan's stream set if needed. */
    public PTXEventRegistry eventRegistry(long executionPlanId) {
        return streamSets.computeIfAbsent(executionPlanId, k -> new PTXExecutionStreamSet()).eventRegistry();
    }

    public boolean contains(long executionPlanId) {
        return streamSets.containsKey(executionPlanId);
    }

    /** Destroys all streams and the registry for a plan and removes it from the pool. */
    public void remove(long executionPlanId) {
        PTXExecutionStreamSet set = streamSets.remove(executionPlanId);
        if (set != null) {
            set.cleanup();
        }
    }
}
