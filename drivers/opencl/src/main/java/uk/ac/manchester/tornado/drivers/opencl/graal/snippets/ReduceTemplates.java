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
package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode.ATOMIC_OPERATION;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

public class ReduceTemplates extends AbstractTemplates {

    private final SnippetInfo helloSnippet = snippet(ReduceSnippets.class, "hello");

    public ReduceTemplates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        super(options, providers, snippetReflection, target);
    }

    public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, LoweringTool tool) {

        StructuredGraph graph = storeAtomicIndexed.graph();

        JavaKind elementKind = storeAtomicIndexed.elementKind();

        ValueNode value = storeAtomicIndexed.value();
        ValueNode array = storeAtomicIndexed.array();
        ValueNode accumulator = storeAtomicIndexed.getAccumulator();

        ATOMIC_OPERATION operation = ATOMIC_OPERATION.CUSTOM;
        if (value instanceof OCLReduceAddNode) {
            operation = ATOMIC_OPERATION.ADD;
        } else if (value instanceof OCLReduceSubNode) {
            operation = ATOMIC_OPERATION.SUB;
        } else if (value instanceof OCLReduceMulNode) {
            operation = ATOMIC_OPERATION.MUL;
        }

        int[] data = new int[100];

        SnippetInfo snippet = helloSnippet;
        Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
        args.add("n", 100);
        args.add("data", address);

        template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, DEFAULT_REPLACER, args);

    }

}
