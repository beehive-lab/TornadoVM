/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal;

import jdk.graal.compiler.lir.phases.LIRPhaseSuite;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoMidTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoAllocationStage;

public class TornadoSuites {

    private final TornadoSketchTier sketchTier;
    private final TornadoHighTier highTier;
    private final TornadoMidTier midTier;
    private final TornadoLowTier lowTier;

    private final TornadoAllocationStage allocStage;
    private final LIRPhaseSuite<PreAllocationOptimizationContext> preAllocStage;
    private final LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage;

    public TornadoSuites(OptionValues options, TornadoDeviceContext deviceContext, TornadoCompilerConfiguration config, MetaAccessProvider metaAccessProvider,
            CanonicalizerPhase.CustomSimplification canonicalizer, AddressLoweringByNodePhase.AddressLowering addressLowering) {
        sketchTier = config.createSketchTier(options, canonicalizer);
        highTier = config.createHighTier(options, deviceContext, canonicalizer, metaAccessProvider);
        midTier = config.createMidTier(options);
        lowTier = config.createLowTier(options, deviceContext, addressLowering);
        allocStage = config.createAllocationStage(options);
        preAllocStage = config.createPreAllocationOptimizationStage(options);
        postAllocStage = config.createPostAllocationOptimizationStage(options);
    }

    public TornadoSketchTier getSketchTier() {
        return sketchTier;
    }

    public TornadoHighTier getHighTier() {
        return highTier;
    }

    public TornadoMidTier getMidTier() {
        return midTier;
    }

    public TornadoLowTier getLowTier() {
        return lowTier;
    }

    public LIRPhaseSuite<PreAllocationOptimizationContext> getPreAllocationOptimizationStage() {
        return preAllocStage;
    }

    public TornadoAllocationStage getAllocationStage() {
        return allocStage;
    }

    public LIRPhaseSuite<PostAllocationOptimizationContext> getPostAllocationOptimizationStage() {
        return postAllocStage;
    }

    public TornadoLIRSuites getLIRSuites() {
        return new TornadoLIRSuites(preAllocStage, allocStage, postAllocStage);
    }

}
