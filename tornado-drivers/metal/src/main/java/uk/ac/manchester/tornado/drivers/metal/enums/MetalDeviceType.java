/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.enums;

public enum MetalDeviceType {

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

    MetalDeviceType(final long v) {
        value = v;
    }

    public long getValue() {
        return value;
    }

    public static MetalDeviceType toDeviceType(final long v) {
        return switch ((int) v) {
            case 1 -> MetalDeviceType.CL_DEVICE_TYPE_DEFAULT;
            case 1 << 1 -> MetalDeviceType.CL_DEVICE_TYPE_CPU;
            case 1 << 2 -> MetalDeviceType.CL_DEVICE_TYPE_GPU;
            case 1 << 3 -> MetalDeviceType.CL_DEVICE_TYPE_ACCELERATOR;
            case 1 << 4 -> MetalDeviceType.CL_DEVICE_TYPE_CUSTOM;
            case 0xFFFFFFFF -> MetalDeviceType.CL_DEVICE_TYPE_ALL;
            default -> MetalDeviceType.Unknown;
        };
    }
}
