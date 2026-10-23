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

public enum MetalDeviceType {

    // @formatter:off
    Unknown (-1),
	METAL_DEVICE_TYPE_DEFAULT(1 << 0),
	METAL_DEVICE_TYPE_CPU(1 << 1),
	METAL_DEVICE_TYPE_GPU(1 << 2),
	METAL_DEVICE_TYPE_ACCELERATOR(1 << 3),
	METAL_DEVICE_TYPE_CUSTOM(1 << 4),
	METAL_DEVICE_TYPE_ALL(0xFFFFFFFFL);
    // @formatter:on

    private final long value;

    MetalDeviceType(final long v) {
        value = v;
    }

    public long getValue() {
        return value;
    }

    public static MetalDeviceType toDeviceType(final long v) {
        return switch ((int) v) {
            case 1 -> MetalDeviceType.METAL_DEVICE_TYPE_DEFAULT;
            case 1 << 1 -> MetalDeviceType.METAL_DEVICE_TYPE_CPU;
            case 1 << 2 -> MetalDeviceType.METAL_DEVICE_TYPE_GPU;
            case 1 << 3 -> MetalDeviceType.METAL_DEVICE_TYPE_ACCELERATOR;
            case 1 << 4 -> MetalDeviceType.METAL_DEVICE_TYPE_CUSTOM;
            case 0xFFFFFFFF -> MetalDeviceType.METAL_DEVICE_TYPE_ALL;
            default -> MetalDeviceType.Unknown;
        };
    }
}
