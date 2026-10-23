/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.enums;

public enum CUDAEventInfo {
    CL_EVENT_COMMAND_QUEUE(0x11D0), CL_EVENT_COMMAND_TYPE(0x11D1), CL_EVENT_REFERENCE_COUNT(0x11D2), CL_EVENT_COMMAND_EXECUTION_STATUS(0x11D3), CL_EVENT_CONTEXT(0x11D4);

    private final int value;

    CUDAEventInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
