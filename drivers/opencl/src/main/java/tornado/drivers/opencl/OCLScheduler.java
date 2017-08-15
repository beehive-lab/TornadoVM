/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl;

import static tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static tornado.common.Tornado.fatal;
import static tornado.drivers.opencl.OpenCL.ACCELERATOR_IS_GPU;

public class OCLScheduler {

    public static final OCLKernelScheduler create(final OCLDeviceContext context) {

        if (FORCE_ALL_TO_GPU) {
            return new OCLGpuScheduler(context);
        }

        if (null != context.getDevice().getDeviceType()) {
            switch (context.getDevice().getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return new OCLGpuScheduler(context);
                case CL_DEVICE_TYPE_CPU:
                    return (ACCELERATOR_IS_GPU)
                            ? new OCLGpuScheduler(context)
                            : new OCLCpuScheduler(context);
                case CL_DEVICE_TYPE_ACCELERATOR:
                    return new OCLCpuScheduler(context);
                default:
                    fatal("No scheduler available for device: %s", context);
                    break;
            }
        }

        return null;
    }
}
