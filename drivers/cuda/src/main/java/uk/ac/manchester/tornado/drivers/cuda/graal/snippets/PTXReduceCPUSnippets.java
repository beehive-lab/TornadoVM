/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
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
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.builtins.PTXIntrinsics;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;

/**
 * Graal-Snippets for CPU OpenCL reductions.
 *
 */
public class PTXReduceCPUSnippets implements Snippets {

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
    public static void partialReduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntAddGlobal2(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceIntMulGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntMulGlobal2(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceIntMaxGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceIntMinGlobal(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    // Long

    @Snippet
    public static void partialReduceLongAddGlobal(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongAddGlobal2(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceLongMulGlobal(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongMulGlobal2(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceLongMaxGlobal(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceLongMinGlobal(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    // Float

    @Snippet
    public static void partialReduceFloatAddGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatAddGlobal2(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceFloatMulGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatMulGlobal2(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceFloatMaxGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceFloatMinGlobal(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    // Double

    @Snippet
    public static void partialReduceDoubleAddGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddGlobal2(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMulGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleMulGlobal2(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMaxGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceDoubleMinGlobal(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        PTXIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    public static class Templates extends AbstractTemplates implements TornadoSnippetTypeInference {

        // Int
        private final SnippetInfo partialReduceAddIntSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceIntAddGlobal");
        private final SnippetInfo partialReduceAddIntSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceIntAddGlobal2");
        private final SnippetInfo partialReduceMulIntSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceIntMulGlobal");
        private final SnippetInfo partialReduceMulIntSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceIntMulGlobal2");
        private final SnippetInfo partialReduceMaxIntSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceIntMaxGlobal");
        private final SnippetInfo partialReduceMinIntSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceIntMinGlobal");

        // Long
        private final SnippetInfo partialReduceAddLongSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceLongAddGlobal");
        private final SnippetInfo partialReduceAddLongSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceLongAddGlobal2");
        private final SnippetInfo partialReduceMulLongSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceLongMulGlobal");
        private final SnippetInfo partialReduceMulLongSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceLongMulGlobal2");
        private final SnippetInfo partialReduceMaxLongSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceLongMaxGlobal");
        private final SnippetInfo partialReduceMinLongSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceLongMinGlobal");

        // Float
        private final SnippetInfo partialReduceAddFloatSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatAddGlobal");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatAddGlobal2");
        private final SnippetInfo partialReduceMulFloatSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatMulGlobal");
        private final SnippetInfo partialReduceMulFloatSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatMulGlobal2");
        private final SnippetInfo partialReduceMaxFloatSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatMaxGlobal");
        private final SnippetInfo partialReduceMinFloatSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceFloatMinGlobal");

        // Double
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleAddGlobal");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleAddGlobal2");
        private final SnippetInfo partialReduceMulDoubleSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleMulGlobal");
        private final SnippetInfo partialReduceMulDoubleSnippetGlobal2 = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleMulGlobal2");
        private final SnippetInfo partialReduceMaxDoubleSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleMaxGlobal");
        private final SnippetInfo partialReduceMinDoubleSnippetGlobal = snippet(PTXReduceCPUSnippets.class, "partialReduceDoubleMinGlobal");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> debugHandlersFactories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, debugHandlersFactories, providers, snippetReflection, target);
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeInteger(PTXIntBinaryIntrinsicNode value) {
            switch (value.operation()) {
                case MAX:
                    return partialReduceMaxIntSnippetGlobal;
                case MIN:
                    return partialReduceMinIntSnippetGlobal;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeLong(PTXIntBinaryIntrinsicNode value) {
            switch (value.operation()) {
                case MAX:
                    return partialReduceMaxLongSnippetGlobal;
                case MIN:
                    return partialReduceMinLongSnippetGlobal;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddIntSnippetGlobal : partialReduceAddIntSnippetGlobal2;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulIntSnippetGlobal : partialReduceMulIntSnippetGlobal2;
            } else if (value instanceof PTXIntBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeInteger((PTXIntBinaryIntrinsicNode) value);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddLongSnippetGlobal : partialReduceAddLongSnippetGlobal2;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulLongSnippetGlobal : partialReduceMulLongSnippetGlobal2;
            } else if (value instanceof PTXIntBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeLong((PTXIntBinaryIntrinsicNode) value);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNode(PTXFPBinaryIntrinsicNode value) {
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
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippetGlobal : partialReduceAddFloatSnippetGlobal2;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulFloatSnippetGlobal : partialReduceMulFloatSnippetGlobal2;
            } else if (value instanceof PTXFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNode((PTXFPBinaryIntrinsicNode) value);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeDouble(PTXFPBinaryIntrinsicNode value) {
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
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddDoubleSnippetGlobal : partialReduceAddDoubleSnippetGlobal2;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceMulDoubleSnippetGlobal : partialReduceMulDoubleSnippetGlobal2;
            } else if (value instanceof PTXFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeDouble((PTXFPBinaryIntrinsicNode) value);
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

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
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
