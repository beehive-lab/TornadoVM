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
package uk.ac.manchester.tornado.drivers.metal.graal;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class MetalProviders extends Providers {

    private final MetalSuitesProvider suites;

    public MetalProviders(MetaAccessProvider metaAccess, //
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
            MetalSuitesProvider suitesProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                snippetReflection, wordTypes, loopsDataProvider);
        this.suites = suitesProvider;
    }

    public MetalSuitesProvider getSuitesProvider() {
        return suites;
    }

}
