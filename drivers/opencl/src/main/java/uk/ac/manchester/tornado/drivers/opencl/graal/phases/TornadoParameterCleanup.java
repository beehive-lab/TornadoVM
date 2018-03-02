/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import uk.ac.manchester.tornado.graal.phases.TornadoHighTierContext;

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
