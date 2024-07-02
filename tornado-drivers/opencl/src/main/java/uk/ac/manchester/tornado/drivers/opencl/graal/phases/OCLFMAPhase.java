/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Optional;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.phases.Phase;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFMANode;

public class OCLFMAPhase extends Phase {

    /**
     * Instrinsics in OpenCL:
     * https://www.khronos.org/registry/OpenCL/sdk/1.0/docs/man/xhtml/fma.html
     * 
     * 
     * From the OpenCL documentation:
     * 
     * The generic type name gentype is used to indicate that the function can take
     * float, float2, float4, float8, or float16 as the type for the arguments. For
     * any specific use of these function, the actual type has to be the same for
     * all arguments and the return type.
     *
     * If extended with cl_khr_fp64, generic type name gentype may indicate double
     * and double{2|4|8|16} as arguments and return values. If extended with
     * cl_khr_fp16, generic type name gentype may indicate half and half{2|4|8|16}
     * as arguments and return values.
     * 
     * @param x
     *            valueNode
     * @return returns true if ValueNode is either Float of Double
     */
    private boolean isValidType(ValueNode x) {
        return (x.getStackKind() == JavaKind.Float || x.getStackKind() == JavaKind.Double);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {

        graph.getNodes().filter(AddNode.class).forEach(addNode -> {
            MulNode mulNode = null;
            if (addNode.getX() instanceof MulNode) {
                mulNode = (MulNode) addNode.getX();
            } else if (addNode.getY() instanceof MulNode) {
                mulNode = (MulNode) addNode.getY();
            }
            if (mulNode != null) {
                ValueNode x = mulNode.getX();
                ValueNode y = mulNode.getY();
                if (isValidType(x) && isValidType(y)) {
                    MulNode finalMulNode = mulNode;
                    ValueNode z = (ValueNode) addNode.inputs().filter(node -> !node.equals(finalMulNode)).first();
                    OCLFMANode oclFMA = new OCLFMANode(x, y, z);
                    graph.addOrUnique(oclFMA);
                    mulNode.removeUsage(addNode);
                    if (mulNode.hasNoUsages()) {
                        mulNode.safeDelete();
                    }
                    addNode.replaceAtUsages(oclFMA);
                    addNode.safeDelete();
                }
            }
        });

    }
}
