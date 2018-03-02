/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            return new OCLGpuScheduler(context);
        }

        if (null != context.getDevice().getDeviceType()) {
            switch (context.getDevice().getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return new OCLGpuScheduler(context);
                case CL_DEVICE_TYPE_ACCELERATOR:
                    return (ACCELERATOR_IS_GPU)
                            ? new OCLGpuScheduler(context)
                            : new OCLCpuScheduler(context);
                case CL_DEVICE_TYPE_CPU:
                    return new OCLCpuScheduler(context);
                default:
                    fatal("No scheduler available for device: %s", context);
                    break;
            }
        }

        return null;
    }
}
