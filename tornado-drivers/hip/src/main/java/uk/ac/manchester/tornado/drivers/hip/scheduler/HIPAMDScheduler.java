/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.hip.scheduler;

import uk.ac.manchester.tornado.drivers.hip.HIPDeviceContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Block sizes for AMD GPUs. A 1-D kernel runs in blocks of {@value #BLOCK_SIZE_1D} threads (fewer
 * when the global size is smaller) over a grid rounded up to whole blocks: the size the reductions
 * allocate their partial results for on AMD platforms. 2-D and 3-D kernels take the sizes of
 * {@link HIPGenericGPUScheduler}, or the occupancy-based ones of {@link HIPKernelScheduler}.
 */
public class HIPAMDScheduler extends HIPGenericGPUScheduler {

    private static final int BLOCK_SIZE_1D = 256;

    public HIPAMDScheduler(final HIPDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        if (meta.getDims() == 1) {
            meta.initLocalWork()[0] = Math.max(1, Math.min(BLOCK_SIZE_1D, meta.getGlobalWork()[0]));
        } else {
            super.calculateLocalWork(meta);
        }
    }
}
