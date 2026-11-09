/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Orders the device work of execution plans that share device buffers through
 * {@code consumeFromDevice(producer, ...)}.
 *
 * <p>
 * Each plan, and each host thread within a plan, has its own device queue, and the device does
 * not order two queues. So every shared buffer remembers a fence for the last run that used it.
 * Before a run, the task-graph waits for the fences of its shared buffers that another plan or
 * another thread recorded; after the run, it records a fence and becomes the last user of those
 * buffers. A reader therefore never starts before the writer it follows has finished, and a
 * writer never starts before the readers it follows have finished, whether or not
 * {@code execute()} blocks the host.
 * </p>
 *
 * <p>
 * Backends wait on the device where they can ({@link TornadoXPUDevice#waitFence}), so an
 * independent plan keeps running in parallel and the host only blocks where the backend needs it.
 * </p>
 */
final class SharedBufferFences {

    /** Shared buffer to the fence of the last run that used it. Guarded by itself. */
    private static final Map<XPUBuffer, Fence> LAST_USE = new IdentityHashMap<>();

    private SharedBufferFences() {
    }

    /** A fence of one run, referenced by every shared buffer whose last user that run is. */
    private static final class Fence {
        final TornadoXPUDevice device;
        final long executionPlanId;
        final long threadId;
        final Object handle;
        /** Buffers and in-progress waits holding this fence. Guarded by {@link #LAST_USE}. */
        int references;

        Fence(TornadoXPUDevice device, long executionPlanId, long threadId, Object handle) {
            this.device = device;
            this.executionPlanId = executionPlanId;
            this.threadId = threadId;
            this.handle = handle;
        }
    }

    /**
     * Makes the calling thread's next work for the plan wait for the last use of each buffer by
     * another plan or another thread. Buffers seen for the first time start being tracked.
     *
     * @param onHost
     *     wait on the host instead of the device: needed when the plan spreads its work over
     *     several queues (intra-plan concurrency), since a device wait orders only one of them
     */
    static void acquire(Collection<XPUBuffer> buffers, TornadoXPUDevice device, long executionPlanId, boolean onHost) {
        if (buffers.isEmpty()) {
            return;
        }
        final long threadId = Thread.currentThread().threadId();
        List<Fence> pending = new ArrayList<>();
        synchronized (LAST_USE) {
            Set<Fence> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (XPUBuffer buffer : buffers) {
                Fence fence = LAST_USE.get(buffer);
                if (!LAST_USE.containsKey(buffer)) {
                    LAST_USE.put(buffer, null);
                }
                if (fence == null || (fence.executionPlanId == executionPlanId && fence.threadId == threadId)) {
                    // nothing pending, or the same queue: it runs in order already
                    continue;
                }
                if (seen.add(fence)) {
                    fence.references++;
                    pending.add(fence);
                }
            }
        }
        // Wait outside the lock: a backend may wait on the host, and other plans must still be
        // able to record their fences meanwhile. The references keep the fences alive.
        try {
            for (Fence fence : pending) {
                if (onHost) {
                    fence.device.awaitFence(fence.handle);
                } else {
                    device.waitFence(executionPlanId, fence.handle);
                }
            }
        } finally {
            synchronized (LAST_USE) {
                for (Fence fence : pending) {
                    dropReference(fence);
                }
            }
        }
    }

    /**
     * Records a fence after the calling thread's work for the plan and makes it the last use of
     * each buffer.
     */
    static void publish(Collection<XPUBuffer> buffers, TornadoXPUDevice device, long executionPlanId) {
        if (buffers.isEmpty()) {
            return;
        }
        Object handle = device.recordFence(executionPlanId);
        Fence fence = handle == null ? null : new Fence(device, executionPlanId, Thread.currentThread().threadId(), handle);
        synchronized (LAST_USE) {
            for (XPUBuffer buffer : buffers) {
                if (fence != null) {
                    fence.references++;
                }
                Fence previous = LAST_USE.put(buffer, fence);
                if (previous != null) {
                    dropReference(previous);
                }
            }
        }
    }

    /**
     * Stops tracking the buffers, after waiting on the host for their last use. Called before the
     * buffers are released, so no plan still reads or writes them on the device.
     */
    static void retire(Collection<XPUBuffer> buffers) {
        if (buffers.isEmpty()) {
            return;
        }
        List<Fence> pending = new ArrayList<>();
        synchronized (LAST_USE) {
            for (XPUBuffer buffer : buffers) {
                Fence fence = LAST_USE.remove(buffer);
                if (fence != null) {
                    // the reference the buffer held now belongs to this wait
                    pending.add(fence);
                }
            }
        }
        try {
            for (Fence fence : pending) {
                fence.device.awaitFence(fence.handle);
            }
        } finally {
            synchronized (LAST_USE) {
                for (Fence fence : pending) {
                    dropReference(fence);
                }
            }
        }
    }

    private static void dropReference(Fence fence) {
        if (--fence.references == 0) {
            fence.device.releaseFence(fence.handle);
        }
    }
}
