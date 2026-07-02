/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package src.main.java.uk.ac.manchester.tornado.drivers.ptx.graal.phases;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceMMANotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.CUDAComputeCapability;
import uk.ac.manchester.tornado.drivers.ptx.PTXDevice;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMAComputeNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMAFragmentNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadAInt8Node;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadANode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadBInt8Node;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadBNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadBSwizzledNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMAStoreBSwizzledNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMAStoreNode;

import java.util.Optional;

/**
 * This compiler phase examines if the execution device supports MMA instructions
 * and throws a {@link TornadoDeviceMMANotSupported} exception if it does not.
 */
public class PTXTensorCoreSupportPhase extends Phase {

    private static final CUDAComputeCapability MMA_MIN = new CUDAComputeCapability(8, 0);

    private final TornadoDeviceContext deviceContext;

    public PTXTensorCoreSupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        CUDAComputeCapability deviceCC = null;

        boolean hasMMA = false;
        for (Node n : graph.getNodes()) {
            if (n instanceof MMALoadANode
                    || n instanceof MMALoadBNode
                    || n instanceof MMALoadBSwizzledNode
                    || n instanceof MMAStoreNode
                    || n instanceof MMAStoreBSwizzledNode
                    || n instanceof MMALoadAInt8Node
                    || n instanceof MMALoadBInt8Node
                    || n instanceof MMAComputeNode
                    || n instanceof MMAFragmentNode) {
                hasMMA = true;
                break;
            }
        }

        if (hasMMA) {
            if (deviceContext.getDevice() instanceof PTXDevice ptxDevice) {
                    deviceCC = ptxDevice.getComputeCapability();
            } else {
                throw new TornadoRuntimeException("Tensor Core instructions require a PTX device, got: " + deviceContext.getDevice().getClass().getName());
            }


            if (deviceCC.compareTo(MMA_MIN) < 0) {
                throw new TornadoDeviceMMANotSupported("The current PTX device (" + deviceContext.getDeviceName()
                        + ", compute capability " + deviceCC + ") does not support Tensor Core instructions"
                        + " (requires compute capability >= " + MMA_MIN + ")");
            }
        }
    }
}
