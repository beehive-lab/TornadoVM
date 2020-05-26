/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2019 APT Group, School of Computer Science,
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
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
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
 * Tornado-Graal snippets for GPUs reductions using OpenCL semantics.
 */
public class ReduceGPUSnippets implements Snippets {

    private static int LOCAL_WORK_GROUP_SIZE = 223;

    @Snippet
    public static void partialReduceIntAddGlobal(int[] inputArray, int[] outputArray, int gidx) {
        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongAddGlobal(long[] inputArray, long[] outputArray, int gidx) {
        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongAddGlobal2(long[] inputArray, long[] outputArray, int gidx, long value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);

        inputArray[myID] = value;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                inputArray[myID] += inputArray[myID + stride];

            }
        }
        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = inputArray[myID];
        }
    }

    @Snippet
    public static void partialReduceFloatAddGlobal(float[] inputArray, float[] outputArray, int gidx) {
        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

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

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);
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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongMultGlobal(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongMultGlobal2(long[] inputArray, long[] outputArray, int gidx, long value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int myID = localIdx + (localGroupSize * groupID);

        inputArray[myID] = value;
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                inputArray[myID] *= inputArray[myID + stride];
            }
        }

        OpenCLIntrinsics.globalBarrier();
        if (localIdx == 0) {
            outputArray[groupID + 1] = inputArray[myID];
        }
    }

    @Snippet
    public static void partialReduceFloatMultGlobal(float[] inputArray, float[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

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

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceIntMaxGlobal2(int[] inputArray, int[] outputArray, int gidx, int extra) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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
    public static void partialReduceLongMaxGlobal(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongMaxGlobal2(long[] inputArray, long[] outputArray, int gidx, long extra) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;

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

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceFloatMaxGlobal2(float[] inputArray, float[] outputArray, int gidx, float extra) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceDoubleMaxGlobal2(double[] inputArray, double[] outputArray, int gidx, double extra) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceIntMinGlobal2(int[] inputArray, int[] outputArray, int gidx, int extra) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        int[] localArray = (int[]) NewArrayNode.newUninitializedArray(int.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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
    public static void partialReduceLongMinGlobal(long[] inputArray, long[] outputArray, int gidx) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceLongMinGlobal2(long[] inputArray, long[] outputArray, int gidx, long extra) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        long[] localArray = (long[]) NewArrayNode.newUninitializedArray(long.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;

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

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceFloatMinGlobal2(float[] inputArray, float[] outputArray, int gidx, float extra) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        float[] localArray = (float[]) NewArrayNode.newUninitializedArray(float.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

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
    public static void partialReduceDoubleMinGlobal2(double[] inputArray, double[] outputArray, int gidx, double extra) {
        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);
        int groupID = OpenCLIntrinsics.get_group_id(0);

        double[] localArray = (double[]) NewArrayNode.newUninitializedArray(double.class, LOCAL_WORK_GROUP_SIZE);

        localArray[localIdx] = extra;
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
        private final SnippetInfo partialReduceLongSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceLongAddGlobal");
        private final SnippetInfo partialReduceLongSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceLongAddGlobal2");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatAddGlobal");
        private final SnippetInfo partialReduceAddFloatSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatAddGlobal2");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAddGlobal");
        private final SnippetInfo partialReduceAddDoubleSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleAddGlobal2");

        // Mul
        private final SnippetInfo partialReduceIntMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMultGlobal");
        private final SnippetInfo partialReduceIntMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceIntMultGlobal2");
        private final SnippetInfo partialReduceLongMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceLongMultGlobal");
        private final SnippetInfo partialReduceLongMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceLongMultGlobal2");
        private final SnippetInfo partialReduceFloatMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMultGlobal");
        private final SnippetInfo partialReduceFloatMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatMultGlobal2");
        private final SnippetInfo partialReduceDoubleMultSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMultGlobal");
        private final SnippetInfo partialReduceDoubleMultSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMultGlobal2");

        // Max
        private final SnippetInfo partialReduceIntMaxSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMaxGlobal");
        private final SnippetInfo partialReduceIntMaxSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceIntMaxGlobal2");
        private final SnippetInfo partialReduceLongMaxSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceLongMaxGlobal");
        private final SnippetInfo partialReduceLongMaxSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceLongMaxGlobal2");
        private final SnippetInfo partialReduceMaxFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMaxGlobal");
        private final SnippetInfo partialReduceMaxFloatSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatMaxGlobal2");
        private final SnippetInfo partialReduceMaxDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMaxGlobal");
        private final SnippetInfo partialReduceMaxDoubleSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMaxGlobal2");

        // Min
        private final SnippetInfo partialReduceIntMinSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceIntMinGlobal");
        private final SnippetInfo partialReduceIntMinSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceIntMinGlobal2");
        private final SnippetInfo partialReduceLongMinSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceLongMinGlobal");
        private final SnippetInfo partialReduceLongMinSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceLongMinGlobal2");
        private final SnippetInfo partialReduceMinFloatSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceFloatMinGlobal");
        private final SnippetInfo partialReduceMinFloatSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceFloatMinGlobal2");
        private final SnippetInfo partialReduceMinDoubleSnippetGlobal = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMinGlobal");
        private final SnippetInfo partialReduceMinDoubleSnippetGlobal2 = snippet(ReduceGPUSnippets.class, "partialReduceDoubleMinGlobal2");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> debugHandlersFactories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, debugHandlersFactories, providers, snippetReflection, target);
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeInteger(OCLIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceIntMaxSnippetGlobal : partialReduceIntMaxSnippetGlobal2;
                case MIN:
                    return (extra == null) ? partialReduceIntMinSnippetGlobal : partialReduceIntMinSnippetGlobal2;
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeLong(OCLIntBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case MAX:
                    return (extra == null) ? partialReduceLongMaxSnippetGlobal : partialReduceLongMaxSnippetGlobal2;
                case MIN:
                    return (extra == null) ? partialReduceLongMinSnippetGlobal : partialReduceLongMinSnippetGlobal2;
                default:
                    throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
        }

        @Override
        public SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceIntSnippetGlobal : partialReduceIntSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                // operation = ATOMIC_OPERATION.MUL;
                snippet = (extra == null) ? partialReduceIntMultSnippetGlobal : partialReduceIntMultSnippetGlobal2;
            } else if (value instanceof OCLIntBinaryIntrinsicNode) {
                OCLIntBinaryIntrinsicNode op = (OCLIntBinaryIntrinsicNode) value;
                snippet = getSnippetFromOCLBinaryNodeInteger(op, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        @Override
        public SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceLongSnippetGlobal : partialReduceLongSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReduceLongMultSnippetGlobal : partialReduceLongMultSnippetGlobal2;
            } else if (value instanceof OCLIntBinaryIntrinsicNode) {
                OCLIntBinaryIntrinsicNode op = (OCLIntBinaryIntrinsicNode) value;
                snippet = getSnippetFromOCLBinaryNodeLong(op, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeInteger(OCLFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return extra == null ? partialReduceMaxFloatSnippetGlobal : partialReduceMaxFloatSnippetGlobal2;
                case FMIN:
                    return extra == null ? partialReduceMinFloatSnippetGlobal : partialReduceMinFloatSnippetGlobal2;
                default:
                    throw new RuntimeException("OCLFPBinaryIntrinsicNode operation not supported yet");
            }
        }

        @Override
        public SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra) {
            SnippetInfo snippet;
            if (value instanceof OCLReduceAddNode) {
                snippet = (extra == null) ? partialReduceAddFloatSnippetGlobal : partialReduceAddFloatSnippetGlobal2;
            } else if (value instanceof OCLReduceMulNode) {
                snippet = (extra == null) ? partialReduceFloatMultSnippetGlobal : partialReduceFloatMultSnippetGlobal2;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeInteger((OCLFPBinaryIntrinsicNode) value, extra);
            } else {
                throw new RuntimeException("Reduce Operation no supported yet: snippet not installed");
            }
            return snippet;
        }

        private SnippetInfo getSnippetFromOCLBinaryNodeDouble(OCLFPBinaryIntrinsicNode value, ValueNode extra) {
            switch (value.operation()) {
                case FMAX:
                    return extra == null ? partialReduceMaxDoubleSnippetGlobal : partialReduceMaxDoubleSnippetGlobal2;
                case FMIN:
                    return extra == null ? partialReduceMinDoubleSnippetGlobal : partialReduceMinDoubleSnippetGlobal2;
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
                snippet = (extra == null) ? partialReduceDoubleMultSnippetGlobal : partialReduceDoubleMultSnippetGlobal2;
            } else if (value instanceof OCLFPBinaryIntrinsicNode) {
                snippet = getSnippetFromOCLBinaryNodeDouble((OCLFPBinaryIntrinsicNode) value, extra);
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
                args.add("value", extra);
            }

            template(storeAtomicIndexed, args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
