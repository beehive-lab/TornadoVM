/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import org.graalvm.compiler.lir.phases.LIRPhaseSuite;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationStage;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationStage;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomCanonicalizer;

import uk.ac.manchester.tornado.graal.compiler.TornadoCompilerConfiguration;
import uk.ac.manchester.tornado.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.graal.compiler.TornadoMidTier;
import uk.ac.manchester.tornado.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.graal.phases.lir.TornadoAllocationStage;

public class OCLCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage(OptionValues options) {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(OptionValues options, CustomCanonicalizer canonicalizer) {
        return new TornadoSketchTier(options, canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(OptionValues options, CustomCanonicalizer canonicalizer) {
        return new OCLHighTier(options, canonicalizer);
    }

    @Override
    public TornadoLowTier createLowTier(OptionValues options, AddressLowering addressLowering) {
        return new OCLLowTier(options, addressLowering);
    }

    @Override
    public TornadoMidTier createMidTier(OptionValues options) {
        return new OCLMidTier(options);
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage(OptionValues options) {
        return new PostAllocationOptimizationStage(options);
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage(OptionValues options) {
        return new PreAllocationOptimizationStage(options);
    }

}
