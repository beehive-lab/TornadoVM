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
        for (Node n : graph.getNodes()) {
            if (n instanceof CUDAMMALoadANode || n instanceof CUDAMMALoadBNode
                    || n instanceof CUDAMMALoadBSwizzledNode
                    || n instanceof CUDAMMAStoreNode
                    || n instanceof CUDAMMAStoreBSwizzledNode
                    || n instanceof CUDAMMALoadAInt8Node
                    || n instanceof CUDAMMALoadBInt8Node
                    || n instanceof CUDAMMAComputeNode
                    || n instanceof CUDAMMAFragmentNode) {
                hasMMA = true;
                break;
            }
        }
        if (!hasMMA) {
            return;
        }
        if (!(deviceContext.getDevice() instanceof CUDADevice cudaDevice)) {
            throw new TornadoDeviceMMANotSupported(
                    "MMA instructions require an NVIDIA CUDA device, got: "
                            + deviceContext.getDevice().getClass().getName());
        }
        int major = cudaDevice.getComputeCapabilityMajor();
        int minor = cudaDevice.getComputeCapabilityMinor();
        if (major < MMA_MAJOR_MIN || (major == MMA_MAJOR_MIN && minor < MMA_MINOR_MIN)) {
            throw new TornadoDeviceMMANotSupported(
                    "MMA instructions require compute capability "
                            + MMA_MAJOR_MIN + "." + MMA_MINOR_MIN
                            + " or higher (Ampere+); device reports "
                            + major + "." + minor);
        }
    }
}
