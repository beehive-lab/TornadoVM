/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;

/**
 * Graal Snippets for GPU OpenCL reductions.
 * 
 */
public class ReduceGPUSnippets implements Snippets {

    private static int LOCAL_WORK_GROUP_SIZE = 223;

    @Snippet
    public static void partialReduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx) {
        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntAddGlobal2(int[] inputArray, int[] outputArray, int gidx, int value) {

        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        localArray[localIdx] = value;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }
        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatAddGlobal(float[] inputArray, float[] outputArray, int gidx) {
        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatAddGlobal2(float[] inputArray, float[] outputArray, int gidx, float value) {

        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddGlobal(double[] inputArray, double[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddGlobal2(double[] inputArray, double[] outputArray, int gidx, double value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMultGlobal(int[] inputArray, int[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMultGlobal2(int[] inputArray, int[] outputArray, int gidx, int value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMultGlobal(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMultGlobal2(float[] inputArray, float[] outputArray, int gidx, float value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];

            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMultGlobal(double[] inputArray, double[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMultGlobal2(double[] inputArray, double[] outputArray, int gidx, double value) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];

            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMaxGlobal(int[] inputArray, int[] outputArray, int gidx) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMaxGlobal(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMaxGlobal(double[] inputArray, double[] outputArray, int gidx) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMinGlobal(int[] inputArray, int[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = new int[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMinGlobal(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = new float[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMinGlobal(double[] inputArray, double[] outputArray, int gidx) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = new double[LOCAL_WORK_GROUP_SIZE];

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    public static class Templates extends AbstractTemplates implements TornadoSnippetTypeInference {

        // Add
        private final SnippetInfo partialReduceIntSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntAddGlobal");
        private final SnippetInfo partialReduceIntSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceIntAddGlobal2");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatAddGlobal");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatAddGlobal2");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAddGlobal");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAddGlobal2");

        // Mul
        private final SnippetInfo partialReduceIntMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMultGlobal");
        private final SnippetInfo partialReduceIntMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceIntMultGlobal2");
        private final SnippetInfo partialReducetFloatMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMultGlobal");
        private final SnippetInfo partialReducetFloatMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatMultGlobal2");
        private final SnippetInfo partialReducetDoubleMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMultGlobal");
        private final SnippetInfo partialReducetDoubleMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMultGlobal2");

        // Max
        private final SnippetInfo partialReduceIntMaxSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMaxGlobal");
        private final SnippetInfo partialReduceMaxFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMaxGlobal");
        private final SnippetInfo partialReduceMaxDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMaxGlobal");

        // Min
        private final SnippetInfo partialReduceIntMinSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMinGlobal");
        private final SnippetInfo partialReduceMinFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMinGlobal");
        private final SnippetInfo partialReduceMinDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMinGlobal");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        private SnippetInfo getSnippetFromOCLBinaryNode(OCLIntBinaryIntrinsicNode value) {
            switch (value.operation()) {
                case MAX:
                    return partialReduceIntMaxSnippetGlobal;
                case MIN:
                    return partialReduceIntMinSnippetGlobal;
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        @Override
        public SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceIntSnippetGlobal : partialReduceIntSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                // operation = ATOMIC_OPERATION.MUL;
                snippet = (extra == null) ? partialReduceIntMultSnippetGlobal : partialReduceIntMultSnippetGlobal2;
            } else if (value instanceof OCLIntBinaryIntrinsicNode) {
                OCLIntBinaryIntrinsicNode op = (OCLIntBinaryIntrinsicNode) value;
                snippet = getSnippetFromOCLBinaryNode(op);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNode(OCLFPBinaryIntrinsicNode value) {
            switch (value.operation()) {
                case FMAX:
                    return partialReduceMaxFloatSnippetGlobal;
                case FMIN:
                    return partialReduceMinFloatSnippetGlobal;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippetGlobal : partialReduceAddFloatSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReducetFloatMultSnippetGlobal : partialReducetFloatMultSnippetGlobal2;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNode((OCLFPBinaryIntrinsicNode) value);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeDouble(OCLFPBinaryIntrinsicNode value) {
            switch (value.operation()) {
                case FMAX:
                    return partialReduceMaxDoubleSnippetGlobal;
                case FMIN:
                    return partialReduceMinDoubleSnippetGlobal;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddDoubleSnippetGlobal : partialReduceAddDoubleSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReducetDoubleMultSnippetGlobal : partialReducetDoubleMultSnippetGlobal2;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeDouble((OCLFPBinaryIntrinsicNode) value);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo getSnippetInstance(JavaKind elementKind, ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (elementKind == JavaKind.Int) {
                snippet = inferIntSnippet(value, extra);
            } else if (elementKind == JavaKind.Float) {
                snippet = inferFloatSnippet(value, extra);
            } else if (elementKind == JavaKind.Double) {
                snippet = inferDoubleSnippet(value, extra);
            }
            return snippet;
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, ValueNode globalId, GlobalThreadSizeNode globalSize, LoweringTool tool) {

            StructuredGraph graph = storeAtomicIndexed.graph();
            JavaKind elementKind = storeAtomicIndexed.elementKind();
            ValueNode value = storeAtomicIndexed.value();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();

            SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", globalId);
            if (extra != null) {
                // extra.as
                args.add("value", extra);
            }

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
