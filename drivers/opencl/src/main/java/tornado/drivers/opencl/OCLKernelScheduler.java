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

import static tornado.common.Tornado.*;
import tornado.meta.Meta;

public abstract class OCLKernelScheduler {

    protected final OCLDeviceContext deviceContext;

    protected double min;
    protected double max;
    protected double sum;
    protected double mean;
    protected double std;
    protected double samples;

    public OCLKernelScheduler(final OCLDeviceContext context) {
        deviceContext = context;
    }

    public void calcStats(int window) {

    }

    public abstract void calculateGlobalWork(final OCLKernelConfig kernelInfo);

    public abstract void calculateLocalWork(final OCLKernelConfig kernelInfo);

    public void adjust() {

    }

    public int submit(final OCLKernel kernel, final Meta meta, final int[] waitEvents) {
        final OCLKernelConfig kernelInfo;
        if (meta.hasProvider(OCLKernelConfig.class)) {
            kernelInfo = meta.getProvider(OCLKernelConfig.class);
        } else {
            kernelInfo = OCLKernelConfig.create(meta);
            calculateGlobalWork(kernelInfo);
            calculateLocalWork(kernelInfo);
        }

        if (DEBUG) {
            kernelInfo.printToLog();
        }

        final int task;
        if (USE_OPENCL_SCHEDULING) {
            task = deviceContext.enqueueNDRangeKernel(kernel, kernelInfo.getDims(), kernelInfo.getGlobalOffset(),
                    kernelInfo.getGlobalWork(), null, waitEvents);
        } else {

            task = deviceContext.enqueueNDRangeKernel(kernel, kernelInfo.getDims(), kernelInfo.getGlobalOffset(),
                    kernelInfo.getGlobalWork(), kernelInfo.getLocalWork(), waitEvents);

        }
        return task;
    }

}
