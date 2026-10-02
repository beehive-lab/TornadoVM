/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.graal.phases;

import java.util.Optional;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.AbstractBeginNode;
import tornado.graal.compiler.core.common.type.ObjectStamp;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ParameterNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.extended.NullCheckNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

/**
 * Removes null checks on kernel parameters that cannot be null.
 *
 * <p>
 * The {@code KernelContext} intrinsics check their receiver for null, and array accesses check the
 * array. In a kernel the host launches, those checks fold away because the arguments are known
 * objects. A kernel launched from the device ({@code KernelContext.launch}) is compiled without its
 * arguments, so the checks would survive, and code generation has no lowering for them. Neither can
 * fail: the {@code KernelContext} stands for the implicit kernel context, and every other parameter of
 * a device-launched kernel is a buffer its parent passed on.
 * </p>
 */
public class CUDAKernelContextNullCheckElimination extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        ResolvedJavaMethod method = graph.method();
        if (method == null) {
            return;
        }
        // Compiled as a kernel but without arguments: launched from the device.
        boolean deviceLaunched = context.isKernel() && context.getArgs() == null;
        if (deviceLaunched) {
            // Mark the parameters non-null, so that lowering does not add null checks for them later.
            for (ParameterNode parameter : graph.getNodes(ParameterNode.TYPE)) {
                if (parameter.stamp(NodeView.DEFAULT) instanceof ObjectStamp stamp && !stamp.nonNull()) {
                    parameter.setStamp(stamp.asNonNull());
                    parameter.inferStamp();
                }
            }
        }
        for (NullCheckNode check : graph.getNodes().filter(NullCheckNode.class).snapshot()) {
            if (!checksKernelContext(check, method) && !(deviceLaunched && checksParameter(check))) {
                continue;
            }
            // Anything the check guarded is guarded by the enclosing block instead.
            check.replaceAtUsages(AbstractBeginNode.prevBegin(check));
            graph.removeFixed(check);
        }
    }

    private static boolean checksParameter(NullCheckNode check) {
        for (Node input : check.inputs()) {
            if (input instanceof ValueNode value && GraphUtil.unproxify(value) instanceof ParameterNode) {
                return true;
            }
        }
        return false;
    }

    private static boolean checksKernelContext(NullCheckNode check, ResolvedJavaMethod method) {
        for (Node input : check.inputs()) {
            if (input instanceof ValueNode value && GraphUtil.unproxify(value) instanceof ParameterNode parameter) {
                int index = parameter.index() - (method.isStatic() ? 0 : 1);
                if (index >= 0 && index < method.getSignature().getParameterCount(false)
                        && method.getSignature().getParameterType(index, method.getDeclaringClass()).toJavaName().equals(KernelContext.class.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
