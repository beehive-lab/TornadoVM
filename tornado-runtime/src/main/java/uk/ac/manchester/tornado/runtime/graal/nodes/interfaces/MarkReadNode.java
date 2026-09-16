/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.runtime.graal.nodes.interfaces;

/**
 * Backend-neutral marker for a node that reads from an array parameter, so that
 * the backend-independent sketch-tier phases ({@code TornadoDataflowAnalysis},
 * {@code TornadoFeatureExtraction}) can recognise a read without depending on
 * any driver package. The counterpart of {@link MarkWriteNode}.
 *
 * <p>
 * A read node that is not recognised is not a missed optimisation, it is a wrong
 * answer: the parameter is classified {@code WRITE_ONLY}, the runtime then skips
 * the host-to-device copy for it, and the kernel reads an uninitialised device
 * buffer. Any backend read node that consumes an array parameter directly, rather
 * than through {@code ReadNode}/{@code JavaReadNode}, must implement this.
 * </p>
 */
public interface MarkReadNode {
}
