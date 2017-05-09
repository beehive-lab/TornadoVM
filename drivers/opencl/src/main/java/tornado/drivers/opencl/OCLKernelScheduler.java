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

import tornado.api.meta.TaskMetaData;

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

    public abstract void calculateGlobalWork(final TaskMetaData meta);

    public abstract void calculateLocalWork(final TaskMetaData meta);

    public void adjust() {

    }

    public int submit(final OCLKernel kernel, final TaskMetaData meta, final int[] waitEvents) {

        calculateGlobalWork(meta);
        calculateLocalWork(meta);

        if (meta.isDebug()) {
            meta.printThreadDims();
        }

        final int task;
        if (meta.shouldUseOpenclScheduling()) {
            task = deviceContext.enqueueNDRangeKernel(kernel, meta.getDims(), meta.getGlobalOffset(),
                    meta.getGlobalWork(), null, waitEvents);
        } else {

            task = deviceContext.enqueueNDRangeKernel(kernel, meta.getDims(), meta.getGlobalOffset(),
                    meta.getGlobalWork(), meta.getLocalWork(), waitEvents);

        }
        return task;
    }

}
