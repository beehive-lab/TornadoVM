/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.library.spi;

/**
 * Capability interface implemented by backend devices that can expose their
 * raw native command stream to external libraries (e.g., the CUDA backend
 * exposes the CUstream so cuBLAS can be bound to it via
 * {@code cublasSetStream}). Library providers query the interpreter device for
 * this interface to enqueue native library work in order with TornadoVM's own
 * kernels and transfers.
 */
public interface TornadoNativeStreamSupport {

    /**
     * Raw native stream handle (e.g., CUstream) used by the given execution
     * plan on this device.
     */
    long getNativeStream(long executionPlanId);

    /**
     * Raw native context handle (e.g., CUcontext) used by the given execution
     * plan on this device.
     */
    long getNativeContext(long executionPlanId);
}
