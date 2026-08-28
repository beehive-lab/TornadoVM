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
package uk.ac.manchester.tornado.drivers.cuda;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;

/**
 * The outcome of running the Graal front end for one task: the generated CUDA-C (or SPIR-V)
 * source plus the identifiers needed to install it.
 *
 * <p>
 * Installed code ({@link uk.ac.manchester.tornado.drivers.cuda.graal.CUDAInstalledCode}) is
 * cached per execution plan, because plan teardown invalidates the underlying module. The
 * source produced by the front end has no such affinity, so it is cached once per device and
 * reused by every later plan. Deliberately holds no {@code TaskDataContext}: the metadata of
 * the task being compiled now is supplied at install time, so a cached entry never pins the
 * metadata of the task that first produced it.
 * </p>
 *
 * <p>
 * {@code domain} is the parallel iteration space that shape analysis derived during that
 * compilation. It is carried here because shape analysis only runs as part of the front end
 * (it is skipped once a task's domain is set), so a task served from this cache would otherwise
 * be launched with no domain at all.
 * </p>
 */
public record CUDACompiledKernel(String id, String entryPoint, byte[] targetCode, ResolvedJavaMethod[] methods, DomainTree domain) {
}
