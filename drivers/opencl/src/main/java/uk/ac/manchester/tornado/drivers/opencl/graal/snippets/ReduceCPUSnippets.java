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
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;

/**
 * Graal Snippets for CPU OpenCL reductions.
 * 
 */
public class ReduceCPUSnippets implements Snippets {

    /**
     * Reduction array has to be of size = number of local threads (CPU
     * threads).
     * 
     * @param inputArray
     * @param outputArray
     * @param gidx
     * @param start
     * @param numThreads
     * @param globalID
     */
    @Snippet
    public static void partialReduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += inputArray[gidx];
    }

    @Snippet
    public static void partialReduceIntAddGlobal2(int[] inputArray, int[] outputArray, int gidx, int start, int numThreads, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += value;
    }

    @Snippet
    public static void partialReduceIntMulGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= inputArray[gidx];
    }

    @Snippet
    public static void partialReduceIntMulGlobal2(int[] inputArray, int[] outputArray, int gidx, int start, int numThreads, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= value;
    }

    @Snippet
    public static void partialReduceFloatAddGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += inputArray[gidx];
    }

    @Snippet
    public static void partialReduceFloatAddGlobal2(float[] inputArray, float[] outputArray, int gidx, int start, int numThreads, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += value;
    }

    @Snippet
    public static void partialReduceFloatMulGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= inputArray[gidx];
    }

    @Snippet
    public static void partialReduceFloatMulGlobal2(float[] inputArray, float[] outputArray, int gidx, int start, int numThreads, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= value;
    }

    @Snippet
    public static void partialReduceDoubleAddGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += inputArray[gidx];
    }

    @Snippet
    public static void partialReduceDoubleAddGlobal2(double[] inputArray, double[] outputArray, int gidx, int start, int numThreads, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] += value;
    }

    @Snippet
    public static void partialReduceDoubleMulGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int numThreads, int globalID) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= inputArray[gidx];
    }

    @Snippet
    public static void partialReduceDoubleMulGlobal2(double[] inputArray, double[] outputArray, int gidx, int start, int numThreads, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        outputArray[globalID] *= value;
    }

    public static class Templates extends AbstractTemplates {

        // Int
        private final SnippetInfo partialReduceAddIntSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceIntAddGlobal");
        private final SnippetInfo partialReduceAddIntSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceIntAddGlobal2");
        private final SnippetInfo partialReduceMulIntSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceIntMulGlobal");
        private final SnippetInfo partialReduceMulIntSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceIntMulGlobal2");

        // Float
        private final SnippetInfo partialReduceAddFloatSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceFloatAddGlobal");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceFloatAddGlobal2");
        private final SnippetInfo partialReduceMulFloatSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceFloatMulGlobal");
        private final SnippetInfo partialReduceMulFloatSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceFloatMulGlobal2");

        // Double
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceDoubleAddGlobal");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceDoubleAddGlobal2");
        private final SnippetInfo partialReduceMulDoubleSnippetGlobal = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMulGlobal");
        private final SnippetInfo partialReduceMulDoubleSnippetGlobal2 = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMulGlobal2");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        private SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddIntSnippetGlobal : partialReduceAddIntSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulIntSnippetGlobal : partialReduceMulIntSnippetGlobal2;
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippetGlobal : partialReduceAddFloatSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulFloatSnippetGlobal : partialReduceMulFloatSnippetGlobal2;
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddDoubleSnippetGlobal : partialReduceAddDoubleSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulDoubleSnippetGlobal : partialReduceMulDoubleSnippetGlobal2;
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetInfo(JavaKind elementKind, ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (elementKind == JavaKind.Int) {
                snippet = inferIntSnippet(value, extra);
            } else if (elementKind == JavaKind.Float) {
                snippet = inferFloatSnippet(value, extra);
            } else if (elementKind == JavaKind.Double) {
                snippet = inferDoubleSnippet(value, extra);
            } else {
                throw new RuntimeException("Data type not supported");
            }
            return snippet;
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, ValueNode threadId, GlobalThreadSizeNode globalSize, ValueNode startNode,
                ValueNode globalID, LoweringTool tool) {

            StructuredGraph graph = storeAtomicIndexed.graph();

            JavaKind elementKind = storeAtomicIndexed.elementKind();

            ValueNode value = storeAtomicIndexed.value();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();

            SnippetInfo snippet = getSnippetInfo(elementKind, value, extra);

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", threadId);
            args.add("start", startNode);
            int numThreads = Runtime.getRuntime().availableProcessors();
            args.add("numThreads", numThreads);
            args.add("globalID", globalID);
            if (extra != null) {
                args.add("value", extra);
            }

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
