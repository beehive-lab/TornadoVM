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

public enum CUDAProgramInfo {

    CL_PROGRAM_REFERENCE_COUNT(0x1160), CL_PROGRAM_CONTEXT(0x1161), CL_PROGRAM_NUM_DEVICES(0x1162), CL_PROGRAM_DEVICES(0x1163), CL_PROGRAM_SOURCE(0x1164), CL_PROGRAM_BINARY_SIZES(
            0x1165), CL_PROGRAM_BINARIES(0x1166), CL_PROGRAM_NUM_KERNELS(0x1167), CL_PROGRAM_KERNEL_NAMES(0x1168);

    private final int value;

    CUDAProgramInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
