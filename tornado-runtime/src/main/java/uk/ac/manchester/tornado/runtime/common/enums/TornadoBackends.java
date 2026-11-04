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

package uk.ac.manchester.tornado.runtime.common.enums;

import uk.ac.manchester.tornado.api.options.CudaFlags;
import uk.ac.manchester.tornado.api.options.MetalFlags;
import uk.ac.manchester.tornado.api.options.OpenCLFlags;

/**
 * Used to prioritize one backend over another. The drivers will be sorted based
 * on their priority. The driver with the highest priority will become driver 0
 * (default driver).
 */

public enum TornadoBackends {

    OpenCL(OpenCLFlags.PRIORITY.get(), "implemented"), //
    Metal(MetalFlags.PRIORITY.get(), "implemented"), //
    CUDA(CudaFlags.PRIORITY.get(), "experimental"); //

    private final int priority;
    private final String status;

    TornadoBackends(int priority, String status) {
        this.priority = priority;
        this.status = status;
    }

    public int value() {
        return priority;
    }

    public String status() {
        return status;
    }
}
