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
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP16NotSupported;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;

/**
 * This compiler phase examines if the execution device supports half precision types
 * and throws a {@link TornadoDeviceFP16NotSupported} exception if it does not.
 */
public class MetalFP16SupportPhase extends Phase {

    private TornadoDeviceContext deviceContext;

    public MetalFP16SupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {
        boolean fp16Support = deviceContext.getDevice() instanceof MetalTargetDevice metalDevice && metalDevice.isDeviceFP16Supported();

        for (ReadNode readNode : graph.getNodes().filter(ReadNode.class)) {
            if (readNode.getLocationIdentity().toString().contains("VectorHalf") && !fp16Support) {
                throw new TornadoDeviceFP16NotSupported("The current Metal device (" + deviceContext.getDeviceName() + ") does not support FP16");
            }
        }
    }
}
