/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCanonicalizer;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoInternalGraphBuilder;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

/**
 * TornadoVM Provider for all TIER compilation bases (HighTier, MidTier, LowTier
 * & SketchTier).
 */
public class SPIRVSuitesProvider implements TornadoSuitesProvider {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final SPIRVCanonicalizer canonicalizer;
    private TornadoSuites suites;
    private TornadoLIRSuites lirSuites;

    public SPIRVSuitesProvider(OptionValues options, SPIRVDeviceContext deviceContext, GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccessProvider,
            SPIRVCompilerConfiguration compilerConfig, AddressLoweringByNodePhase.AddressLowering addressLowering) {
        this.graphBuilderSuite = createGraphBuilderSuite(plugins);
        this.canonicalizer = new SPIRVCanonicalizer();
        suites = new TornadoSuites(options, deviceContext, compilerConfig, metaAccessProvider, canonicalizer, addressLowering);
        lirSuites = new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(), suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());

    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(GraphBuilderConfiguration.Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
        config.withEagerResolving(true);
        suite.appendPhase(new TornadoInternalGraphBuilder(config));
        return suite;
    }

    public TornadoLIRSuites getLIRSuites() {
        return lirSuites;
    }

    public TornadoSuites getSuites() {
        return suites;
    }

    @Override
    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    @Override
    public TornadoSketchTier getSketchTier() {
        return suites.getSketchTier();
    }
}
