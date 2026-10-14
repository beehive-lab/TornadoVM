/*
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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import java.util.Locale;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.loop.DefaultLoopPolicies;
import tornado.graal.compiler.nodes.loop.LoopEx;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileMmaNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileNode;

/**
 * Loop policies of the CUDA backend: decides whether a loop that contains a CUDA Tile operation
 * (the k-loop of a tile GEMM) is fully unrolled into straight-line load/load/mma steps or kept
 * rolled.
 *
 * <p>
 * Task specialisation gives the k-loop a constant trip count, so Graal could unroll it. Which form
 * is faster depends on the architecture and the tile shape (fp16 GEMM, n = 1024 to 4096):
 * </p>
 * <ul>
 * <li>sm_120: rolled is 2.4-15x faster for every tile shape, and the straight-line form can take
 * longer to compile than the tile compiler timeout.</li>
 * <li>sm_8x (A10, RTX 4090): straight-line is 1.5-2.4x faster for 128x128x64 tiles, where the
 * rolled loop needs 64 KB of shared memory per block and runs at one block per SM. For smaller
 * tiles the straight-line form spills and rolled is 1.7-6.5x faster.</li>
 * </ul>
 *
 * <p>
 * By default ({@code auto}) a tile k-loop is unrolled only for 128x128x64 tiles on compute
 * capability 8.x and kept rolled everywhere else. {@code -Dtornado.cuda.tile.unrollKLoop=true}
 * unrolls every tile k-loop that fits the unroll budget, {@code =false} keeps them all rolled.
 * </p>
 */
public class CUDATileLoopPolicies extends DefaultLoopPolicies {

    public static final String UNROLL_K_LOOP_PROPERTY = "tornado.cuda.tile.unrollKLoop";

    private static final int UNROLLED_TILE_MN = 128;

    private static final int UNROLLED_TILE_K = 64;

    private static final int UNROLLED_COMPUTE_CAPABILITY_MAJOR = 8;

    private final TornadoDeviceContext deviceContext;

    public CUDATileLoopPolicies(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    public static boolean isTileOperation(Node node) {
        return node instanceof CUDATileNode;
    }

    public static boolean containsTileOperation(LoopEx loop) {
        for (Node node : loop.inside().nodes()) {
            if (isTileOperation(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param loop a counted loop
     * @return true when the loop contains a tile operation and must not be fully unrolled
     */
    public boolean keepRolled(LoopEx loop) {
        if (!containsTileOperation(loop)) {
            return false;
        }
        String mode = System.getProperty(UNROLL_K_LOOP_PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "true" -> false;
            case "false" -> true;
            default -> !straightLineIsFaster(loop);
        };
    }

    private boolean straightLineIsFaster(LoopEx loop) {
        if (computeCapabilityMajor() != UNROLLED_COMPUTE_CAPABILITY_MAJOR) {
            return false;
        }
        for (Node node : loop.inside().nodes()) {
            if (node instanceof CUDATileMmaNode mma && isUnrolledTileShape(mma)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnrolledTileShape(CUDATileMmaNode mma) {
        int[] accumulator = mma.getAccumulatorShape();
        if (accumulator == null || accumulator.length != 2 || accumulator[0] != UNROLLED_TILE_MN || accumulator[1] != UNROLLED_TILE_MN) {
            return false;
        }
        if (!(mma.getTileA() instanceof CUDATileNode operandA)) {
            return false;
        }
        int[] shapeA = operandA.tileShape();
        return shapeA != null && shapeA.length == 2 && shapeA[1] == UNROLLED_TILE_K;
    }

    private int computeCapabilityMajor() {
        if (deviceContext != null && deviceContext.getDevice() instanceof CUDADevice cudaDevice) {
            return cudaDevice.getComputeCapabilityMajor();
        }
        return -1;
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        return !keepRolled(loop) && super.shouldFullUnroll(loop);
    }
}
