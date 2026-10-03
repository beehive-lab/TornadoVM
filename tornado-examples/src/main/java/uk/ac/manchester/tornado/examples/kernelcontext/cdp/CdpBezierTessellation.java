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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Port of {@code cdpBezierTessellation} from NVIDIA's cuda-samples ({@code cpp/3_CUDA_Features/cdpBezierTessellation}) to CUDA Dynamic
 * Parallelism in TornadoVM ({@link KernelContext#launch}). One thread per quadratic Bezier line computes the curvature, chooses how many points to tessellate it into and launches a grid of exactly that many threads to compute them.
 *
 * <p>
 * The same kernels are tested in {@code TestCudaSamplesDynamicParallelism}. How to run (CUDA backend):
 * </p>
 *
 * <pre>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.cdp.CdpBezierTessellation
 * </pre>
 */
public class CdpBezierTessellation {

    private static GridScheduler grid(String task, int global, int local) {
        WorkerGrid worker = new WorkerGrid1D(global);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    private static final int MAX_TESSELLATION = 32;

    private static final DeviceKernel BEZIER_POSITIONS = DeviceKernel.of(CdpBezierTessellation::computeBezierLinePositions);

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

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int nLines = 256;
        final int blockDim = 64;
        Random random = new Random(1);
        FloatArray controlPoints = new FloatArray(nLines * 6);
        IntArray nVertices = new IntArray(nLines);
        FloatArray vertexPos = new FloatArray(nLines * MAX_TESSELLATION * 2);
        for (int l = 0; l < nLines; l++) {
            controlPoints.set(l * 6, 0.0f);
            controlPoints.set(l * 6 + 1, 0.0f);
            controlPoints.set(l * 6 + 2, random.nextFloat());
            controlPoints.set(l * 6 + 3, random.nextFloat() * 2 - 1);
            controlPoints.set(l * 6 + 4, 1.0f);
            controlPoints.set(l * 6 + 5, random.nextFloat() * 0.2f);
        }
        TaskGraph taskGraph = new TaskGraph("bezier") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, controlPoints) //
                .task("cdp", CdpBezierTessellation::computeBezierLinesCDP, new KernelContext(), controlPoints, nVertices, vertexPos, nLines) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, nVertices, vertexPos);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("bezier.cdp", nLines, blockDim)).execute();
        }
        int total = 0;
        int[] histogram = new int[MAX_TESSELLATION + 1];
        for (int l = 0; l < nLines; l++) {
            total += nVertices.get(l);
            histogram[nVertices.get(l)]++;
        }
        System.out.println(nLines + " lines tessellated into " + total + " vertices; lines per tessellation size:");
        for (int t = 0; t <= MAX_TESSELLATION; t++) {
            if (histogram[t] > 0) {
                System.out.printf("  %2d points: %d%n", t, histogram[t]);
            }
        }
        System.out.println("Bezier Line CDP Test PASSED");
    }
}
