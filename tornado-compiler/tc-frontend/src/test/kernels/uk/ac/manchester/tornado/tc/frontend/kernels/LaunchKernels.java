/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.frontend.kernels;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/** KernelContext.launch: ping and pong launch each other, so the launch graph has a cycle. */
public final class LaunchKernels {

    static final DeviceKernel PING = DeviceKernel.of(LaunchKernels::ping);
    static final DeviceKernel PONG = DeviceKernel.of(LaunchKernels::pong);
    static final DeviceKernel SCALE = DeviceKernel.of(LaunchKernels::scale);

    private LaunchKernels() {
    }

    public static void parent(KernelContext context, IntArray trace, int depth) {
        if (context.globalIdx == 0) {
            context.launch(PING, DeviceLaunchMode.TAIL, 1, 1, trace, depth);
        }
    }

    static void ping(KernelContext context, IntArray trace, int depth) {
        trace.set(depth, 1);
        if (depth > 0) {
            context.launch(PONG, 1, 1, trace, depth - 1);
        }
    }

    static void pong(KernelContext context, IntArray trace, int depth) {
        trace.set(depth, 2);
        if (depth > 0) {
            context.launch(PING, 1, 1, trace, depth - 1);
        }
    }

    /** Launches SCALE from an inlined helper, and also calls scale directly (an ordinary call). */
    public static void dispatch(KernelContext context, FloatArray data, int n) {
        data.set(context.globalIdx, data.get(context.globalIdx) + 1.0f);
        if (context.globalIdx == 0) {
            launchScale(context, data, n);
        }
        scale(context, data, n);
    }

    private static void launchScale(KernelContext context, FloatArray data, int n) {
        context.launch(SCALE, n, 32, data, n);
    }

    static void scale(KernelContext context, FloatArray data, int n) {
        int i = context.globalIdx;
        if (i < n) {
            data.set(i, data.get(i) * 3.0f);
        }
    }

    /** A launch argument chosen by a ternary: javac carries the varargs array across the branch. */
    public static void chosenArgument(KernelContext context, FloatArray data, int n, int wide) {
        if (context.globalIdx == 0) {
            context.launch(SCALE, n, 32, data, wide > 0 ? n : 1);
        }
    }
}
