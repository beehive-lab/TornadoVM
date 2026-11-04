/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.options.CudaFlags;
import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;

/**
 * Central, refcounted bookkeeping for host memory pinned via {@code cuMemHostRegister}.
 * One instance per {@link CUDAContext}; every register/unregister of user host memory
 * on the CUDA backend must go through here.
 *
 * <p>Pinning host segments lets async transfers DMA directly (no driver staging copy,
 * true transfer/compute overlap), but a registration that outlives its memory is a
 * hazard: once the memory is freed and reused by a new allocation, a transfer from the
 * new allocation can span registered and pageable pages, which the driver rejects
 * ({@code cuMemcpyHtoDAsync}: {@code CUDA_ERROR_INVALID_VALUE}, beehive-lab/TornadoVM#1295),
 * or read through a stale registration. This registry closes those holes:
 * <ul>
 *   <li><b>Registered memory is never freed</b>: a registration holds its segment, so
 *       the memory (an automatic arena's) stays allocated while it is registered. When
 *       the object that owns the segment (the {@code FloatArray}, ...) is collected, a
 *       {@link Cleaner} queues the unregistration, and the segment is let go only once
 *       the driver has unregistered it. This holds for plans that are dropped without
 *       {@code close()}, whose buffers never release their holds.</li>
 *   <li><b>Refcounting</b>: several holders of the same live segment share one driver
 *       registration.</li>
 *   <li><b>Pin caching</b>: releasing the last hold does NOT unregister - the driver
 *       pin is kept cached so the alloc/free buffer churn across repeated executions
 *       costs no {@code cuMemHostRegister}/{@code cuMemHostUnregister} calls (each is
 *       tens of microseconds plus a context synchronisation, which destroys
 *       multi-stream overlap when paid per execution).</li>
 *   <li><b>Revival of the same segment only</b>: a cached pin is revived only when the
 *       request presents the SAME {@link MemorySegment} instance. Another segment at the
 *       same address (a view of the same memory) stays pageable.</li>
 *   <li><b>Sync before unpin, off the plan threads</b>: unregistering synchronises every
 *       context first, so no in-flight async DMA can still touch a region being unpinned.
 *       It runs on the context's unpinning thread: the driver also synchronises every device
 *       there, and a thread running plans could block on another thread's work (an NCCL
 *       collective waiting for this thread's next one). A region being unregistered stays
 *       pageable until that has finished.</li>
 *   <li><b>External pins are never stolen</b>: if the driver reports the range as
 *       already registered by someone outside this registry the entry is tracked
 *       as {@code external} and this registry never unregisters it.</li>
 *   <li><b>Drain on plan teardown</b> ({@link #unpinAll()}): bulk buffer releases
 *       bypass per-buffer free hooks, so the device context drains the registry on
 *       {@code reset()} once no execution plan is left ({@link #unpinAll()}), and while
 *       other plans are alive drops only the pins no live buffer holds
 *       ({@link #unpinUnheld()}).</li>
 * </ul>
 */
public final class CUDAPinnedMemoryRegistry {

    /** Kill switch: {@code -Dtornado.cuda.host.pinning=false} disables pinning (pageable transfers). */
    public static final boolean HOST_PINNING_ENABLED = CudaFlags.HOST_PINNING.get();

    /**
     * {@code -Dtornado.cuda.host.pinning.all=true} pins the host memory of every device buffer. By
     * default only the buffers a task-graph transfers on every execution are pinned: one-shot
     * uploads (weights, inputs set once) and device-only working buffers gain nothing from it,
     * and pinning gigabytes of them per plan makes the driver fail allocations and copies when
     * plans are created and closed often.
     */
    public static final boolean PIN_ALL_BUFFERS = CudaFlags.HOST_PINNING_ALL.get();

    /** Unregisters the pins of segments whose owners were collected. */
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class Registration {
        final long numBytes;
        int refCount;
        /** Pinned by someone outside this registry (never unregister it here). */
        final boolean external;
        /**
         * The registered segment, held so that its memory is not freed while registered; null
         * for an external pin, and once the registration is dropped.
         */
        MemorySegment segment;
        /** Unregisters this pin when the segment's owner is collected; null for an external pin. */
        Cleaner.Cleanable ownerWatch;

        Registration(long numBytes, int refCount, boolean external, MemorySegment segment) {
            this.numBytes = numBytes;
            this.refCount = refCount;
            this.external = external;
            this.segment = segment;
        }
    }

    private final CUDAContext context;

    private final Map<Long, Registration> registrations = new HashMap<>();

    /** Regions whose unregistration is queued on the context's unpinning thread, with their segments. */
    private final Map<Long, MemorySegment> unpinning = new HashMap<>();

    public CUDAPinnedMemoryRegistry(CUDAContext context) {
        this.context = context;
    }

    /**
     * Ensures the host region backing {@code segment} is pinned, registering it with the
     * driver on first use, refcounting repeats, and reviving a cached pin only when it
     * belongs to this same segment.
     *
     * @param owner
     *     the object that owns {@code segment} (a native array, collection, image, ...): its
     *     collection unregisters the pin. It must not be reachable from the segment.
     * @return true when the region is pinned (async DMA will bypass the staging copy)
     */
    public synchronized boolean pin(Object owner, MemorySegment segment, long numBytes) {
        if (!HOST_PINNING_ENABLED || segment == null || numBytes <= 0) {
            return false;
        }
        long hostPointer = segment.address();
        if (hostPointer == 0 || unpinning.containsKey(hostPointer)) {
            // a region still being unregistered stays pageable this time
            return false;
        }
        Registration entry = registrations.get(hostPointer);
        if (entry != null) {
            if (entry.numBytes == numBytes && (entry.external || entry.segment == segment)) {
                // Same segment: share (or revive) the existing driver pin.
                entry.refCount++;
                return true;
            }
            // Another segment over the same memory (registered segments are kept alive, so the
            // memory cannot have been reused): leave the pin to its segment, stay pageable.
            return false;
        }
        int result = context.registerPinnedMemory(hostPointer, numBytes);
        if (result == 0) {
            Registration registration = new Registration(numBytes, 1, false, segment);
            registration.ownerWatch = CLEANER.register(owner, () -> ownerCollected(hostPointer, registration));
            registrations.put(hostPointer, registration);
            return true;
        }
        if (result == CUDAContext.CUDA_ERROR_HOST_MEMORY_ALREADY_REGISTERED) {
            // Pinned by someone outside this registry
            // The memory IS page-locked, so DMA works; track it but never
            // unregister a pin we do not own.
            registrations.put(hostPointer, new Registration(numBytes, 1, true, null));
            return true;
        }
        return false;
    }

    /**
     * Releases one hold on the region. The driver pin is deliberately kept (cached) even
     * when the last holder releases: buffer alloc/free churn across repeated executions
     * would otherwise pay register + synchronised-unregister per cycle. The cached pin is
     * revived by {@link #pin}, or dropped when its owner is collected or by
     * {@link #unpinUnheld} / {@link #unpinAll}.
     */
    public synchronized void unpin(long hostPointer) {
        Registration entry = registrations.get(hostPointer);
        if (entry != null && entry.refCount > 0) {
            entry.refCount--;
        }
    }

    /**
     * Drops the owned pins that no live buffer holds (cached pins, refcount 0). Pins still held
     * stay registered. Called on the teardown of one execution plan while others are alive
     * ({@code CUDADeviceContext#reset}): their captured CUDA graphs copy from the pinned host
     * buffers they hold, and unregistering those under them crashes the next graph launch.
     */
    public synchronized void unpinUnheld() {
        var it = registrations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Registration> entry = it.next();
            if (entry.getValue().refCount == 0) {
                it.remove();
                drop(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Drops every owned pin regardless of refcount, including cached ones. Called on plan
     * teardown ({@code CUDADeviceContext#reset}): bulk buffer releases bypass the
     * per-buffer free hook, and a pin left behind there is never released by a holder.
     * Live segments simply degrade to pageable transfers until their next allocate re-pins.
     */
    public synchronized void unpinAll() {
        // Out of the map first: drop() runs the owner watch, which looks the registration up.
        var all = new HashMap<>(registrations);
        registrations.clear();
        all.forEach(this::drop);
    }

    /** The owner of a registered segment was collected: its pin goes, held or not. */
    private synchronized void ownerCollected(long hostPointer, Registration registration) {
        if (registrations.get(hostPointer) == registration) {
            registrations.remove(hostPointer);
            drop(hostPointer, registration);
        }
    }

    /** A registration removed from the map: unregisters an owned pin, keeping its memory until then. */
    private void drop(long hostPointer, Registration registration) {
        MemorySegment segment = registration.segment;
        registration.segment = null;
        if (registration.ownerWatch != null) {
            // An owner that is still alive needs no watching now: clean() deregisters it and runs
            // ownerCollected, a no-op for a registration no longer in the map.
            Cleaner.Cleanable watch = registration.ownerWatch;
            registration.ownerWatch = null;
            watch.clean();
        }
        if (!registration.external) {
            unregisterLater(hostPointer, segment);
        }
    }

    /**
     * Queues the unregistration of an owned pin on the context's unpinning thread: unregistering
     * synchronises every device, which can block a thread running plans on another thread's
     * collective (see {@link CUDAContext#unregisterPinnedMemoryLater}). The segment stays
     * reachable until then, so its memory is not freed while it is registered.
     */
    private void unregisterLater(long hostPointer, MemorySegment segment) {
        unpinning.put(hostPointer, segment);
        context.unregisterPinnedMemoryLater(hostPointer, () -> {
            synchronized (this) {
                unpinning.remove(hostPointer);
            }
        });
    }
}
