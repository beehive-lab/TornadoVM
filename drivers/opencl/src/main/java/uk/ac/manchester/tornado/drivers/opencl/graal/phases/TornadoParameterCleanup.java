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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementProxyNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

@Deprecated
public class TornadoParameterCleanup extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        for (int i = 0; i < graph.getNodes().filter(ParameterNode.class).count(); i++) {
            final ParameterNode param = graph.getParameter(i);

            if (param == null || !param.getStackKind().isObject()) {
                continue;
            }

            final Stamp stamp = param.stamp();
//					System.out.printf("node: node=%s, stamp=%s, type=%s\n",param, stamp,stamp.javaType(context.getMetaAccess()));
            final ResolvedJavaType type = stamp.javaType(context.getMetaAccess());
            final OCLKind vectorKind = OCLKind.fromResolvedJavaType(type);
            if (vectorKind != OCLKind.ILLEGAL) {
                if (param.usages().filter(VectorValueNode.class).isEmpty()) {
//							System.out.printf("inserting vector value...\n");

                    final VectorValueNode vector = graph.addOrUnique(new VectorValueNode(vectorKind, param));
                    if (context.isKernel()) {
                        vector.setNeedsLoad();
                    }

                    param.usages().filter(VectorLoadElementProxyNode.class).forEach(loadProxy -> {
                        loadProxy.setOrigin(vector);
                        if (loadProxy.canResolve()) {
                            loadProxy.replaceAndDelete(loadProxy.tryResolve());
                        }
                    });

                    param.usages().filter(MethodCallTargetNode.class).forEach(methodCall -> {
                        param.replaceAtMatchingUsages(vector, usage -> usage == methodCall);
                    });

//                    param.usages().filter(GuardingPiNode.class).forEach(guard -> {
//                        guard.replaceAtUsages(vector);
//                        GraphUtil.tryKillUnused(guard);
//                    });
                }
            }

        }
    }

}
