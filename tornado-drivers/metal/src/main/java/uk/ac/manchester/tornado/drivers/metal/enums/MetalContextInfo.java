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

public enum MetalContextInfo {
    METAL_CONTEXT_REFERENCE_COUNT(0x1080), METAL_CONTEXT_DEVICES(0x1081), METAL_CONTEXT_PROPERTIES(0x1082), METAL_CONTEXT_NUM_DEVICES(0x1083);

    private final int value;

    MetalContextInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
