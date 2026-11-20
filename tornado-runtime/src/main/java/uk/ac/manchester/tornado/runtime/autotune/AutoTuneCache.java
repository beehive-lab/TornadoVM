/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.autotune;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.TornadoDevice;

/**
 * Process-wide, in-memory cache of tuning results keyed by
 * {@code (device, taskId, kernelSignatureHash, globalWorkSize)}. A task is benchmarked once per key for
 * the lifetime of the JVM, across {@code execute()} calls and across {@code TornadoExecutionPlan}
 * instances.
 *
 * <p>
 * The key does not include the metric, the strategy type, the custom candidates or any user
 * {@code WorkerGrid}. The map is unbounded and entries are never evicted. A result is stored even if the
 * search ended early because of the config's time budget.
 * </p>
 */
public final class AutoTuneCache {

    private record CacheKey(TornadoDevice device, String taskId, long kernelSignatureHash, long[] globalWorkSize) {

        /** Compares {@code globalWorkSize} by content; the record default would compare the array by reference. */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CacheKey other)) {
                return false;
            }
            return kernelSignatureHash == other.kernelSignatureHash && device.equals(other.device) && taskId.equals(other.taskId) && Arrays.equals(globalWorkSize, other.globalWorkSize);
        }

        @Override
        public int hashCode() {
            int result = device.hashCode();
            result = 31 * result + taskId.hashCode();
            result = 31 * result + Long.hashCode(kernelSignatureHash);
            result = 31 * result + Arrays.hashCode(globalWorkSize);
            return result;
        }
    }

    private static final Map<CacheKey, AutoTuneResult> RESULTS = new ConcurrentHashMap<>();

    private AutoTuneCache() {
    }

    /** Returns the cached result for this key, if any. */
    public static Optional<AutoTuneResult> get(TornadoDevice device, String taskId, long kernelSignatureHash, long[] globalWorkSize) {
        CacheKey key = new CacheKey(device, taskId, kernelSignatureHash, globalWorkSize);
        return Optional.ofNullable(RESULTS.get(key));
    }

    /** Stores a result under the key formed by its own task ID, device, kernel signature hash and global work size, replacing any existing entry. */
    public static void put(AutoTuneResult result) {
        CacheKey key = new CacheKey(result.getDevice(), result.getTaskId(), result.getKernelSignatureHash(), result.getGlobalWorkSize());
        RESULTS.put(key, result);
    }
}