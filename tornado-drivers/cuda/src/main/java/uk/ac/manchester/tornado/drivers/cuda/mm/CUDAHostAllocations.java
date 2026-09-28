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

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import uk.ac.manchester.tornado.api.memory.HostMemoryType;

/**
 * Host memory allocated page-locked by the CUDA backend ({@code cuMemHostAlloc}), for arrays created
 * with {@code allocate(n, HostMemoryType.PINNED | MAPPED)}. The buffer wrappers look segments up here
 * to skip registering memory that is already page-locked, and to use mapped memory in place.
 */
public final class CUDAHostAllocations {

    /** A page-locked allocation: its size, its type and, when mapped, the device address of its start. */
    public record Allocation(long base, long byteSize, HostMemoryType type, long devicePointer) {
        boolean contains(long address, long bytes) {
            return address >= base && address + bytes <= base + byteSize;
        }
    }

    private static final ConcurrentSkipListMap<Long, Allocation> ALLOCATIONS = new ConcurrentSkipListMap<>();

    private CUDAHostAllocations() {
    }

    public static void register(Allocation allocation) {
        ALLOCATIONS.put(allocation.base(), allocation);
    }

    public static void remove(long base) {
        ALLOCATIONS.remove(base);
    }

    /** The page-locked allocation holding {@code [address, address + bytes)}, or {@code null}. */
    public static Allocation find(long address, long bytes) {
        Map.Entry<Long, Allocation> entry = ALLOCATIONS.floorEntry(address);
        if (entry == null || !entry.getValue().contains(address, bytes)) {
            return null;
        }
        return entry.getValue();
    }
}
