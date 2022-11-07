/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.drivers.ptx.enums;

/**
 * Contains a subset of the device properties that can be queried. See @link{
 * https://docs.nvidia.com/cuda/cuda-driver-api/group__CUDA__TYPES.html } for
 * the full list.
 */
public enum PTXDeviceAttribute {

    MAX_THREADS_PER_BLOCK(1), //
    MAX_BLOCK_DIM_X(2), //
    MAX_BLOCK_DIM_Y(3), //
    MAX_BLOCK_DIM_Z(4), //
    MAX_GRID_DIM_X(5), //
    MAX_GRID_DIM_Y(6), //
    MAX_GRID_DIM_Z(7), //
    MAX_SHARED_MEMORY_PER_BLOCK(8), //
    TOTAL_CONSTANT_MEMORY(9), //
    WARP_SIZE(10), //
    MAX_REGISTERS_PER_BLOCK(12), //
    CLOCK_RATE(13), //
    MULTIPROCESSOR_COUNT(16), //
    COMPUTE_CAPABILITY_MAJOR(75), //
    COMPUTE_CAPABILITY_MINOR(76); //

    private final int value;

    PTXDeviceAttribute(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
