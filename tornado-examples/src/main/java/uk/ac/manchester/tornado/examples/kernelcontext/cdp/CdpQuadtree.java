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
package uk.ac.manchester.tornado.examples.kernelcontext.cdp;

import java.util.Random;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Port of {@code cdpQuadtree} from NVIDIA's cuda-samples ({@code cpp/3_CUDA_Features/cdpQuadtree}) to CUDA Dynamic
 * Parallelism in TornadoVM ({@link KernelContext#launch}). Builds a quadtree of random points. One block per node counts its points per quadrant with warp votes, moves them into the next buffer grouped by quadrant, and launches one block per child.
 *
 * <p>
 * The same kernels are tested in {@code TestCudaSamplesDynamicParallelism}. How to run (CUDA backend):
 * </p>
 *
 * <pre>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.cdp.CdpQuadtree
 * </pre>
 */
public class CdpQuadtree {

    private static GridScheduler grid(String task, int global, int local) {
        WorkerGrid worker = new WorkerGrid1D(global);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    private static final int QT_THREADS = 128;
    private static final int QT_WARPS = QT_THREADS / 32;

    private static final DeviceKernel QUADTREE = DeviceKernel.of(CdpQuadtree::buildQuadtreeKernel);

    /** Index of the first node of {@code level}: 1 + 4 + ... + 4^(level-1). */
    private static int nodeLevelBase(int level) {
        int base = 0;
        int count = 1;
        for (int l = 0; l < level; l++) {
            base += count;
            count *= 4;
        }
        return base;
    }

    /**
     * build_quadtree_kernel: one block per node. Warps count the node's points per quadrant with
     * ballots, the block turns the counts into offsets, the points are moved into the other buffer
     * grouped by quadrant, and the last thread launches one block per child. Nodes are
     * {@code nodeLevelBase(level) + id}, with bounding boxes as (min x, min y, max x, max y) and point
     * ranges [begin, end). {@code points} holds two buffers of n x-coordinates followed by n
     * y-coordinates; {@code selector} is the one holding this level's input.
     */
    private static void buildQuadtreeKernel(KernelContext context, FloatArray bbox, IntArray begin, IntArray end, FloatArray points, int n, int level, int firstId, int selector,
            int maxDepth, int minPointsPerNode) {
        int tid = context.localIdx;
        int warpId = tid / 32;
        int lane = tid % 32;
        int laneMaskLt = (1 << lane) - 1;

        int id = firstId + context.groupIdx;
        int node = nodeLevelBase(level) + id;
        int pointsBegin = begin.get(node);
        int pointsEnd = end.get(node);
        int numPoints = pointsEnd - pointsBegin;
        int in = selector * 2 * n;
        int out = (1 - selector) * 2 * n;

        // 1- Stop the recursion here, with all the points in buffer 0.
        if (level >= maxDepth | numPoints <= minPointsPerNode) {
            if (selector == 1) {
                for (int it = pointsBegin + tid; it < pointsEnd; it += QT_THREADS) {
                    points.set(it, points.get(in + it));
                    points.set(n + it, points.get(in + n + it));
                }
            }
            return;
        }

        // Allocated after the leaf return: a local array that some paths never reach is not scheduled.
        int[] smem = context.allocateIntLocalArray(4 * QT_WARPS);
        float minX = bbox.get(node * 4);
        float minY = bbox.get(node * 4 + 1);
        float maxX = bbox.get(node * 4 + 2);
        float maxY = bbox.get(node * 4 + 3);
        float centerX = 0.5f * (minX + maxX);
        float centerY = 0.5f * (minY + maxY);

        int pointsPerWarp = Math.max(32, (numPoints + QT_WARPS - 1) / QT_WARPS);
        int rangeBegin = pointsBegin + warpId * pointsPerWarp;
        int rangeEnd = Math.min(rangeBegin + pointsPerWarp, pointsEnd);

        // 2- Count the number of points in each child.
        int c0 = 0;
        int c1 = 0;
        int c2 = 0;
        int c3 = 0;
        for (int it = rangeBegin + lane; context.simdAny(it < rangeEnd); it += 32) {
            boolean active = it < rangeEnd;
            float px = 0.0f;
            float py = 0.0f;
            if (active) {
                px = points.get(in + it);
                py = points.get(in + n + it);
            }
            c0 += Integer.bitCount(context.simdBallot(active & px < centerX & py >= centerY));
            c1 += Integer.bitCount(context.simdBallot(active & px >= centerX & py >= centerY));
            c2 += Integer.bitCount(context.simdBallot(active & px < centerX & py < centerY));
            c3 += Integer.bitCount(context.simdBallot(active & px >= centerX & py < centerY));
        }
        if (lane == 0) {
            smem[warpId] = c0;
            smem[QT_WARPS + warpId] = c1;
            smem[2 * QT_WARPS + warpId] = c2;
            smem[3 * QT_WARPS + warpId] = c3;
        }
        context.localBarrier();

        // 3- Scan the warps' counts, quadrant by quadrant, into each warp's first output slot.
        if (tid == 0) {
            int offset = pointsBegin;
            for (int k = 0; k < 4 * QT_WARPS; k++) {
                int count = smem[k];
                smem[k] = offset;
                offset += count;
            }
        }
        context.localBarrier();

        // 4- Move the points, grouped by quadrant, into the other buffer.
        c0 = smem[warpId];
        c1 = smem[QT_WARPS + warpId];
        c2 = smem[2 * QT_WARPS + warpId];
        c3 = smem[3 * QT_WARPS + warpId];
        for (int it = rangeBegin + lane; context.simdAny(it < rangeEnd); it += 32) {
            boolean active = it < rangeEnd;
            float px = 0.0f;
            float py = 0.0f;
            if (active) {
                px = points.get(in + it);
                py = points.get(in + n + it);
            }
            boolean p0 = active & px < centerX & py >= centerY;
            int vote = context.simdBallot(p0);
            if (p0) {
                int dest = c0 + Integer.bitCount(vote & laneMaskLt);
                points.set(out + dest, px);
                points.set(out + n + dest, py);
            }
            c0 += Integer.bitCount(vote);
            boolean p1 = active & px >= centerX & py >= centerY;
            vote = context.simdBallot(p1);
            if (p1) {
                int dest = c1 + Integer.bitCount(vote & laneMaskLt);
                points.set(out + dest, px);
                points.set(out + n + dest, py);
            }
            c1 += Integer.bitCount(vote);
            boolean p2 = active & px < centerX & py < centerY;
            vote = context.simdBallot(p2);
            if (p2) {
                int dest = c2 + Integer.bitCount(vote & laneMaskLt);
                points.set(out + dest, px);
                points.set(out + n + dest, py);
            }
            c2 += Integer.bitCount(vote);
            boolean p3 = active & px >= centerX & py < centerY;
            vote = context.simdBallot(p3);
            if (p3) {
                int dest = c3 + Integer.bitCount(vote & laneMaskLt);
                points.set(out + dest, px);
                points.set(out + n + dest, py);
            }
            c3 += Integer.bitCount(vote);
        }
        context.localBarrier();
        if (lane == 0) {
            smem[warpId] = c0;
            smem[QT_WARPS + warpId] = c1;
            smem[2 * QT_WARPS + warpId] = c2;
            smem[3 * QT_WARPS + warpId] = c3;
        }
        context.localBarrier();

        // 5- The last thread sets up the four children and launches one block per child.
        if (tid == QT_THREADS - 1) {
            int children = nodeLevelBase(level + 1) + 4 * id;
            int last = QT_WARPS - 1;
            bbox.set(children * 4, minX);
            bbox.set(children * 4 + 1, centerY);
            bbox.set(children * 4 + 2, centerX);
            bbox.set(children * 4 + 3, maxY);
            bbox.set((children + 1) * 4, centerX);
            bbox.set((children + 1) * 4 + 1, centerY);
            bbox.set((children + 1) * 4 + 2, maxX);
            bbox.set((children + 1) * 4 + 3, maxY);
            bbox.set((children + 2) * 4, minX);
            bbox.set((children + 2) * 4 + 1, minY);
            bbox.set((children + 2) * 4 + 2, centerX);
            bbox.set((children + 2) * 4 + 3, centerY);
            bbox.set((children + 3) * 4, centerX);
            bbox.set((children + 3) * 4 + 1, minY);
            bbox.set((children + 3) * 4 + 2, maxX);
            bbox.set((children + 3) * 4 + 3, centerY);
            begin.set(children, pointsBegin);
            end.set(children, smem[last]);
            begin.set(children + 1, smem[last]);
            end.set(children + 1, smem[QT_WARPS + last]);
            begin.set(children + 2, smem[QT_WARPS + last]);
            end.set(children + 2, smem[2 * QT_WARPS + last]);
            begin.set(children + 3, smem[2 * QT_WARPS + last]);
            end.set(children + 3, smem[3 * QT_WARPS + last]);
            context.launch(QUADTREE, 4 * QT_THREADS, QT_THREADS, bbox, begin, end, points, n, level + 1, 4 * id, 1 - selector, maxDepth, minPointsPerNode);
        }
    }

    /** check_quadtree: children partition their parent's points, and every leaf's points lie in its box. */
    private static boolean checkQuadtree(FloatArray bbox, IntArray begin, IntArray end, FloatArray points, int n, int level, int id, int maxDepth, int minPointsPerNode) {
        int node = nodeLevelBase(level) + id;
        int numPoints = end.get(node) - begin.get(node);
        if (!(level >= maxDepth || numPoints <= minPointsPerNode)) {
            int children = nodeLevelBase(level + 1) + 4 * id;
            int inChildren = 0;
            for (int q = 0; q < 4; q++) {
                inChildren += end.get(children + q) - begin.get(children + q);
            }
            if (inChildren != numPoints) {
                return false;
            }
            for (int q = 0; q < 4; q++) {
                if (!checkQuadtree(bbox, begin, end, points, n, level + 1, 4 * id + q, maxDepth, minPointsPerNode)) {
                    return false;
                }
            }
            return true;
        }
        for (int it = begin.get(node); it < end.get(node); it++) {
            float x = points.get(it);
            float y = points.get(n + it);
            if (it >= n || x < bbox.get(node * 4) || x > bbox.get(node * 4 + 2) || y < bbox.get(node * 4 + 1) || y > bbox.get(node * 4 + 3)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int numPoints = 1024;
        final int maxDepth = 8;
        final int minPointsPerNode = 16;
        final int maxNodes = nodeLevelBase(maxDepth + 1);
        Random random = new Random(42);
        FloatArray points = new FloatArray(2 * 2 * numPoints);
        for (int i = 0; i < numPoints; i++) {
            points.set(i, random.nextFloat());
            points.set(numPoints + i, random.nextFloat());
        }
        FloatArray bbox = new FloatArray(maxNodes * 4);
        IntArray begin = new IntArray(maxNodes);
        IntArray end = new IntArray(maxNodes);
        bbox.set(2, 1.0f);
        bbox.set(3, 1.0f);
        end.set(0, numPoints);
        System.out.println("GPU quadtree of " + numPoints + " points, max depth " + maxDepth + ", at most " + minPointsPerNode + " points per leaf");
        TaskGraph taskGraph = new TaskGraph("quadtree") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, bbox, begin, end, points) //
                .task("cdp", CdpQuadtree::buildQuadtreeKernel, new KernelContext(), bbox, begin, end, points, numPoints, 0, 0, 0, maxDepth, minPointsPerNode) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bbox, begin, end, points);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("quadtree.cdp", QT_THREADS, QT_THREADS)).execute();
        }
        boolean ok = checkQuadtree(bbox, begin, end, points, numPoints, 0, 0, maxDepth, minPointsPerNode);
        System.out.println(ok ? "Results: OK" : "Results: FAILED");
    }
}
