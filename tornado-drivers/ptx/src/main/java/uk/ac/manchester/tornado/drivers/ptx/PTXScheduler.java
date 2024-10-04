/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022 APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.FULL_DEBUG;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXScheduler {

    private final PTXDevice device;
    private final TornadoLogger logger;

    public PTXScheduler(final PTXDevice device) {
        this.device = device;
        this.logger = new TornadoLogger(this.getClass());
    }

    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        if (meta.isGlobalWorkDefined()) {
            return;
        }

        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            globalWork[i] = value;
        }
    }

    public int[] calculateBlockDimension(PTXModule module, TaskDataContext taskMeta) {
        if (taskMeta.isLocalWorkDefined()) {
            return Arrays.stream(taskMeta.getLocalWork()).mapToInt(l -> (int) l).toArray();
        }

        long maxThreadsPerBlock = taskMeta.getXPUDevice().getPhysicalDevice().getMaxThreadsPerBlock();
        if (taskMeta.getDims() > 1) {
            maxThreadsPerBlock = module.getPotentialBlockSizeMaxOccupancy();
        }
        return calculateBlockDimension(taskMeta.getGlobalWork(), maxThreadsPerBlock, taskMeta.getDims(), module.javaName);
    }

    public int[] calculateBlockDimension(long[] globalWork, long maxThreadBlocks, int dimension, String javaName) {
        int[] defaultBlocks = { 1, 1, 1 };
        try {
            long maxBlockThreads = maxThreadBlocks;
            for (int i = 0; i < dimension; i++) {
                defaultBlocks[i] = calculateBlockSize(calculateEffectiveMaxWorkItemSize(dimension, maxBlockThreads), globalWork[i]);
            }
        } catch (Exception e) {
            logger.warn("[CUDA-PTX] Failed to calculate blocks for " + javaName);
            logger.warn("[CUDA-PTX] Falling back to blocks: " + Arrays.toString(defaultBlocks));
            if (DEBUG || FULL_DEBUG) {
                e.printStackTrace();
            }
            throw new TornadoBailoutRuntimeException("[Error During Block Size compute] ", e);
        }
        return defaultBlocks;
    }

    private long calculateEffectiveMaxWorkItemSize(int dimension, long threads) {
        if (dimension == 0) {
            shouldNotReachHere();
        }
        return (long) Math.pow(threads, (double) 1 / dimension);
    }

    private int calculateBlockSize(long maxBlockSize, long globalWorkSize) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(maxBlockSize, globalWorkSize);
        if (value == 0) {
            return 1;
        }
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }

    public int[] calculateGridDimension(PTXModule module, TaskDataContext taskMeta, int[] blockDimension) {
        int[] globalWork = Arrays.stream(taskMeta.getGlobalWork()).mapToInt(l -> (int) l).toArray();
        return calculateGridDimension(module.javaName, taskMeta.getDims(), globalWork, blockDimension);
    }

    public int[] calculateGridDimension(String javaName, int dimension, int[] globalWork, int[] blockDimension) {
        int[] defaultGrids = { 1, 1, 1 };

        try {
            long[] maxGridSizes = device.getDeviceMaxWorkGroupSize();

            for (int i = 0; i < dimension; i++) {
                int workSize = globalWork[i];
                defaultGrids[i] = Math.max(Math.min(workSize / blockDimension[i], (int) maxGridSizes[i]), 1);
            }
        } catch (Exception e) {
            logger.warn("[CUDA-PTX] Failed to calculate grids for " + javaName);
            logger.warn("[CUDA-PTX] Falling back to grid: " + Arrays.toString(defaultGrids));
            if (DEBUG || FULL_DEBUG) {
                e.printStackTrace();
            }
            throw new TornadoBailoutRuntimeException("[Error During Grid Size compute] ", e);
        }
        return defaultGrids;
    }
}
