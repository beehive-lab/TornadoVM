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
package uk.ac.manchester.tornado.runtime.autotune.spi;

import java.util.List;

import uk.ac.manchester.tornado.api.enums.AutoTuneStrategyType;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Service-provider interface that generates candidate local work-group sizes for a backend, which
 * {@code AutoTuner} then benchmarks. Backend modules implement it and register it with
 * {@code provides AutoTuneSearchStrategy with ...} in their module descriptor, and
 * {@code AutoTuneStrategyRegistry} discovers it through {@link java.util.ServiceLoader}. A task whose
 * device no registered strategy handles keeps the backend's default scheduler.
 *
 * <p>
 * Each list returned by a {@code generateCandidates*} method has {@code null} at index 0 (the backend's
 * default scheduler), then the seed (the task's current local size) if there is one, then the candidates
 * for the requested {@link AutoTuneStrategyType}. {@code AutoTuner} breaks timing ties in favour of the
 * earlier candidate, so index 0 must be the default. The strategy type is a parameter because a
 * registered implementation is a single shared instance.
 * </p>
 */
public interface AutoTuneSearchStrategy {

    /** Returns {@code true} if this strategy can generate candidates for tasks running on {@code device}. */
    boolean canHandle(TornadoXPUDevice device);

       /**
     * Generates candidate local sizes for a 1D task; each entry has length 1. {@code userSuppliedLocalWork}
     * is the task's current local size, or {@code null} if none is set. Throws
     * {@link UnsupportedOperationException} if {@code strategyType} is not supported.
     */
    List<int[]> generateCandidates1D(long globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType);

    /** Generates candidate local sizes for a 2D task; each entry has length 2. See {@link #generateCandidates1D}. */
    List<int[]> generateCandidates2D(long[] globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType);

    /** Generates candidate local sizes for a 3D task; each entry has length 3. See {@link #generateCandidates1D}. */
    List<int[]> generateCandidates3D(long[] globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType);
}