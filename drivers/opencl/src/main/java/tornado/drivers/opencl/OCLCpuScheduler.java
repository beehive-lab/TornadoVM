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

import static tornado.common.Tornado.USE_THREAD_COARSENING;
import static tornado.common.Tornado.getProperty;

public class OCLCpuScheduler extends OCLKernelScheduler {

    private final double CPU_COMPUTE_UNIT_COEFF = Double.parseDouble(getProperty("tornado.opencl.cpu.coeff", "1.0"));

    public OCLCpuScheduler(final OCLDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateGlobalWork(final OCLKernelConfig kernelInfo) {
        long[] maxItems = deviceContext.getDevice().getMaxWorkItemSizes();

        final long[] globalWork = kernelInfo.getGlobalWork();
        for (int i = 0; i < kernelInfo.getDims(); i++) {
            if (USE_THREAD_COARSENING) {
                globalWork[i] = maxItems[i] > 1 ? (long) (kernelInfo.getDomain().get(i).cardinality()) : 1;
            } else {
                globalWork[i] = i == 0 ? (long) (deviceContext.getDevice().getMaxComputeUnits() * CPU_COMPUTE_UNIT_COEFF) : 1;
            }
        }
    }

    @Override
    public void calculateLocalWork(OCLKernelConfig kernelInfo) {
        final long[] globalWork = kernelInfo.getGlobalWork();
        final long[] localWork = kernelInfo.getLocalWork();

        for (int i = 0; i < globalWork.length; i++) {
            localWork[i] = 1;
        }
    }

}
