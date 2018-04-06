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

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode.ATOMIC_OPERATION;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

public class ReduceSnippets implements Snippets {

    @Fold
    static LocationIdentity getArrayLocation(JavaKind kind) {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    /**
     * 1D full snippet for OpenCL reductions.
     * 
     * @param inputArray
     * @param outputArray
     * @param localMemory
     * @param gidx
     * @param numGroups
     */
    @Snippet
    public static void reduceIntAdd(int[] inputArray, int[] outputArray, int gidx, int globalSize) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);

        int sizeLocalMemory = 16;

        // Allocate a chunk of data in local memory
        int[] localMemory = new int[sizeLocalMemory];
        OpenCLIntrinsics.createLocalMemory(localMemory, sizeLocalMemory);

        // Copy input data to local memory
        localMemory[localIdx] = inputArray[gidx];

        int start = localGroupSize / 2;
        // Reduction in local memory
        for (int stride = start; stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (stride > localIdx) {
                localMemory[localIdx] += localMemory[localIdx + stride];
            }
        }

        if (localIdx == 0) {
            int groupID = OpenCLIntrinsics.get_group_id(0);
            outputArray[groupID] = localMemory[0];
        }

        OpenCLIntrinsics.globalBarrier();
        if (gidx == 0) {
            int numGroups = globalSize / localGroupSize;
            for (int i = 1; i < numGroups; i++) {
                outputArray[0] += outputArray[i];
            }
        }
    }

    /**
     * Full reduction in global memory for GPU.
     * 
     * @param inputArray
     * @param outputArray
     * @param gidx
     */
    @Snippet
    public static void reduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);
        int globalSize = OpenCLIntrinsics.get_global_size(0);

        int myID = localIdx + (localGroupSize * groupID);

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                inputArray[myID] += inputArray[myID + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID] = inputArray[myID];
        }

        OpenCLIntrinsics.globalBarrier();
        if (myID == 0) {
            int numGroups = globalSize / localGroupSize;
            int acc = outputArray[0];
            for (int i = 1; i < numGroups; i++) {
                OpenCLIntrinsics.printEmpty();
                acc += outputArray[i];
            }
            outputArray[0] = acc;
        }
    }

    /**
     * Full reduction in global memory for GPU.
     * 
     * @param inputArray
     * @param outputArray
     * @param gidx
     */
    @Snippet
    public static void reduceIntAddLocalMemory(int[] inputArray, int[] outputArray, int gidx) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);
        int globalSize = OpenCLIntrinsics.get_global_size(0);

        int myID = localIdx + (localGroupSize * groupID);

        int sizeLocalMemory = 512;
        int[] localMemory = new int[sizeLocalMemory];
        OpenCLIntrinsics.createLocalMemory(localMemory, sizeLocalMemory);
        localMemory[localIdx] = inputArray[myID];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localMemory[localIdx] += localMemory[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID] = localMemory[0];
        }

        OpenCLIntrinsics.globalBarrier();
        if (myID == 0) {
            int numGroups = globalSize / localGroupSize;
            int acc = outputArray[0];
            for (int i = 1; i < numGroups; i++) {
                OpenCLIntrinsics.printEmpty();
                acc += outputArray[i];
            }
            outputArray[0] = acc;
        }
    }

    public static class Templates extends AbstractTemplates {

        @SuppressWarnings("unused")
        private final SnippetInfo reduceIntSnippet = snippet(ReduceSnippets.class, "reduceIntAdd");
        @SuppressWarnings("unused")
        private final SnippetInfo reduceIntSnippetGlobal = snippet(ReduceSnippets.class, "reduceIntAddGlobal");

        @SuppressWarnings("unused")
        private final SnippetInfo reduceIntSnippetLocalMemory = snippet(ReduceSnippets.class, "reduceIntAddLocalMemory");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, ValueNode globalId, GlobalThreadSizeNode globalSize, LoweringTool tool) {

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

            SnippetInfo snippet = reduceIntSnippetGlobal;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", globalId);

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, DEFAULT_REPLACER, args);

        }

    }
}
