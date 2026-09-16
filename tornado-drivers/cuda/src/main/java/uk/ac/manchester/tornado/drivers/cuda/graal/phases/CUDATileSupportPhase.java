/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.cuda.graal.phases;

import java.util.Optional;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.Invoke;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.phases.Phase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDATileCompiler;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileMmaNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileViewNode;

/**
 * Checks that the device, driver and toolkit can actually run a CUDA Tile kernel, and that the
 * tile operations in the graph are ones CUDA Tile accepts.
 *
 * <p>
 * This runs before code generation so that an unsupported combination fails with a message
 * naming the missing requirement, rather than as an opaque compiler or loader error much later.
 * Three requirements are independent and each is reported separately:
 * </p>
 *
 * <ul>
 * <li>compute capability 8.0 or newer, which is where CUDA Tile code generation starts;</li>
 * <li>toolkit 13.3 or newer, the first release with the CUDA Tile C++ surface. The version
 * checked is the nvcc that would compile the kernel, not NVRTC: the tile path does not use
 * NVRTC, so a system CUDA older than 13.3 with a userspace 13.3 nvcc is fine;</li>
 * <li>a driver new enough to load the result. That one cannot be checked here and is reported
 * by the loader, but the message below names it because it is the requirement people miss.</li>
 * </ul>
 */
public class CUDATileSupportPhase extends Phase {

    /** CUDA Tile code generation is disabled below sm_80. */
    private static final int TILE_MAJOR_MIN = 8;
    private static final int TILE_MINOR_MIN = 0;

    /**
     * CUDA Tile C++ ships in toolkit 13.3. Encoded as major * 1000 + minor, the same encoding
     * the rest of the backend uses for toolkit versions.
     */
    private static final int TILE_TOOLKIT_MIN = 13003;

    /**
     * Compute capability where {@code tileiras} starts accepting fp8 tiles. Below this it
     * rejects them with "Incompatibility with architecture 'sm_89': unsupported type
     * 'f8E4M3FN'" from inside nvcc, which is a confusing place to learn it; measured against
     * 13.3.73 by compiling the same kernel for sm_89 and sm_90.
     */
    private static final int FP8_MAJOR_MIN = 9;

    private final TornadoDeviceContext deviceContext;

    public CUDATileSupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        CUDATileNode firstTileNode = null;
        CUDATileNode fp8Node = null;
        for (Node node : graph.getNodes()) {
            if (node instanceof CUDATileViewNode) {
                // A view is a parse time descriptor that the partition plugin consumes. One
                // surviving to this point means view() was called without a matching
                // partition(), and it has no device representation.
                throw new TornadoDeviceTileNotSupported(
                        "A tile view reached code generation unconsumed. Every TileContext.view(...) must be "
                                + "passed to TileContext.partition(...) in the same kernel; a view is not a value.");
            }
            if (node instanceof CUDATileNode tileNode) {
                if (firstTileNode == null) {
                    firstTileNode = tileNode;
                }
                if (node instanceof CUDATileMmaNode mma) {
                    verifyMmaTypes(mma);
                }
                if (fp8Node == null && isFp8(tileNode.tileDType())) {
                    fp8Node = tileNode;
                }
            }
        }

        if (firstTileNode == null) {
            verifyNoUnintrinsifiedTileCalls(graph);
            return;
        }
        verifyNoUnintrinsifiedTileCalls(graph);

        if (!(deviceContext.getDevice() instanceof CUDADevice cudaDevice)) {
            throw new TornadoDeviceTileNotSupported("CUDA Tile kernels require an NVIDIA CUDA device, got: "
                    + deviceContext.getDevice().getClass().getName());
        }

        int major = cudaDevice.getComputeCapabilityMajor();
        int minor = cudaDevice.getComputeCapabilityMinor();
        if (major < TILE_MAJOR_MIN || (major == TILE_MAJOR_MIN && minor < TILE_MINOR_MIN)) {
            throw new TornadoDeviceTileNotSupported("CUDA Tile requires compute capability " + TILE_MAJOR_MIN + "."
                    + TILE_MINOR_MIN + " or higher (Ampere and newer); device reports " + major + "." + minor
                    + ". Rewrite this task with KernelContext to use the SIMT path instead.");
        }

        if (fp8Node != null && major < FP8_MAJOR_MIN) {
            throw new TornadoDeviceTileNotSupported("The tile operation '" + fp8Node.tileOperationName() + "' uses "
                    + fp8Node.tileDType() + ", and CUDA Tile supports fp8 tiles only from compute capability "
                    + FP8_MAJOR_MIN + ".0 (Hopper) onwards; device reports " + major + "." + minor
                    + ". Use F16 or BF16 operands on this device.");
        }

        // The tile toolchain is nvcc, not NVRTC, so this asks nvcc. A system CUDA older than
        // 13.3 alongside a userspace 13.3 nvcc is a perfectly workable setup, and gating on the
        // NVRTC version would reject it.
        int toolkit = CUDATileCompiler.toolkitVersion();
        if (toolkit == 0) {
            throw new TornadoDeviceTileNotSupported("No nvcc capable of compiling CUDA Tile C++ was found. Install "
                    + "CUDA Toolkit 13.3 or newer, or point -Dtornado.cuda.nvcc at one; 'pip install --user "
                    + "cuda-tile[tileiras]' installs a usable one without root.");
        }
        if (toolkit < TILE_TOOLKIT_MIN) {
            throw new TornadoDeviceTileNotSupported("CUDA Tile C++ requires CUDA Toolkit 13.3 or newer, but the nvcc "
                    + "selected for the tile path reports " + (toolkit / 1000) + "." + (toolkit % 1000)
                    + ". Loading a tile kernel additionally needs driver R580 or newer.");
        }
    }

    private static boolean isFp8(DType dtype) {
        return dtype == DType.FP8_E4M3 || dtype == DType.FP8_E5M2;
    }

    /**
     * A tile API call that survived as a real invoke was never intrinsified, and there is no
     * such function on the device. That happens when a kernel is resolved through reflection,
     * because plugin lookup is skipped on that path, which is why the MMA intrinsics are
     * registered both as invocation plugins and as cases in TornadoCUDAIntrinsicsReplacements.
     *
     * <p>
     * Until the tile operations are handled in that phase too, detect the situation and say so
     * rather than emitting a call to a function that does not exist. Failing here is the whole
     * point: the alternative is a kernel that compiles in one launch mode and breaks in another.
     * </p>
     */
    private static void verifyNoUnintrinsifiedTileCalls(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.callTarget() == null) {
                continue;
            }
            String target = invoke.callTarget().targetName();
            if (target != null && (target.contains("TileContext.") || target.contains("PartitionView."))) {
                throw new TornadoDeviceTileNotSupported("The tile operation '" + target + "' was not intrinsified. "
                        + "This happens when the kernel is resolved reflectively, where invocation plugins are not "
                        + "consulted. Handle it in TornadoCUDAIntrinsicsReplacements as the MMA intrinsics are, or "
                        + "run this task through the non-reflective path.");
            }
        }
    }

    /**
     * The accumulator type of a tile mma must be one CUDA Tile allows for the operand type, and
     * must equal the result type. Rejecting the pair here produces an actionable message instead
     * of a template error from the tile compiler.
     */
    private static void verifyMmaTypes(CUDATileMmaNode mma) {
        DType operand = mma.getOperandType();
        DType accumulator = mma.getAccumulatorType();
        if (!operand.canAccumulateInto(accumulator)) {
            throw new TornadoDeviceTileNotSupported("CUDA Tile does not accumulate " + operand + " operands into a "
                    + accumulator + " tile. See the mmaf and mmai type tables for the legal pairs.");
        }
    }
}
