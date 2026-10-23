/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.enums;

public enum MetalProgramInfo {

    METAL_PROGRAM_REFERENCE_COUNT(0x1160), METAL_PROGRAM_CONTEXT(0x1161), METAL_PROGRAM_NUM_DEVICES(0x1162), METAL_PROGRAM_DEVICES(0x1163), METAL_PROGRAM_SOURCE(0x1164), METAL_PROGRAM_BINARY_SIZES(
            0x1165), METAL_PROGRAM_BINARIES(0x1166), METAL_PROGRAM_NUM_KERNELS(0x1167), METAL_PROGRAM_KERNEL_NAMES(0x1168);

    private final int value;

    MetalProgramInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
