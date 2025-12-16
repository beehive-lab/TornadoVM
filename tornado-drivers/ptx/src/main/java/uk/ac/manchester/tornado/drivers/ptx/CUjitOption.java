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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

public enum CUjitOption {
    CU_JIT_MAX_REGISTERS(0),
    CU_JIT_OPTIMIZATION_LEVEL(7),
    CU_JIT_TARGET(9),
    CU_JIT_GENERATE_DEBUG_INFO(11),
    CU_JIT_LOG_VERBOSE(12),
    CU_JIT_GENERATE_LINE_INFO(13),
    CU_JIT_CACHE_MODE(14);

    private final int value;

    CUjitOption(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
