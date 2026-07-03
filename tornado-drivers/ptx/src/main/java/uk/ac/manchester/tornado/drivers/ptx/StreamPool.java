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
 * Per-device-context pool of {@link ExecutionStreamSet}s, keyed by execution plan.
 * Replaces the thread-keyed {@code PTXStreamTable}: streams are pooled per plan and
 * addressed by {@link PTXStreamType} role, decoupled from the issuing host thread.
 *
 * <p>The owning {@link PTXDeviceContext} is already per-device, so this pool does not
 * carry a device dimension. Later iterations will extend it with a stream cap and a
 * ring of generic streams ({@code acquire(n)}) for staged transfers; Phase 1 keeps it
 * to per-plan role streams plus the plan-private {@link EventRegistry}.
 */
public final class StreamPool {

    private final Map<Long, ExecutionStreamSet> streamSets = new ConcurrentHashMap<>();

    /**
     * Returns the role stream for a plan, creating the {@link ExecutionStreamSet} and the
     * stream lazily.
     *
     * @return the role stream, or {@code null} when called on the JVM shutdown-hook thread
     *         (mirrors the prior {@code PTXStreamTable} behaviour so shutdown cleanup is safe).
     */
    public PTXStream acquire(long executionPlanId, PTXStreamType type) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        return streamSets.computeIfAbsent(executionPlanId, k -> new ExecutionStreamSet()).acquire(type);
    }

    /** Returns the COMPUTE stream at {@code index} in the plan's compute pool. */
    public PTXStream acquireCompute(long executionPlanId, int index) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        return streamSets.computeIfAbsent(executionPlanId, k -> new ExecutionStreamSet()).acquireCompute(index);
    }

    /** Round-robin index of the next COMPUTE stream for this plan. */
    public int nextComputeIndex(long executionPlanId) {
        return streamSets.computeIfAbsent(executionPlanId, k -> new ExecutionStreamSet()).nextComputeIndex();
    }

    /** Snapshot of all currently-created streams for a plan (or empty if the plan is unknown). */
    public java.util.Collection<PTXStream> activeStreams(long executionPlanId) {
        ExecutionStreamSet set = streamSets.get(executionPlanId);
        return set == null ? java.util.List.of() : set.activeStreams();
    }

    /** Returns the role stream only if its {@link ExecutionStreamSet} and the stream already exist. */
    public PTXStream getIfExists(long executionPlanId, PTXStreamType type) {
        if (Thread.currentThread().threadId() == PTX.SHUTDOW_THREAD_ID_HOOK) {
            return null;
        }
        ExecutionStreamSet set = streamSets.get(executionPlanId);
        return set == null ? null : set.getIfExists(type);
    }

    /** The plan-private event registry, creating the plan's stream set if needed. */
    public EventRegistry eventRegistry(long executionPlanId) {
        return streamSets.computeIfAbsent(executionPlanId, k -> new ExecutionStreamSet()).eventRegistry();
    }

    public boolean contains(long executionPlanId) {
        return streamSets.containsKey(executionPlanId);
    }

    /** Destroys all streams and the registry for a plan and removes it from the pool. */
    public void remove(long executionPlanId) {
        ExecutionStreamSet set = streamSets.remove(executionPlanId);
        if (set != null) {
            set.cleanup();
        }
    }
}
