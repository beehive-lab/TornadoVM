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
package uk.ac.manchester.tornado.drivers.cuda.graal.backend;

/**
 * CUDA C preamble emitted once before every compiled kernel.
 *
 * <p>The codegen layer is cloned from the OpenCL C backend, so it emits
 * OpenCL-style scalar type names ({@code uchar}, {@code uint}, {@code ulong},
 * {@code ushort}). CUDA C / NVRTC does not define these as built-in types, so
 * this preamble provides the matching {@code typedef}s. In particular the
 * device-buffer kernel parameters are generated as {@code uchar *}, which would
 * not compile under NVRTC without these aliases.
 *
 * <p>Kept intentionally minimal for the MVP scalar-kernel path. Vector types,
 * FP16/half shims, atomics, and OpenCL math built-ins are deferred.
 */
public final class CUDAPreamble {

    private CUDAPreamble() {
    }

    // @formatter:off
    public static final String PREAMBLE =
        "typedef unsigned char uchar;\n" +
        "typedef unsigned short ushort;\n" +
        "typedef unsigned int uint;\n" +
        "typedef unsigned long ulong;\n";
    // @formatter:on
}
