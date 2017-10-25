/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.drivers.opencl;

import tornado.api.meta.TaskMetaData;

import static tornado.common.Tornado.getProperty;

public class OCLCpuScheduler extends OCLKernelScheduler {

    private final double CPU_COMPUTE_UNIT_COEFF = Double.parseDouble(getProperty("tornado.opencl.cpu.coeff", "1.0"));

    public OCLCpuScheduler(final OCLDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateGlobalWork(final TaskMetaData meta) {
        long[] maxItems = deviceContext.getDevice().getMaxWorkItemSizes();

        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            if (meta.enableThreadCoarsener()) {
                globalWork[i] = maxItems[i] > 1 ? (long) (meta.getDomain().get(i).cardinality()) : 1;
            } else {
                globalWork[i] = i == 0 ? (long) (deviceContext.getDevice().getMaxComputeUnits() * CPU_COMPUTE_UNIT_COEFF) : 1;
            }
        }
    }

    @Override
    public void calculateLocalWork(final TaskMetaData meta) {
        final long[] globalWork = meta.getGlobalWork();
        final long[] localWork = meta.getLocalWork();

        for (int i = 0; i < globalWork.length; i++) {
            localWork[i] = 1;
        }
    }

}
