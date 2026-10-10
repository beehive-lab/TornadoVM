/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
}
