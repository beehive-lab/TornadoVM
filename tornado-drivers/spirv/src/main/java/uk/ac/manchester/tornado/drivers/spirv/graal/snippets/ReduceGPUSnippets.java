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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.snippets;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.TornadoSnippetTypeInference;
import uk.ac.manchester.tornado.drivers.spirv.builtins.SPIRVOCLIntrinsics;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;

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

    public static class Templates extends SnippetTemplate.AbstractTemplates implements TornadoSnippetTypeInference {
        // Add
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntAdd");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntAddCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongAdd");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongAddCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceAddFloatSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatAdd");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceAddFloatSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatAddCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceAddDoubleSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleAdd");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceAddDoubleSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleAddCarrierValue");

        // Mul
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMultSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMult");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMultSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMultCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMultSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMult");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMultSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMultCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceFloatMultSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMult");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceFloatMultSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMultCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceDoubleMultSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMult");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceDoubleMultSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMultCarrierValue");

        // Max
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMaxSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMax");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMaxSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMaxSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMax");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMaxSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMaxFloatSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMax");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMaxFloatSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMaxCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMaxDoubleSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMax");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMaxDoubleSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMaxCarrierValue");

        // Min
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMinSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMin");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceIntMinSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceIntMinCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMinSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMin");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceLongMinSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceLongMinCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMinFloatSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMin");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMinFloatSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceFloatMinCarrierValue");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMinDoubleSnippet = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMin");
        private final Tuple2<Class<? extends ReduceGPUSnippets>, String> partialReduceMinDoubleSnippetCarrierValue = new Tuple2<>(ReduceGPUSnippets.class, "partialReduceDoubleMinCarrierValue");

        Providers providers;

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);
            this.providers = providers;
        }

        private SnippetInfo snippet(Tuple2<Class<? extends ReduceGPUSnippets>, String> tuple2) {
            return snippet(providers, tuple2.t0, tuple2.t1);

        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeInteger(SPIRVIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? snippet(partialReduceIntMaxSnippet) : snippet(partialReduceIntMaxSnippetCarrierValue);
                case MIN:
                    return (extra == null) ? snippet(partialReduceIntMinSnippet) : snippet(partialReduceIntMinSnippetCarrierValue);
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        private SnippetTemplate.SnippetInfo getSnippetFromOCLBinaryNodeLong(SPIRVIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? snippet(partialReduceLongMaxSnippet) : snippet(partialReduceLongMaxSnippetCarrierValue);
                case MIN:
                    return (extra == null) ? snippet(partialReduceLongMinSnippet) : snippet(partialReduceLongMinSnippetCarrierValue);
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceIntSnippet) : snippet(partialReduceIntSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                // operation = ATOMIC_OPERATION.MUL;
                snippet = (extra == null) ? snippet(partialReduceIntMultSnippet) : snippet(partialReduceIntMultSnippetCarrierValue);
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
                snippet = (extra == null) ? snippet(partialReduceLongSnippet) : snippet(partialReduceLongSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceLongMultSnippet) : snippet(partialReduceLongMultSnippetCarrierValue);
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
                    return extra == null ? snippet(partialReduceMaxFloatSnippet) : snippet(partialReduceMaxFloatSnippetCarrierValue);
                case FMIN:
                    return extra == null ? snippet(partialReduceMinFloatSnippet) : snippet(partialReduceMinFloatSnippetCarrierValue);
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddFloatSnippet) : snippet(partialReduceAddFloatSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceFloatMultSnippet) : snippet(partialReduceFloatMultSnippetCarrierValue);
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
                    return extra == null ? snippet(partialReduceMaxDoubleSnippet) : snippet(partialReduceMaxDoubleSnippetCarrierValue);
                case FMIN:
                    return extra == null ? snippet(partialReduceMinDoubleSnippet) : snippet(partialReduceMinDoubleSnippetCarrierValue);
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetTemplate.SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra) {
            SnippetTemplate.SnippetInfo snippet;
            if (value instanceof TornadoReduceAddNode) {
                snippet = (extra == null) ? snippet(partialReduceAddDoubleSnippet) : snippet(partialReduceAddDoubleSnippetCarrierValue);
            } else if (value instanceof TornadoReduceMulNode) {
                snippet = (extra == null) ? snippet(partialReduceDoubleMultSnippet) : snippet(partialReduceDoubleMultSnippetCarrierValue);
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
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, GraphState.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", globalId);
            if (extra != null) {
                args.add("value", extra);
            }

            SnippetTemplate template = template(tool, storeAtomicIndexed, args);
            template.instantiate(tool.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);

        }

        public void lower(WriteAtomicNode writeAtomicNode, ValueNode globalId, GlobalThreadSizeNode globalSize, LoweringTool tool) {

            JavaKind elementKind = writeAtomicNode.getElementKind();
            ValueNode value = writeAtomicNode.value();
            ValueNode extra = writeAtomicNode.getExtraOperation();

            SnippetTemplate.SnippetInfo snippet = getSnippetInstance(elementKind, value, extra);

            // Sets the guard stage to AFTER_FSA because we want to avoid any frame state
            // assignment for the snippet (see SnippetTemplate::assignNecessaryFrameStates)
            // This is needed because we have nodes in the snippet which have multiple side
            // effects and this is not allowed (see
            // SnippetFrameStateAssignment.NodeStateAssignment.INVALID)
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, GraphState.GuardsStage.AFTER_FSA, tool.getLoweringStage());
            args.add("inputData", writeAtomicNode.getInputArray());
            args.add("outputArray", writeAtomicNode.getOutArray());
            args.add("gidx", globalId);
            if (extra != null) {
                args.add("value", extra);
            }

            SnippetTemplate template = template(tool, writeAtomicNode, args);
            template.instantiate(tool.getMetaAccess(), writeAtomicNode, SnippetTemplate.DEFAULT_REPLACER, args);

        }

    }

}
