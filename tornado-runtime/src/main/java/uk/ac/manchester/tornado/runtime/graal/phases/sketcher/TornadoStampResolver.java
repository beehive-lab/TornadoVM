/*
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import java.util.Optional;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoStampResolver extends BasePhase<TornadoSketchTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {

        graph.getNodes().filter(PhiNode.class).forEach((PhiNode phi) -> {
            Stamp stamp = phi.stamp(NodeView.DEFAULT);
            if (stamp.isEmpty()) {
                for (ValueNode n : phi.values()) {
                    if (stamp.isEmpty()) {
                        stamp = n.stamp(NodeView.DEFAULT);
                    } else {
                        stamp = stamp.meet(n.stamp(NodeView.DEFAULT));
                    }
                }
                phi.setStamp(stamp);
            }
        });
    }

}
