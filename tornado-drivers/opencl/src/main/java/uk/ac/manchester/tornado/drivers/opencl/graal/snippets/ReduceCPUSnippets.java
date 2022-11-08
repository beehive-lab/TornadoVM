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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import org.graalvm.compiler.api.replacements.Snippet;
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

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;

/**
 * Graal-Snippets for CPU OpenCL reductions.
 *
 */
public class ReduceCPUSnippets implements Snippets {

    /**
     * Reduction array has to be of size = number of local threads (CPU threads).
     *
     * @param inputArray
     * @param outputArray
     * @param gidx
     * @param start
     * @param globalID
     */
    @Snippet
    public static void partialReduceIntAdd(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntAddCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceIntMul(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntMulCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceIntMax(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceIntMaxCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceIntMin(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceIntMinCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Long
    @Snippet
    public static void partialReduceLongAdd(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongAddCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceLongMul(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongMulCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceLongMax(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceLongMaxCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceLongMin(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceLongMinCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Float
    @Snippet
    public static void partialReduceFloatAdd(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatAddCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceFloatMul(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatMulCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceFloatMax(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceFloatMaxCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceFloatMin(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceFloatMinCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Double
    @Snippet
    public static void partialReduceDoubleAdd(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMul(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleMulCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMax(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceDoubleMaxCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceDoubleMin(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceDoubleMinCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        OpenCLIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    public static class Templates extends AbstractTemplates implements TornadoSnippetTypeInference {

        // Int
        private final SnippetInfo partialReduceAddIntSnippet = snippet(ReduceCPUSnippets.class, "partialReduceIntAdd");
        private final SnippetInfo partialReduceAddIntSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceIntAddCarrierValue");
        private final SnippetInfo partialReduceMulIntSnippet = snippet(ReduceCPUSnippets.class, "partialReduceIntMul");
        private final SnippetInfo partialReduceMulIntSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceIntMulCarrierValue");
        private final SnippetInfo partialReduceMaxIntSnippet = snippet(ReduceCPUSnippets.class, "partialReduceIntMax");
        private final SnippetInfo partialReduceMaxIntSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceIntMaxCarrierValue");
        private final SnippetInfo partialReduceMinIntSnippet = snippet(ReduceCPUSnippets.class, "partialReduceIntMin");
        private final SnippetInfo partialReduceMinIntSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceIntMinCarrierValue");

        // Long
        private final SnippetInfo partialReduceAddLongSnippet = snippet(ReduceCPUSnippets.class, "partialReduceLongAdd");
        private final SnippetInfo partialReduceAddLongSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceLongAddCarrierValue");
        private final SnippetInfo partialReduceMulLongSnippet = snippet(ReduceCPUSnippets.class, "partialReduceLongMul");
        private final SnippetInfo partialReduceMulLongSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceLongMulCarrierValue");
        private final SnippetInfo partialReduceMaxLongSnippet = snippet(ReduceCPUSnippets.class, "partialReduceLongMax");
        private final SnippetInfo partialReduceMaxLongSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceLongMaxCarrierValue");
        private final SnippetInfo partialReduceMinLongSnippet = snippet(ReduceCPUSnippets.class, "partialReduceLongMin");
        private final SnippetInfo partialReduceMinLongSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceLongMinCarrierValue");

        // Float
        private final SnippetInfo partialReduceAddFloatSnippet = snippet(ReduceCPUSnippets.class, "partialReduceFloatAdd");
        private final SnippetInfo partialReduceAddFloatSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceFloatAddCarrierValue");
        private final SnippetInfo partialReduceMulFloatSnippet = snippet(ReduceCPUSnippets.class, "partialReduceFloatMul");
        private final SnippetInfo partialReduceMulFloatSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceFloatMulCarrierValue");
        private final SnippetInfo partialReduceMaxFloatSnippet = snippet(ReduceCPUSnippets.class, "partialReduceFloatMax");
        private final SnippetInfo partialReduceMaxFloatSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceFloatMaxCarrierValue");
        private final SnippetInfo partialReduceMinFloatSnippet = snippet(ReduceCPUSnippets.class, "partialReduceFloatMin");
        private final SnippetInfo partialReduceMinFloatSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceFloatMinCarrierValue");

        // Double
        private final SnippetInfo partialReduceAddDoubleSnippet = snippet(ReduceCPUSnippets.class, "partialReduceDoubleAdd");
        private final SnippetInfo partialReduceAddDoubleSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceDoubleAddCarrierValue");
        private final SnippetInfo partialReduceMulDoubleSnippet = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMul");
        private final SnippetInfo partialReduceMulDoubleSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMulCarrierValue");
        private final SnippetInfo partialReduceMaxDoubleSnippet = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMax");
        private final SnippetInfo partialReduceMaxDoubleSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMaxCarrierValue");
        private final SnippetInfo partialReduceMinDoubleSnippet = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMin");
        private final SnippetInfo partialReduceMinDoubleSnippetCarrierValue = snippet(ReduceCPUSnippets.class, "partialReduceDoubleMinCarrierValue");

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeInteger(OCLIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceMaxIntSnippet : partialReduceMaxIntSnippetCarrierValue;
                case MIN:
                    return (extra == null) ? partialReduceMinIntSnippet : partialReduceMinIntSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeLong(OCLIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceMaxLongSnippet : partialReduceMaxLongSnippetCarrierValue;
                case MIN:
                    return (extra == null) ? partialReduceMinLongSnippet : partialReduceMinLongSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddIntSnippet : partialReduceAddIntSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulIntSnippet : partialReduceMulIntSnippetCarrierValue;
            } else if (value instanceof OCLIntBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeInteger((OCLIntBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddLongSnippet : partialReduceAddLongSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulLongSnippet : partialReduceMulLongSnippetCarrierValue;
            } else if (value instanceof OCLIntBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeLong((OCLIntBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNode(OCLFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return (extra == null) ? partialReduceMaxFloatSnippet : partialReduceMaxFloatSnippetCarrierValue;
                case FMIN:
                    return (extra == null) ? partialReduceMinFloatSnippet : partialReduceMinFloatSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippet : partialReduceAddFloatSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulFloatSnippet : partialReduceMulFloatSnippetCarrierValue;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNode((OCLFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeDouble(OCLFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return (extra == null) ? partialReduceMaxDoubleSnippet : partialReduceMaxDoubleSnippetCarrierValue;
                case FMIN:
                    return (extra == null) ? partialReduceMinDoubleSnippet : partialReduceMinDoubleSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddDoubleSnippet : partialReduceAddDoubleSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulDoubleSnippet : partialReduceMulDoubleSnippetCarrierValue;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeDouble((OCLFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo getSnippetInstance(JavaKind elementKind, ValueNode value, ValueNode extra) {
            SnippetInfo snippet;
            if (elementKind == JavaKind.Int) {
                snippet = inferIntSnippet(value, extra);
            } else if (elementKind == JavaKind.Long) {
                snippet = inferLongSnippet(value, extra);
            } else if (elementKind == JavaKind.Float) {
                snippet = inferFloatSnippet(value, extra);
            } else if (elementKind == JavaKind.Double) {
                snippet = inferDoubleSnippet(value, extra);
            } else {
                throw new RuntimeException("Data type not supported");
            }
            return snippet;
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, ValueNode threadId, ValueNode globalID, ValueNode startIndexNode, LoweringTool tool) {

            StructuredGraph graph = storeAtomicIndexed.graph();
            JavaKind elementKind = storeAtomicIndexed.elementKind();
            ValueNode value = storeAtomicIndexed.value();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();

            SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            // Sets the guard stage to AFTER_FSA because we want to avoid any frame state
            // assignment for the snippet (see SnippetTemplate::assignNecessaryFrameStates)
            // This is needed because we have nodes in the snippet which have multiple side
            // effects and this is not allowed (see
            // SnippetFrameStateAssignment.NodeStateAssignment.INVALID)
            Arguments args = new Arguments(snippet, StructuredGraph.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", threadId);
            args.add("start", startIndexNode);
            args.add("globalID", globalID);
            if (extra != null) {
                args.add("value", extra);
            }

            template(storeAtomicIndexed, args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
