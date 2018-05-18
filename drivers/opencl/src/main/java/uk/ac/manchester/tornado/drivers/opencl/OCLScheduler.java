/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static uk.ac.manchester.tornado.common.Tornado.fatal;
import static uk.ac.manchester.tornado.drivers.opencl.OpenCL.ACCELERATOR_IS_GPU;

public class OCLScheduler {

    public static final OCLKernelScheduler create(final OCLDeviceContext context) {

        if (FORCE_ALL_TO_GPU) {
            return new OCLGPUScheduler(context);
        }

        if (null != context.getDevice().getDeviceType()) {
            switch (context.getDevice().getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return new OCLGPUScheduler(context);
                case CL_DEVICE_TYPE_ACCELERATOR:
                    return (ACCELERATOR_IS_GPU)
                            ? new OCLGPUScheduler(context)
                            : new OCLCPUScheduler(context);
                case CL_DEVICE_TYPE_CPU:
                    return new OCLCPUScheduler(context);
                default:
                    fatal("No scheduler available for device: %s", context);
                    break;
            }
        }

        return null;
    }
}
