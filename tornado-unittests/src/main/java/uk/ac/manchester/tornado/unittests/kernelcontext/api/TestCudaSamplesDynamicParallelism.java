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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Ports of the CUDA Dynamic Parallelism samples of NVIDIA's cuda-samples
 * ({@code cpp/3_CUDA_Features/cdp*}, https://github.com/NVIDIA/cuda-samples) to
 * {@link KernelContext#launch}. Each test keeps the sample's algorithm and launch structure and checks
 * its result the way the sample does.
 *
 * <ul>
 * <li>{@code cdpSimplePrint}: every thread launches a grid, recursively, to a given depth.</li>
 * <li>{@code cdpSimpleQuicksort}: single-thread quicksort, each side launched into its own stream.</li>
 * <li>{@code cdpBezierTessellation}: one thread per curve picks the tessellation from the curvature and
 * launches a grid of that size.</li>
 * <li>{@code cdpQuadtree}: each block splits its points into four quadrants with warp votes and launches
 * four blocks for the children.</li>
 * </ul>
 *
 * <p>
 * Adaptations: structs become flat arrays and pointers become indices, the atomically allocated block
 * ids of cdpSimplePrint are computed from the launch tree instead, device-side {@code cudaMalloc} in
 * cdpBezierTessellation becomes a buffer preallocated for the maximum tessellation, and the per-launch
 * {@code cudaStreamNonBlocking} streams become {@link DeviceLaunchMode#FIRE_AND_FORGET}.
 * </p>
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestCudaSamplesDynamicParallelism
 * </pre>
 */
public class TestCudaSamplesDynamicParallelism extends TornadoTestBase {

    private void assumeDynamicParallelism() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    private static GridScheduler grid(String task, int global, int local) {
        WorkerGrid worker = new WorkerGrid1D(global);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    // =================================================================== cdpSimplePrint

    private static final DeviceKernel SIMPLE_PRINT = DeviceKernel.of(TestCudaSamplesDynamicParallelism::cdpKernel);

    /** Index of the first block of {@code depth}: two root blocks, and four times as many per level. */
    private static int levelOffset(int depth) {
        int offset = 0;
        int blocks = 2;
        for (int d = 0; d < depth; d++) {
            offset += blocks;
            blocks *= 4;
        }
        return offset;
    }

    /**
     * cdp_kernel: every block records who launched it, then each of its threads launches a grid of the
     * same shape (2 blocks of 2 threads), until {@code maxDepth}. The sample numbers blocks with a global
     * atomic counter; here the id follows from the position in the launch tree: {@code firstIndex} is
     * the index, within its level, of the first block of this launch.
     */
    private static void cdpKernel(KernelContext context, IntArray records, int maxDepth, int depth, int thread, int parentUid, int firstIndex) {
        int index = firstIndex + context.groupIdx;
        int uid = levelOffset(depth) + index;
        if (context.localIdx == 0) {
            records.set(uid * 3, depth);
            records.set(uid * 3 + 1, parentUid);
            records.set(uid * 3 + 2, thread);
        }
        if (depth + 1 < maxDepth) {
            context.launch(SIMPLE_PRINT, 4, 2, records, maxDepth, depth + 1, context.localIdx, uid, index * 4 + context.localIdx * 2);
        }
    }

    @Test
    public void testCdpSimplePrint() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int maxDepth = 4;
        final int blocks = levelOffset(maxDepth);               // 2 + 8 + 32 + 128
        IntArray records = new IntArray(blocks * 3);
        records.init(-2);

        TaskGraph taskGraph = new TaskGraph("print") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, records) //
                .task("cdp", TestCudaSamplesDynamicParallelism::cdpKernel, new KernelContext(), records, maxDepth, 0, 0, -1, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, records);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("print.cdp", 4, 2)).execute();
        }

        // Every block of the launch tree ran once, at its depth, launched by the right thread of the right parent.
        for (int depth = 0; depth < maxDepth; depth++) {
            int count = 2 * (int) Math.pow(4, depth);
            for (int index = 0; index < count; index++) {
                int uid = levelOffset(depth) + index;
                assertEquals("depth of block " + uid, depth, records.get(uid * 3));
                int expectedParent = depth == 0 ? -1 : levelOffset(depth - 1) + index / 4;
                assertEquals("parent of block " + uid, expectedParent, records.get(uid * 3 + 1));
                assertEquals("launching thread of block " + uid, depth == 0 ? 0 : (index % 4) / 2, records.get(uid * 3 + 2));
            }
        }
    }

    // =================================================================== cdpSimpleQuicksort

    private static final int QS_MAX_DEPTH = 16;
    private static final int QS_INSERTION_SORT = 32;

    private static final DeviceKernel SIMPLE_QUICKSORT = DeviceKernel.of(TestCudaSamplesDynamicParallelism::cdpSimpleQuicksort);

    /** selection_sort: used when the recursion is too deep or the range is small. */
    private static void selectionSort(IntArray data, int left, int right) {
        for (int i = left; i <= right; ++i) {
            int minVal = data.get(i);
            int minIdx = i;
            for (int j = i + 1; j <= right; ++j) {
                int valJ = data.get(j);
                if (valJ < minVal) {
                    minIdx = j;
                    minVal = valJ;
                }
            }
            if (i != minIdx) {
                data.set(minIdx, data.get(i));
                data.set(i, minVal);
            }
        }
    }

    /** cdp_simple_quicksort: one thread partitions [left, right] and launches one grid per side. */
    private static void cdpSimpleQuicksort(KernelContext context, IntArray data, int left, int right, int depth) {
        // '|' rather than '||': see beehive-lab/TornadoVM#1137.
        if (depth >= QS_MAX_DEPTH | right - left <= QS_INSERTION_SORT) {
            selectionSort(data, left, right);
            return;
        }
        int lptr = left;
        int rptr = right;
        int pivot = data.get((left + right) / 2);
        while (lptr <= rptr) {
            int lval = data.get(lptr);
            int rval = data.get(rptr);
            while (lval < pivot) {
                lptr++;
                lval = data.get(lptr);
            }
            while (rval > pivot) {
                rptr--;
                rval = data.get(rptr);
            }
            if (lptr <= rptr) {
                data.set(lptr, rval);
                data.set(rptr, lval);
                lptr++;
                rptr--;
            }
        }
        if (left < rptr) {
            context.launch(SIMPLE_QUICKSORT, DeviceLaunchMode.FIRE_AND_FORGET, 1, 1, data, left, rptr, depth + 1);
        }
        if (lptr < right) {
            context.launch(SIMPLE_QUICKSORT, DeviceLaunchMode.FIRE_AND_FORGET, 1, 1, data, lptr, right, depth + 1);
        }
    }

    @Test
    public void testCdpSimpleQuicksort() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int numItems = 4096;
        Random random = new Random(2047);
        IntArray data = new IntArray(numItems);
        int[] expected = new int[numItems];
        for (int i = 0; i < numItems; i++) {
            int value = random.nextInt(numItems);
            data.set(i, value);
            expected[i] = value;
        }
        Arrays.sort(expected);

        TaskGraph taskGraph = new TaskGraph("qsort") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("cdp", TestCudaSamplesDynamicParallelism::cdpSimpleQuicksort, new KernelContext(), data, 0, numItems - 1, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("qsort.cdp", 1, 1)).execute();
        }
        for (int i = 0; i < numItems; i++) {
            assertEquals("item " + i, expected[i], data.get(i));
        }
    }

    // =================================================================== cdpBezierTessellation

    private static final int MAX_TESSELLATION = 32;

    private static final DeviceKernel BEZIER_POSITIONS = DeviceKernel.of(TestCudaSamplesDynamicParallelism::computeBezierLinePositions);

    /** computeBezierLinePositions: one thread per tessellation point of line {@code lidx}. */
    private static void computeBezierLinePositions(KernelContext context, int lidx, FloatArray controlPoints, FloatArray vertexPos, int nTessPoints) {
        int idx = context.globalIdx;
        if (idx < nTessPoints) {
            float u = (float) idx / (float) (nTessPoints - 1);
            float omu = 1.0f - u;
            float b0 = omu * omu;
            float b1 = 2.0f * u * omu;
            float b2 = u * u;
            int cp = lidx * 6;
            float x = b0 * controlPoints.get(cp) + b1 * controlPoints.get(cp + 2) + b2 * controlPoints.get(cp + 4);
            float y = b0 * controlPoints.get(cp + 1) + b1 * controlPoints.get(cp + 3) + b2 * controlPoints.get(cp + 5);
            int out = (lidx * MAX_TESSELLATION + idx) * 2;
            vertexPos.set(out, x);
            vertexPos.set(out + 1, y);
        }
    }

    /** computeBezierLinesCDP: one thread per line sizes the tessellation from the curvature and launches it. */
    private static void computeBezierLinesCDP(KernelContext context, FloatArray controlPoints, IntArray nVertices, FloatArray vertexPos, int nLines) {
        int lidx = context.globalIdx;
        if (lidx < nLines) {
            int cp = lidx * 6;
            float midX = controlPoints.get(cp + 2) - 0.5f * (controlPoints.get(cp) + controlPoints.get(cp + 4));
            float midY = controlPoints.get(cp + 3) - 0.5f * (controlPoints.get(cp + 1) + controlPoints.get(cp + 5));
            float spanX = controlPoints.get(cp + 4) - controlPoints.get(cp);
            float spanY = controlPoints.get(cp + 5) - controlPoints.get(cp + 1);
            float curvature = TornadoMath.sqrt(midX * midX + midY * midY) / TornadoMath.sqrt(spanX * spanX + spanY * spanY);
            int nTessPoints = Math.min(Math.max((int) (curvature * 16.0f), 4), MAX_TESSELLATION);
            nVertices.set(lidx, nTessPoints);
            context.launch(BEZIER_POSITIONS, nTessPoints, 32, lidx, controlPoints, vertexPos, nTessPoints);
        }
    }

    @Test
    public void testCdpBezierTessellation() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int nLines = 256;
        final int blockDim = 64;
        Random random = new Random(1);
        FloatArray controlPoints = new FloatArray(nLines * 6);
        IntArray nVertices = new IntArray(nLines);
        FloatArray vertexPos = new FloatArray(nLines * MAX_TESSELLATION * 2);
        vertexPos.init(Float.NaN);
        for (int l = 0; l < nLines; l++) {
            // As in the sample: a line along x with a random middle control point.
            controlPoints.set(l * 6, 0.0f);
            controlPoints.set(l * 6 + 1, 0.0f);
            controlPoints.set(l * 6 + 2, random.nextFloat());
            controlPoints.set(l * 6 + 3, random.nextFloat() * 2 - 1);
            controlPoints.set(l * 6 + 4, 1.0f);
            controlPoints.set(l * 6 + 5, random.nextFloat() * 0.2f);
        }

        TaskGraph taskGraph = new TaskGraph("bezier") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, controlPoints) //
                .task("cdp", TestCudaSamplesDynamicParallelism::computeBezierLinesCDP, new KernelContext(), controlPoints, nVertices, vertexPos, nLines) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, nVertices, vertexPos);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("bezier.cdp", nLines, blockDim)).execute();
        }

        for (int l = 0; l < nLines; l++) {
            float[] cp = new float[6];
            for (int k = 0; k < 6; k++) {
                cp[k] = controlPoints.get(l * 6 + k);
            }
            float midX = cp[2] - 0.5f * (cp[0] + cp[4]);
            float midY = cp[3] - 0.5f * (cp[1] + cp[5]);
            float spanX = cp[4] - cp[0];
            float spanY = cp[5] - cp[1];
            float curvature = (float) (Math.sqrt(midX * midX + midY * midY) / Math.sqrt(spanX * spanX + spanY * spanY));
            int nTess = Math.min(Math.max((int) (curvature * 16.0f), 4), MAX_TESSELLATION);
            assertEquals("tessellation of line " + l, nTess, nVertices.get(l));
            for (int i = 0; i < nTess; i++) {
                float u = (float) i / (float) (nTess - 1);
                float omu = 1.0f - u;
                float x = omu * omu * cp[0] + 2 * u * omu * cp[2] + u * u * cp[4];
                float y = omu * omu * cp[1] + 2 * u * omu * cp[3] + u * u * cp[5];
                int out = (l * MAX_TESSELLATION + i) * 2;
                assertEquals("x of vertex " + i + " of line " + l, x, vertexPos.get(out), 1e-5f);
                assertEquals("y of vertex " + i + " of line " + l, y, vertexPos.get(out + 1), 1e-5f);
            }
        }
    }

    // =================================================================== cdpQuadtree

    private static final int QT_THREADS = 128;
    private static final int QT_WARPS = QT_THREADS / 32;

    private static final DeviceKernel QUADTREE = DeviceKernel.of(TestCudaSamplesDynamicParallelism::buildQuadtreeKernel);

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

    @Test
    public void testCdpQuadtree() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int numPoints = 1024;
        final int maxDepth = 8;
        final int minPointsPerNode = 16;
        final int maxNodes = nodeLevelBase(maxDepth + 1);

        Random random = new Random(42);
        FloatArray points = new FloatArray(2 * 2 * numPoints);
        float[] xs = new float[numPoints];
        for (int i = 0; i < numPoints; i++) {
            float x = random.nextFloat();
            float y = random.nextFloat();
            points.set(i, x);
            points.set(numPoints + i, y);
            xs[i] = x;
        }
        FloatArray bbox = new FloatArray(maxNodes * 4);
        IntArray begin = new IntArray(maxNodes);
        IntArray end = new IntArray(maxNodes);
        bbox.set(0, 0.0f);
        bbox.set(1, 0.0f);
        bbox.set(2, 1.0f);
        bbox.set(3, 1.0f);
        begin.set(0, 0);
        end.set(0, numPoints);

        TaskGraph taskGraph = new TaskGraph("quadtree") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, bbox, begin, end, points) //
                .task("cdp", TestCudaSamplesDynamicParallelism::buildQuadtreeKernel, new KernelContext(), bbox, begin, end, points, numPoints, 0, 0, 0, maxDepth, minPointsPerNode) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bbox, begin, end, points);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("quadtree.cdp", QT_THREADS, QT_THREADS)).execute();
        }

        assertTrue("quadtree is not well formed", checkQuadtree(bbox, begin, end, points, numPoints, 0, 0, maxDepth, minPointsPerNode));
        // The points in buffer 0 are a permutation of the input.
        float[] sorted = new float[numPoints];
        for (int i = 0; i < numPoints; i++) {
            sorted[i] = points.get(i);
        }
        Arrays.sort(sorted);
        Arrays.sort(xs);
        for (int i = 0; i < numPoints; i++) {
            assertEquals("x-coordinate " + i, xs[i], sorted[i], 0.0f);
        }
    }
}
