/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.snippets;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.TornadoSnippetTypeInference;
import uk.ac.manchester.tornado.drivers.spirv.builtins.SPIRVOCLIntrinsics;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;

/**
 * Tornado-Graal snippets for GPUs reductions using OpenCL semantics.
 */
public class ReduceGPUSnippets implements Snippets {

    /**
     * Dummy value for local memory allocation. The actual value to be allocated is
     * replaced in later stages of the JIT compiler.
     */
    private static int LOCAL_WORK_GROUP_SIZE = 223;

    @Snippet
    public static void partialReduceIntAdd(int[] inputArray, int[] outputArray, int gidx) {
        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntAddCarrierValue(int[] inputArray, int[] outputArray, int gidx, int value) {

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        localArray[localIdx] = value;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }
        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongAdd(long[] inputArray, long[] outputArray, int gidx) {
        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongAddCarrierValue(long[] inputArray, long[] outputArray, int gidx, long value) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);

        inputArray[myID] = value;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                inputArray[myID] += inputArray[myID + stride];

            }
        }
        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = inputArray[myID];
        }
    }

    @Snippet
    public static void partialReduceFloatAdd(float[] inputArray, float[] outputArray, int gidx) {
        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatAddCarrierValue(float[] inputArray, float[] outputArray, int gidx, float value) {

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);
        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleAdd(double[] inputArray, double[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleAddCarrierValue(double[] inputArray, double[] outputArray, int gidx, double value) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] += localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMult(int[] inputArray, int[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMultCarrierValue(int[] inputArray, int[] outputArray, int gidx, int value) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMult(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMultCarrierValue(long[] inputArray, long[] outputArray, int gidx, long value) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);

        inputArray[myID] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                inputArray[myID] *= inputArray[myID + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = inputArray[myID];
        }
    }

    @Snippet
    public static void partialReduceFloatMult(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMultCarrierValue(float[] inputArray, float[] outputArray, int gidx, float value) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];

            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMult(double[] inputArray, double[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMultCarrierValue(double[] inputArray, double[] outputArray, int gidx, double value) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] *= localArray[localIdx + stride];

            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMax(int[] inputArray, int[] outputArray, int gidx) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMaxCarrierValue(int[] inputArray, int[] outputArray, int gidx, int extra) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMax(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMaxCarrierValue(long[] inputArray, long[] outputArray, int gidx, long extra) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMax(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMaxCarrierValue(float[] inputArray, float[] outputArray, int gidx, float extra) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMax(double[] inputArray, double[] outputArray, int gidx) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMaxCarrierValue(double[] inputArray, double[] outputArray, int gidx, double extra) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.max(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMin(int[] inputArray, int[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceIntMinCarrierValue(int[] inputArray, int[] outputArray, int gidx, int extra) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMin(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceLongMinCarrierValue(long[] inputArray, long[] outputArray, int gidx, long extra) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMin(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceFloatMinCarrierValue(float[] inputArray, float[] outputArray, int gidx, float extra) {

        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMin(double[] inputArray, double[] outputArray, int gidx) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = inputArray[gidx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    @Snippet
    public static void partialReduceDoubleMinCarrierValue(double[] inputArray, double[] outputArray, int gidx, double extra) {
        int localIdx = SPIRVOCLIntrinsics.get_local_id(0);
        int localGroupSize = SPIRVOCLIntrinsics.get_local_size(0);
        int groupID = SPIRVOCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            SPIRVOCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localArray[localIdx] = TornadoMath.min(localArray[localIdx], localArray[localIdx + stride]);
            }
        }

        SPIRVOCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = localArray[0];
        }
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates implements TornadoSnippetTypeInference {
        // Add
        private final SnippetTemplate.SnippetInfo partialReduceIntSnippet = snippet(ReduceGPUSnippets.class, "partialReduceIntAdd");
        private final SnippetTemplate.SnippetInfo partialReduceIntSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceIntAddCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceLongSnippet = snippet(ReduceGPUSnippets.class, "partialReduceLongAdd");
        private final SnippetTemplate.SnippetInfo partialReduceLongSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceLongAddCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceAddFloatSnippet = snippet(ReduceGPUSnippets.class, "partialReduceFloatAdd");
        private final SnippetTemplate.SnippetInfo partialReduceAddFloatSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceFloatAddCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceAddDoubleSnippet = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAdd");
        private final SnippetTemplate.SnippetInfo partialReduceAddDoubleSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAddCarrierValue");

        // Mul
        private final SnippetTemplate.SnippetInfo partialReduceIntMultSnippet = snippet(ReduceGPUSnippets.class, "partialReduceIntMult");
        private final SnippetTemplate.SnippetInfo partialReduceIntMultSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceIntMultCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceLongMultSnippet = snippet(ReduceGPUSnippets.class, "partialReduceLongMult");
        private final SnippetTemplate.SnippetInfo partialReduceLongMultSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceLongMultCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceFloatMultSnippet = snippet(ReduceGPUSnippets.class, "partialReduceFloatMult");
        private final SnippetTemplate.SnippetInfo partialReduceFloatMultSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceFloatMultCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceDoubleMultSnippet = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMult");
        private final SnippetTemplate.SnippetInfo partialReduceDoubleMultSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMultCarrierValue");

        // Max
        private final SnippetTemplate.SnippetInfo partialReduceIntMaxSnippet = snippet(ReduceGPUSnippets.class, "partialReduceIntMax");
        private final SnippetTemplate.SnippetInfo partialReduceIntMaxSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceIntMaxCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceLongMaxSnippet = snippet(ReduceGPUSnippets.class, "partialReduceLongMax");
        private final SnippetTemplate.SnippetInfo partialReduceLongMaxSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceLongMaxCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceMaxFloatSnippet = snippet(ReduceGPUSnippets.class, "partialReduceFloatMax");
        private final SnippetTemplate.SnippetInfo partialReduceMaxFloatSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceFloatMaxCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceMaxDoubleSnippet = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMax");
        private final SnippetTemplate.SnippetInfo partialReduceMaxDoubleSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMaxCarrierValue");

        // Min
        private final SnippetTemplate.SnippetInfo partialReduceIntMinSnippet = snippet(ReduceGPUSnippets.class, "partialReduceIntMin");
        private final SnippetTemplate.SnippetInfo partialReduceIntMinSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceIntMinCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceLongMinSnippet = snippet(ReduceGPUSnippets.class, "partialReduceLongMin");
        private final SnippetTemplate.SnippetInfo partialReduceLongMinSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceLongMinCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceMinFloatSnippet = snippet(ReduceGPUSnippets.class, "partialReduceFloatMin");
        private final SnippetTemplate.SnippetInfo partialReduceMinFloatSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceFloatMinCarrierValue");
        private final SnippetTemplate.SnippetInfo partialReduceMinDoubleSnippet = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMin");
        private final SnippetTemplate.SnippetInfo partialReduceMinDoubleSnippetCarrierValue = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMinCarrierValue");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> debugHandlersFactories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, debugHandlersFactories, providers, snippetReflection, target);
        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeInteger(SPIRVIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceIntMaxSnippet : partialReduceIntMaxSnippetCarrierValue;
                case MIN:
                    return (extra == null) ? partialReduceIntMinSnippet : partialReduceIntMinSnippetCarrierValue;
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeLong(SPIRVIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceLongMaxSnippet : partialReduceLongMaxSnippetCarrierValue;
                case MIN:
                    return (extra == null) ? partialReduceLongMinSnippet : partialReduceLongMinSnippetCarrierValue;
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceIntSnippet : partialReduceIntSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                // operation = ATOMIC_OPERATION.MUL;
                snippet = (extra == null) ? partialReduceIntMultSnippet : partialReduceIntMultSnippetCarrierValue;
            } else if (value instanceof SPIRVIntBinaryIntrinsicNode) {
                SPIRVIntBinaryIntrinsicNode op = (SPIRVIntBinaryIntrinsicNode) value;
                snippet = getSnippetFromOCLBinaryNodeInteger(op, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetTemplate.SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceLongSnippet : partialReduceLongSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceLongMultSnippet : partialReduceLongMultSnippetCarrierValue;
            } else if (value instanceof SPIRVIntBinaryIntrinsicNode) {
                SPIRVIntBinaryIntrinsicNode op = (SPIRVIntBinaryIntrinsicNode) value;
                snippet = getSnippetFromOCLBinaryNodeLong(op, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeInteger(SPIRVFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return extra == null ? partialReduceMaxFloatSnippet : partialReduceMaxFloatSnippetCarrierValue;
                case FMIN:
                    return extra == null ? partialReduceMinFloatSnippet : partialReduceMinFloatSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippet : partialReduceAddFloatSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceFloatMultSnippet : partialReduceFloatMultSnippetCarrierValue;
            } else if (value instanceof SPIRVFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeInteger((SPIRVFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeDouble(SPIRVFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return extra == null ? partialReduceMaxDoubleSnippet : partialReduceMaxDoubleSnippetCarrierValue;
                case FMIN:
                    return extra == null ? partialReduceMinDoubleSnippet : partialReduceMinDoubleSnippetCarrierValue;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddDoubleSnippet : partialReduceAddDoubleSnippetCarrierValue;
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? partialReduceDoubleMultSnippet : partialReduceDoubleMultSnippetCarrierValue;
            } else if (value instanceof SPIRVFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeDouble((SPIRVFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetTemplate.SnippetInfo getSnippetInstance(JavaKind elementKind, ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet = null;
            if (elementKind == JavaKind.Int) {
                snippet = inferIntSnippet(value, extra);
            } else if (elementKind == JavaKind.Long) {
                snippet = inferLongSnippet(value, extra);
            } else if (elementKind == JavaKind.Float) {
                snippet = inferFloatSnippet(value, extra);
            } else if (elementKind == JavaKind.Double) {
                snippet = inferDoubleSnippet(value, extra);
            }
            return snippet;
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, ValueNode globalId, GlobalThreadSizeNode globalSize, LoweringTool tool) {

            JavaKind elementKind = storeAtomicIndexed.elementKind();
            ValueNode value = storeAtomicIndexed.value();
            ValueNode extra = storeAtomicIndexed.getExtraOperation();

            SnippetTemplate.SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            // Sets the guard stage to AFTER_FSA because we want to avoid any frame state
            // assignment for the snippet (see SnippetTemplate::assignNecessaryFrameStates)
            // This is needed because we have nodes in the snippet which have multiple side
            // effects and this is not allowed (see
            // SnippetFrameStateAssignment.NodeStateAssignment.INVALID)
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, StructuredGraph.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", globalId);
            if (extra != null) {
                args.add("value", extra);
            }

            SnippetTemplate template = template(storeAtomicIndexed, args);
            template.instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

}
