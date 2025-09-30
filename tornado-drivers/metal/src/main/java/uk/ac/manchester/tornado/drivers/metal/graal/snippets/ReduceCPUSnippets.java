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
package uk.ac.manchester.tornado.drivers.metal.graal.snippets;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.nodes.GraphState;
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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.metal.builtins.MetalIntrinsics;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;

/**
 * Graal-Snippets for CPU Metal reductions.
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
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntAddCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceIntMul(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceIntMulCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceIntMax(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceIntMaxCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceIntMin(int[] inputArray, int[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceIntMinCarrierValue(int[] inputArray, int[] outputArray, int gidx, int start, int globalID, int value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Long
    @Snippet
    public static void partialReduceLongAdd(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongAddCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceLongMul(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceLongMulCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceLongMax(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceLongMaxCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceLongMin(long[] inputArray, long[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceLongMinCarrierValue(long[] inputArray, long[] outputArray, int gidx, int start, int globalID, long value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Float
    @Snippet
    public static void partialReduceFloatAdd(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatAddCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceFloatMul(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceFloatMulCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceFloatMax(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceFloatMaxCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceFloatMin(float[] inputArray, float[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceFloatMinCarrierValue(float[] inputArray, float[] outputArray, int gidx, int start, int globalID, float value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    // Double
    @Snippet
    public static void partialReduceDoubleAdd(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] += value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMul(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= inputArray[gidx];
        }
    }

    @Snippet
    public static void partialReduceDoubleMulCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] *= value;
        }
    }

    @Snippet
    public static void partialReduceDoubleMax(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceDoubleMaxCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.max(outputArray[globalID + 1], value);
        }
    }

    @Snippet
    public static void partialReduceDoubleMin(double[] inputArray, double[] outputArray, int gidx, int start, int globalID) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], inputArray[gidx]);
        }
    }

    @Snippet
    public static void partialReduceDoubleMinCarrierValue(double[] inputArray, double[] outputArray, int gidx, int start, int globalID, double value) {
        MetalIntrinsics.localBarrier();
        if (gidx >= start) {
            outputArray[globalID + 1] = TornadoMath.min(outputArray[globalID + 1], value);
        }
    }

    protected static class Tuple2<T0, T1> {
        T0 t0;
        T1 t1;

        public Tuple2(T0 first, T1 second) {
            this.t0 = first;
            this.t1 = second;
        }

        public T0 f0() {
            return t0;
        }

        public T1 f1() {
            return t1;
        }
    }

    public static class Templates extends AbstractTemplates implements TornadoSnippetTypeInference {
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddIntSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntAdd");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulIntSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMulCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxIntSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMax");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxIntSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinIntSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMin");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinIntSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMinCarrierValue");
        // Long
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddLongSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongAdd");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddLongSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongAddCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulLongSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMul");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulLongSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMulCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxLongSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMax");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxLongSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinLongSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMin");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinLongSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceLongMinCarrierValue");
        // Float
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddFloatSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatAdd");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddFloatSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatAddCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulFloatSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMul");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulFloatSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMulCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxFloatSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMax");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxFloatSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinFloatSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMin");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinFloatSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceFloatMinCarrierValue");
        // Double
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddDoubleSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleAdd");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddDoubleSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleAddCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulDoubleSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMul");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulDoubleSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMulCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxDoubleSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMax");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMaxDoubleSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinDoubleSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMin");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMinDoubleSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceDoubleMinCarrierValue");
        // Additional tuple
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceAddIntSnippetCarrierValue = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntAddCarrierValue");
        private final Tuple2<Class<? extends ReduceCPUSnippets>, String> partialReduceMulIntSnippet = new Tuple2<>(ReduceCPUSnippets.class, "partialReduceIntMul");
        Providers providers;

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);
            this.providers = providers;
        }

        private SnippetInfo snippet(Tuple2<Class<? extends ReduceCPUSnippets>, String> tuple2) {
            return snippet(providers, tuple2.t0, tuple2.t1);

        }

        private SnippetInfo getSnippetFromMetalBinaryNodeInteger(MetalIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? snippet(partialReduceMaxIntSnippet) : snippet(partialReduceMaxIntSnippetCarrierValue);
                case MIN:
                    return (extra == null) ? snippet(partialReduceMinIntSnippet) : snippet(partialReduceMinIntSnippetCarrierValue);
                default:
                    throw new RuntimeException("MetalFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        private SnippetInfo getSnippetFromMetalBinaryNodeLong(MetalIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? snippet(partialReduceMaxLongSnippet) : snippet(partialReduceMaxLongSnippetCarrierValue);
                case MIN:
                    return (extra == null) ? snippet(partialReduceMinLongSnippet) : snippet(partialReduceMinLongSnippetCarrierValue);
                default:
                    throw new RuntimeException("MetalFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddIntSnippet) : snippet(partialReduceAddIntSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceMulIntSnippet) : snippet(partialReduceMulIntSnippetCarrierValue);
            } else if (value instanceof MetalIntBinaryIntrinsicNode) {
                snippet = getSnippetFromMetalBinaryNodeInteger((MetalIntBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddLongSnippet) : snippet(partialReduceAddLongSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceMulLongSnippet) : snippet(partialReduceMulLongSnippetCarrierValue);
            } else if (value instanceof MetalIntBinaryIntrinsicNode) {
                snippet = getSnippetFromMetalBinaryNodeLong((MetalIntBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromMetalBinaryNode(MetalFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return (extra == null) ? snippet(partialReduceMaxFloatSnippet) : snippet(partialReduceMaxFloatSnippetCarrierValue);
                case FMIN:
                    return (extra == null) ? snippet(partialReduceMinFloatSnippet) : snippet(partialReduceMinFloatSnippetCarrierValue);
                default:
                    throw new RuntimeException("MetalFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddFloatSnippet) : snippet(partialReduceAddFloatSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceMulFloatSnippet) : snippet(partialReduceMulFloatSnippetCarrierValue);
            } else if (value instanceof MetalFPBinaryIntrinsicNode) {
                snippet = getSnippetFromMetalBinaryNode((MetalFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromMetalBinaryNodeDouble(MetalFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return (extra == null) ? snippet(partialReduceMaxDoubleSnippet) : snippet(partialReduceMaxDoubleSnippetCarrierValue);
                case FMIN:
                    return (extra == null) ? snippet(partialReduceMinDoubleSnippet) : snippet(partialReduceMinDoubleSnippetCarrierValue);
                default:
                    throw new RuntimeException("MetalFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet = null;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddDoubleSnippet) : snippet(partialReduceAddDoubleSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceMulDoubleSnippet) : snippet(partialReduceMulDoubleSnippetCarrierValue);
            } else if (value instanceof MetalFPBinaryIntrinsicNode) {
                snippet = getSnippetFromMetalBinaryNodeDouble((MetalFPBinaryIntrinsicNode) value, extra);
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
            JavaKind elementKind = storeAtomicIndexed.elementKind();
            ValueNode value = storeAtomicIndexed.value();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();
            SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            // Sets the guard stage to AFTER_FSA because we want to avoid any frame state
            // assignment for the snippet (see SnippetTemplate::assignNecessaryFrameStates)
            // This is needed because we have nodes in the snippet which have multiple side
            // effects and this is not allowed (see
            // SnippetFrameStateAssignment.NodeStateAssignment.INVALID)
            Arguments args = new Arguments(snippet, GraphState.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", threadId);
            args.add("start", startIndexNode);
            args.add("globalID", globalID);
            if (extra != null) {
                args.add("value", extra);
            }
            template(tool, storeAtomicIndexed, args).instantiate(tool.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(WriteAtomicNode writeAtomic, ValueNode threadId, ValueNode globalID, ValueNode startIndexNode, LoweringTool tool) {
            JavaKind elementKind = writeAtomic.getElementKind();
            ValueNode value = writeAtomic.value();
            ValueNode extra = writeAtomic.getExtraOperation();
            SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            // Sets the guard stage to AFTER_FSA because we want to avoid any frame state
            // assignment for the snippet (see SnippetTemplate::assignNecessaryFrameStates)
            // This is needed because we have nodes in the snippet which have multiple side
            // effects and this is not allowed (see
            // SnippetFrameStateAssignment.NodeStateAssignment.INVALID)
            Arguments args = new Arguments(snippet, GraphState.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", writeAtomic.getInputArray());
            args.add("outputArray", writeAtomic.getOutArray());
            args.add("gidx", threadId);
            args.add("start", startIndexNode);
            args.add("globalID", globalID);
            if (extra != null) {
                args.add("value", extra);
            }
            template(tool, writeAtomic, args).instantiate(tool.getMetaAccess(), writeAtomic, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
