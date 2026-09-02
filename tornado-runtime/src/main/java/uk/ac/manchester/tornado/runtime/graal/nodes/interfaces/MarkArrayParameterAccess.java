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
package uk.ac.manchester.tornado.runtime.graal.nodes.interfaces;

import tornado.graal.compiler.nodes.PiNode;
import tornado.graal.compiler.nodes.ValueNode;

import uk.ac.manchester.tornado.api.common.Access;

/**
 * Marker for nodes that consume array (kernel-parameter) operands directly and
 * therefore must declare the read/write access of each such operand themselves.
 *
 * <p>{@code TornadoDataflowAnalysis} classifies a parameter's access by inspecting
 * the node types that use it (loads -> read, stores -> write). Intrinsic nodes that
 * lower to backend-specific memory operations (e.g. Metal {@code simdgroup_matrix}
 * load/store) are opaque to that analysis, so they implement this interface to
 * report, per operand, whether they read it, write it, or both.
 */
public interface MarkArrayParameterAccess {

    /**
     * @param parameter
     *     a kernel-parameter operand that flows into this node.
     * @return the access this node performs on {@code parameter}, or
     *     {@link Access#NONE} if {@code parameter} is not one of its array operands.
     */
    Access getArrayParameterAccess(ValueNode parameter);

    /** Strips any {@link PiNode} wrappers to reach the underlying value node. */
    static ValueNode unwrapPi(ValueNode v) {
        while (v instanceof PiNode pi) {
            v = pi.object();
        }
        return v;
    }
}
