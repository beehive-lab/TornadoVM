/*
 * Copyright (c) 2018, 2020, 2024 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.common.compiler.phases.guards;

import java.util.Optional;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoValueTypeCleanup extends BasePhase<TornadoHighTierContext> {
    private static final NodePredicate valueTypeFilter = new NodePredicate() {
        @Override
        public boolean apply(Node node) {
            return ((VirtualInstanceNode) node).hasNoUsages();
        }
    };

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(NewInstanceNode.class).filter(valueTypeFilter).forEach(GraphUtil::tryKillUnused);
    }
}
