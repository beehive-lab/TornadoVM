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
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.lang.foreign.MemorySegment;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;

/**
 * Central, refcounted bookkeeping for host memory pinned via {@code cuMemHostRegister}.
 * One instance per {@link CUDAContext}; every register/unregister of user host memory
 * on the CUDA backend must go through here.
 *
 * <p>Pinning host segments lets async transfers DMA directly (no driver staging copy,
 * true transfer/compute overlap), but a registration that outlives its segment is a
 * data hazard: when the segment is freed and its virtual address is reused by a new
 * allocation, the driver's stale pin still maps the OLD physical pages, so transfers
 * silently read/write the wrong memory. Per-buffer "do I own the pin" booleans (the
 * PTX backend's scheme) cannot track this once buffers alias or bulk-release paths
 * skip the per-buffer free hook; this registry closes those holes:
 * <ul>
 *   <li><b>Refcounting</b>: several holders of the same live segment share one driver
 *       registration.</li>
 *   <li><b>Pin caching</b>: releasing the last hold does NOT unregister - the driver
 *       pin is kept cached so the alloc/free buffer churn across repeated executions
 *       costs no {@code cuMemHostRegister}/{@code cuMemHostUnregister} calls (each is
 *       tens of microseconds plus a context synchronisation, which destroys
 *       multi-stream overlap when paid per execution).</li>
 *   <li><b>Provable-liveness revival</b>: a cached pin is revived only when the request
 *       presents the SAME {@link MemorySegment} instance (tracked by weak reference).
 *       A new segment at a reused address is a different instance, and a collected
 *       segment clears the weak reference - both force unregister + fresh register, so
 *       DMA always sees the current physical pages. This is the stale-pin detection the
 *       PTX backend lacks.</li>
 *   <li><b>Sync before unpin</b>: the native unregister synchronises the context first,
 *       so no in-flight async DMA can still touch a region being unpinned.</li>
 *   <li><b>External pins are never stolen</b>: if the driver reports the range as
 *       already registered by someone outside this registry (e.g. the PTX backend
 *       sharing the process), the entry is tracked as {@code external} and this
 *       registry never unregisters it.</li>
 *   <li><b>Drain on plan teardown</b> ({@link #unpinAll()}): bulk buffer releases
 *       bypass per-buffer free hooks, so the device context drains the registry on
 *       {@code reset()}; cached-but-stale pins are also reclaimed there.</li>
 * </ul>
 *
 * <p>A cached pin whose segment has died is never a transfer hazard: user-segment DMA
 * always goes through a wrapper whose {@code allocate()} re-validates the pin here
 * first. Until the next revival or drain it only holds page-locked pages, which is a
 * bounded memory cost, not a correctness one.
 */
public final class CUDAPinnedMemoryRegistry {

    /** Kill switch: {@code -Dtornado.cuda.host.pinning=false} disables pinning (pageable transfers). */
    public static final boolean HOST_PINNING_ENABLED = Boolean.parseBoolean(System.getProperty("tornado.cuda.host.pinning", "True"));

    private static final class Registration {
        long numBytes;
        int refCount;
        /** Pinned by someone outside this registry (never unregister it here). */
        boolean external;
        /** Identity of the live segment this pin covers; cleared by GC when the segment dies. */
        WeakReference<MemorySegment> segmentRef;

        Registration(long numBytes, int refCount, boolean external, MemorySegment segment) {
            this.numBytes = numBytes;
            this.refCount = refCount;
            this.external = external;
            this.segmentRef = new WeakReference<>(segment);
        }
    }

    private final CUDAContext context;
    private final Map<Long, Registration> registrations = new HashMap<>();

    public CUDAPinnedMemoryRegistry(CUDAContext context) {
        this.context = context;
    }

    /**
     * Ensures the host region backing {@code segment} is pinned, registering it with the
     * driver on first use, refcounting repeats, and reviving a cached pin only when it
     * provably belongs to this same live segment.
     *
     * @return true when the region is pinned (async DMA will bypass the staging copy)
     */
    public synchronized boolean pin(MemorySegment segment, long numBytes) {
        if (!HOST_PINNING_ENABLED || segment == null || numBytes <= 0) {
            return false;
        }
        long hostPointer = segment.address();
        if (hostPointer == 0) {
            return false;
        }
        Registration entry = registrations.get(hostPointer);
        if (entry != null) {
            if (entry.numBytes == numBytes && entry.segmentRef.get() == segment) {
                // Same live segment: share (or revive) the existing driver pin.
                entry.refCount++;
                return true;
            }
            // Different instance or size at a known base: the old segment died and the
            // address was reused. Drop the stale pin (native side syncs first) and fall
            // through to register the current physical pages.
            if (!entry.external) {
                context.unregisterPinnedMemory(hostPointer);
            }
            registrations.remove(hostPointer);
        }
        int result = context.registerPinnedMemory(hostPointer, numBytes);
        if (result == 0) {
            registrations.put(hostPointer, new Registration(numBytes, 1, false, segment));
            return true;
        }
        if (result == CUDAContext.CUDA_ERROR_HOST_MEMORY_ALREADY_REGISTERED) {
            // Pinned by someone outside this registry (e.g. the PTX backend in the same
            // process). The memory IS page-locked, so DMA works; track it but never
            // unregister a pin we do not own.
            registrations.put(hostPointer, new Registration(numBytes, 1, true, segment));
            return true;
        }
        return false;
    }

    /**
     * Releases one hold on the region. The driver pin is deliberately kept (cached) even
     * when the last holder releases: buffer alloc/free churn across repeated executions
     * would otherwise pay register + synchronised-unregister per cycle. The cached pin is
     * revived, replaced (stale) or drained by {@link #pin} / {@link #unpinAll}.
     */
    public synchronized void unpin(long hostPointer) {
        Registration entry = registrations.get(hostPointer);
        if (entry != null && entry.refCount > 0) {
            entry.refCount--;
        }
    }

    /**
     * Drops every owned pin regardless of refcount, including cached ones. Called on plan
     * teardown ({@code CUDADeviceContext#reset}): bulk buffer releases bypass the
     * per-buffer free hook, and a pin left behind there is exactly the stale-pin hazard.
     * Live segments simply degrade to pageable transfers until their next allocate re-pins.
     */
    public synchronized void unpinAll() {
        for (Map.Entry<Long, Registration> entry : registrations.entrySet()) {
            if (!entry.getValue().external) {
                context.unregisterPinnedMemory(entry.getKey());
            }
        }
        registrations.clear();
    }
}
