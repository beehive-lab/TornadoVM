/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.graal.phases.TornadoHighTierContext;

@Deprecated
public class TornadoInvokeCleanup extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(InvokeWithExceptionNode.class).forEach(invoke -> {
            //System.out.printf("cleaning: %s\n",invoke);
//            final List<GuardingPiNode> guardingPis = invoke.usages().filter(GuardingPiNode.class).snapshot();
//
//            if (invoke.exceptionEdge() != null) {
//                invoke.killExceptionEdge();
//            }
//
//            AbstractBeginNode begin = invoke.next();
//            if (begin instanceof KillingBeginNode) {
//                AbstractBeginNode newBegin = new BeginNode();
//                graph.addAfterFixed(begin, graph.add(newBegin));
//                begin.replaceAtUsages(newBegin);
//                graph.removeFixed(begin);
//            }
        });
    }

}
