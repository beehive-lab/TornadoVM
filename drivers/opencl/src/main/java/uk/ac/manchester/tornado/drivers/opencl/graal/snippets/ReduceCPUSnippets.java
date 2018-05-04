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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

/**
 * Graal Snippets for CPU OpenCL reductions.
 * 
 */
public class ReduceCPUSnippets implements Snippets {

    @Snippet
    public static void partialReduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        // int position = start / numThreads;
        outputArray[globalID] += inputArray[gidx];
    }

    public static class Templates extends AbstractTemplates {

        // Add
        private final SnippetInfo partialReduceIntSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceIntAddGlobal");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        private SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = partialReduceIntSnippetGlobal;
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetInfo(JavaKind elementKind, ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (elementKind == JavaKind.Int) {
                snippet = inferIntSnippet(value, extra);
            } else {
                throw new RuntimeException("Data type not supported");
            }
            return snippet;
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, ValueNode threadId, GlobalThreadSizeNode globalSize, ValueNode startNode,
                ValueNode globalID, LoweringTool tool) {

            StructuredGraph graph = storeAtomicIndexed.graph();

            // In this point, depending on the type and the operation,
            // we call the corresponding snippet.
            JavaKind elementKind = storeAtomicIndexed.elementKind();

            ValueNode value = storeAtomicIndexed.value();
            ValueNode array = storeAtomicIndexed.array();
            ValueNode accumulator = storeAtomicIndexed.getAccumulator();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();

            SnippetInfo snippet = getSnippetInfo(elementKind, value, extra);

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", threadId);
            args.add("start", startNode);
            args.add("numThreads", 8);
            args.add("globalID", globalID);
            if (extra != null) {
                args.add("value", extra);
            }

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
