/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.cuda.scheduler;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDAKernel;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Launch geometry for a CUDA Tile kernel.
 *
 * <p>
 * A tile kernel is launched with one thread per block and the tile compiler expands that into
 * whatever thread configuration it chose, so the only correct local work is 1x1x1. The generic
 * schedulers would instead size the block from the occupancy API or fall back to
 * DEFAULT_BLOCK_SIZE, and a tile kernel launched with a 256-thread block produces wrong results
 * rather than an error.
 * </p>
 *
 * <p>
 * Global work is therefore the number of tile blocks: the launch path computes
 * {@code grid = ceil(global / block)}, which with a local work of 1 is exactly the tile-block
 * count the user set on the worker grid.
 * </p>
 */
public class CUDATileScheduler extends CUDAKernelScheduler {

    public CUDATileScheduler(final CUDADeviceContext context) {
        super(context);
    }

    /**
     * Pins the block to one thread before the usual submit path runs.
     *
     * <p>
     * Overriding {@link #calculateLocalWork} alone is not enough: when a worker grid is supplied
     * - which every tile task needs, because the grid is how the tile-block count is expressed -
     * the base class skips local-work calculation entirely and launches with the grid's own local
     * work. A grid with no explicit local work reaches the launch path as null and picks up
     * DEFAULT_BLOCK_SIZE, so the kernel would run with a 256-thread block and silently compute
     * the wrong answer.
     * </p>
     */
    @Override
    public int submit(long executionPlanId, final CUDAKernel kernel, final TaskDataContext meta, final int[] waitEvents, long batchThreads) {
        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            long[] local = grid.getLocalWork();
            if (local == null || local[0] != 1 || local[1] != 1 || local[2] != 1) {
                grid.setLocalWork(1, 1, 1);
            }
        }
        return super.submit(executionPlanId, kernel, meta, waitEvents, batchThreads);
    }

    @Override
    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            // One logical thread per tile block; the domain cardinality already counts blocks.
            globalWork[i] = (batchThreads <= 0) ? (long) meta.getDomain().get(i).cardinality() : batchThreads;
        }
    }

    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        final long[] localWork = meta.initLocalWork();
        for (int i = 0; i < localWork.length; i++) {
            localWork[i] = 1;
        }
    }

    /**
     * Nothing to adapt: one thread per block is already the smallest legal block, and it is the
     * only one a tile kernel may be launched with.
     */
    @Override
    public void checkAndAdaptLocalWork(final TaskDataContext meta) {
    }
}
