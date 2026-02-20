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

/**
 * Metal Memory Flags for Metal 1.2 and Intel FPGA extensions.
 *
 * Link: https://github.com/KhronosGroup/Metal-Headers/blob/master/CL/cl.h
 *
 */
public class MetalMemFlags {

    // @formatter:off
    public static final long CL_MEM_READ_WRITE      = (1 << 0);
    public static final long CL_MEM_WRITE_ONLY      = (1 << 1);
    public static final long CL_MEM_READ_ONLY       = (1 << 2);
    public static final long CL_MEM_USE_HOST_PTR    = (1 << 3);
    public static final long CL_MEM_ALLOC_HOST_PTR  = (1 << 4);
    public static final long CL_MEM_COPY_HOST_PTR   = (1 << 5);

    // reserved (1 << 6)
    public static final long CL_MEM_HOST_WRITE_ONLY = (1 << 7);
    public static final long CL_MEM_HOST_READ_ONLY  = (1 << 8);
    public static final long CL_MEM_HOST_NO_ACCESS  = (1 << 9);

    // Intel Altera FPGAs Memory Banks
    public static final long CL_CHANNEL_1_INTELFPGA = (1 << 16);
    public static final long CL_CHANNEL_2_INTELFPGA = (2 << 16);
    public static final long CL_CHANNEL_3_INTELFPGA = (3 << 16);
    public static final long CL_CHANNEL_4_INTELFPGA = (4 << 16);
    public static final long CL_CHANNEL_5_INTELFPGA = (5 << 16);
    public static final long CL_CHANNEL_6_INTELFPGA = (6 << 16);
    public static final long CL_CHANNEL_7_INTELFPGA = (7 << 16);
    public static final long CL_MEM_HETEROGENEOUS_INTELFPGA = (1 << 19);
    // @formatter:on

}
