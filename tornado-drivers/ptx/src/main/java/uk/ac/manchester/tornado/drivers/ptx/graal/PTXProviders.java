/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public class PTXProviders extends Providers {
    private final TornadoSuitesProvider suites;

    public PTXProviders(MetaAccessProvider metaAccess, //
            CodeCacheProvider codeCache, //
            ConstantReflectionProvider constantReflection, //
            ConstantFieldProvider constantFieldProvider, //
            ForeignCallsProvider foreignCalls, //
            LoweringProvider lowerer, //
            Replacements replacements, //
            StampProvider stampProvider, //
            PlatformConfigurationProvider platformConfigurationProvider, //
            MetaAccessExtensionProvider metaAccessExtensionProvider, //
            SnippetReflectionProvider snippetReflection, //
            WordTypes wordTypes, //
            LoopsDataProvider loopsDataProvider, //
            PTXSuitesProvider suitesProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                snippetReflection, wordTypes, loopsDataProvider);
        this.suites = suitesProvider;
    }

    public TornadoSuitesProvider getSuitesProvider() {
        return suites;
    }
}
