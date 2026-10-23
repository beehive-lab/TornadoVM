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

public enum CUDADeviceType {

    // @formatter:off
    Unknown (-1),
	CL_DEVICE_TYPE_DEFAULT(1 << 0),
	CL_DEVICE_TYPE_CPU(1 << 1),
	CL_DEVICE_TYPE_GPU(1 << 2),
	CL_DEVICE_TYPE_ACCELERATOR(1 << 3),
	CL_DEVICE_TYPE_CUSTOM(1 << 4),
	CL_DEVICE_TYPE_ALL(0xFFFFFFFFL);
    // @formatter:on

    private final long value;

    CUDADeviceType(final long v) {
        value = v;
    }

    public long getValue() {
        return value;
    }

    public static CUDADeviceType toDeviceType(final long v) {
        return switch ((int) v) {
            case 1 -> CUDADeviceType.CL_DEVICE_TYPE_DEFAULT;
            case 1 << 1 -> CUDADeviceType.CL_DEVICE_TYPE_CPU;
            case 1 << 2 -> CUDADeviceType.CL_DEVICE_TYPE_GPU;
            case 1 << 3 -> CUDADeviceType.CL_DEVICE_TYPE_ACCELERATOR;
            case 1 << 4 -> CUDADeviceType.CL_DEVICE_TYPE_CUSTOM;
            case 0xFFFFFFFF -> CUDADeviceType.CL_DEVICE_TYPE_ALL;
            default -> CUDADeviceType.Unknown;
        };
    }
}
