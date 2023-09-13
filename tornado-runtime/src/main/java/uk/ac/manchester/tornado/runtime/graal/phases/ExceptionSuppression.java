/*
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.phases.BasePhase;

public class ExceptionSuppression extends BasePhase<TornadoHighTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        Set<LogicNode> conditions = new HashSet<>();
        Set<GuardNode> guards = new HashSet<>();

        graph.getNodes().filter(n -> n instanceof GuardedNode).forEach((node) -> {
            GuardedNode guardedNode = (GuardedNode) node;
            if (guardedNode.getGuard() instanceof GuardNode) {
                GuardNode guard = (GuardNode) guardedNode.getGuard();
                LogicNode condition = guard.getCondition();

                conditions.add(condition);
                guards.add(guard);
                guardedNode.setGuard(null);

            }
        });

        graph.getNodes().filter(ValueAnchorNode.class).forEach(anchor -> {
            if (anchor.getAnchoredNode() instanceof GuardNode) {
                final GuardNode guard = (GuardNode) anchor.getAnchoredNode();
                guards.add(guard);
                conditions.add(guard.getCondition());
                anchor.removeAnchoredNode();
            }
        });

        guards.forEach(guard -> {
            guard.clearInputs();
            guard.safeDelete();
        });

        conditions.forEach(condition -> {
            condition.clearInputs();
            condition.safeDelete();
        });
    }

}
