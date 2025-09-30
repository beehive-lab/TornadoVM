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

public enum MetalKernelInfo {

    CL_KERNEL_FUNCTION_NAME(0x1190), CL_KERNEL_NUM_ARGS(0x1191), CL_KERNEL_REFERENCE_COUNT(0x1192), CL_KERNEL_CONTEXT(0x1193), CL_KERNEL_PROGRAM(0x1194), CL_KERNEL_ATTRIBUTES(0x1195);

    private final int value;

    MetalKernelInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
