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
package uk.ac.manchester.tornado.drivers.opencl.enums;

public enum OCLProfilingInfo {

    // @formatter:off
	CL_PROFILING_COMMAND_QUEUED(0x1280),
	CL_PROFILING_COMMAND_SUBMIT(0x1281),
	CL_PROFILING_COMMAND_START(0x1282),
	CL_PROFILING_COMMAND_END(0x1283),
    CL_PROFILING_COMMAND_COMPLETE(0x1284);
    // @formatter:on

    private final long value;

    OCLProfilingInfo(final long v) {
        value = v;
    }

    public long getValue() {
        return value;
    }
}
