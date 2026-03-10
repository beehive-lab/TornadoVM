/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.enums;

/**
 * Metal Memory Flags for Metal 1.2 and Intel FPGA extensions.
 *
 * Link: https://github.com/KhronosGroup/Metal-Headers/blob/master/CL/cl.h
 *
 */
public class  MetalMemFlags {

    // @formatter:off
    public static final long METAL_MEM_READ_WRITE      = (1 << 0);
    public static final long METAL_MEM_WRITE_ONLY      = (1 << 1);
    public static final long METAL_MEM_READ_ONLY       = (1 << 2);
    public static final long METAL_MEM_USE_HOST_PTR    = (1 << 3);
    public static final long METAL_MEM_ALLOC_HOST_PTR  = (1 << 4);
    public static final long METAL_MEM_COPY_HOST_PTR   = (1 << 5);

    // reserved (1 << 6)
    public static final long METAL_MEM_HOST_WRITE_ONLY = (1 << 7);
    public static final long METAL_MEM_HOST_READ_ONLY  = (1 << 8);
    public static final long METAL_MEM_HOST_NO_ACCESS  = (1 << 9);
    // @formatter:on

}
