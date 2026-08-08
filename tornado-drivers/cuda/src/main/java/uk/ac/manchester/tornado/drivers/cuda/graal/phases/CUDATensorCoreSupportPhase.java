/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceMMANotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDAProgram;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDACpAsyncCommitGroupNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDACpAsyncCopyNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDACpAsyncWaitGroupNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAComputeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAFragmentNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadAInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadANode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreNode;


import java.util.Optional;

/**
 * This compiler phase examines if the execution device supports MMA instructions
 * and throws a {@link TornadoDeviceMMANotSupported} exception if it does not.
 */
public class CUDATensorCoreSupportPhase extends Phase {

    private static final int MMA_MAJOR_MIN = 8;
    private static final int MMA_MINOR_MIN = 0;
    // FP8 (e4m3/e5m2) mma.sync operands need the Ada/Hopper tensor cores.
    private static final int FP8_MMA_MAJOR_MIN = 8;
    private static final int FP8_MMA_MINOR_MIN = 9;
    // FP8 mma.sync is encodable only from PTX ISA 8.4, which ships with CUDA 12.4.
    // NVRTC stamps its own .version onto the PTX it emits, so an older toolkit cannot
    // express the instruction at all - independent of the device's compute capability.
    // Encoded as major * 1000 + minor to match CUDAProgram.getNvrtcVersion().
    private static final int FP8_MMA_NVRTC_MIN = 12004;

    private final TornadoDeviceContext deviceContext;

    public CUDATensorCoreSupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        boolean hasMMA = false;
        boolean hasFP8MMA = false;
        for (Node n : graph.getNodes()) {
            // cp.async shares the sm_80 floor with mma.sync, so its nodes are gated by
            // this same phase rather than a separate one.
            if (n instanceof CUDAMMALoadANode || n instanceof CUDAMMALoadBNode
                    || n instanceof CUDAMMALoadBSwizzledNode
                    || n instanceof CUDAMMAStoreNode
                    || n instanceof CUDAMMAStoreBSwizzledNode
                    || n instanceof CUDAMMALoadAInt8Node
                    || n instanceof CUDAMMALoadBInt8Node
                    || n instanceof CUDAMMAFragmentNode
                    || n instanceof CUDACpAsyncCopyNode
                    || n instanceof CUDACpAsyncCommitGroupNode
                    || n instanceof CUDACpAsyncWaitGroupNode) {
                hasMMA = true;
            } else if (n instanceof CUDAMMAComputeNode compute) {
                hasMMA = true;
                CUDALIRStmt.MMAComputeStmt.MMAOperand operand = compute.getOperand();
                if (operand == CUDALIRStmt.MMAComputeStmt.MMAOperand.E4M3
                        || operand == CUDALIRStmt.MMAComputeStmt.MMAOperand.E5M2) {
                    hasFP8MMA = true;
                }
            }
        }
        if (!hasMMA) {
            return;
        }
        if (!(deviceContext.getDevice() instanceof CUDADevice cudaDevice)) {
            throw new TornadoDeviceMMANotSupported(
                    "MMA/cp.async instructions require an NVIDIA CUDA device, got: "
                            + deviceContext.getDevice().getClass().getName());
        }
        int major = cudaDevice.getComputeCapabilityMajor();
        int minor = cudaDevice.getComputeCapabilityMinor();
        if (major < MMA_MAJOR_MIN || (major == MMA_MAJOR_MIN && minor < MMA_MINOR_MIN)) {
            throw new TornadoDeviceMMANotSupported(
                    "MMA/cp.async instructions require compute capability "
                            + MMA_MAJOR_MIN + "." + MMA_MINOR_MIN
                            + " or higher (Ampere+); device reports "
                            + major + "." + minor);
        }
        if (hasFP8MMA && (major < FP8_MMA_MAJOR_MIN
                || (major == FP8_MMA_MAJOR_MIN && minor < FP8_MMA_MINOR_MIN))) {
            throw new TornadoDeviceMMANotSupported(
                    "FP8 (e4m3/e5m2) MMA instructions require compute capability "
                            + FP8_MMA_MAJOR_MIN + "." + FP8_MMA_MINOR_MIN
                            + " or higher (Ada/Hopper+); device reports "
                            + major + "." + minor);
        }
        // Capability is necessary but not sufficient: the instruction also has to be
        // encodable by the toolkit that emits the PTX. A device newer than the toolkit
        // (e.g. a Blackwell sm_120 with CUDA 12.3) clears the check above and then fails
        // at module load with "requires PTX ISA .version 8.4 or later" - report it as
        // unsupported here instead, so it degrades like every other capability gap.
        // A failed version query (-1) is not treated as a veto: leave those toolkits on
        // the pre-existing behaviour rather than refusing work they may well support.
        if (hasFP8MMA) {
            int nvrtcVersion = CUDAProgram.getNvrtcVersion();
            if (nvrtcVersion >= 0 && nvrtcVersion < FP8_MMA_NVRTC_MIN) {
                throw new TornadoDeviceMMANotSupported(
                        "FP8 (e4m3/e5m2) MMA instructions need PTX ISA 8.4, which requires a CUDA "
                                + "toolkit of version " + (FP8_MMA_NVRTC_MIN / 1000) + "." + (FP8_MMA_NVRTC_MIN % 1000)
                                + " or higher; the NVRTC in use reports "
                                + (nvrtcVersion / 1000) + "." + (nvrtcVersion % 1000)
                                + ". Install a newer CUDA toolkit to run FP8 tensor-core kernels.");
            }
        }
    }
}
