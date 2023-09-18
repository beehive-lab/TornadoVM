/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.Optional;

import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLLoweringProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLBarrierNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OpenCLPrintf;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoOpenCLIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccess;

    public TornadoOpenCLIntrinsicsReplacements(MetaAccessProvider metaAccess) {
        super();
        this.metaAccess = metaAccess;
    }

    private ConstantNode getConstantNodeFromArguments(InvokeNode invoke, int index) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        return (ConstantNode) arguments.get(index);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        NodeIterable<InvokeNode> invokeNodes = graph.getNodes().filter(InvokeNode.class);
        for (InvokeNode invoke : invokeNodes) {
            String methodName = invoke.callTarget().targetName();

            switch (methodName) {
                case "Direct#NewArrayNode.newArray": {
                    lowerInvokeNode(invoke);
                    break;
                }
                case "Direct#OpenCLIntrinsics.localBarrier": {
                    OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#OpenCLIntrinsics.globalBarrier": {
                    OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIDFixedNode localIDNode = graph.addOrUnique(new LocalThreadIDFixedNode(dimension));
                    graph.replaceFixed(invoke, localIDNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSizeNode = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSizeNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadId = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadId);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalSize = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalSize);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.printEmpty":
                    OpenCLPrintf printfNode = graph.addOrUnique(new OpenCLPrintf("\"\""));
                    graph.replaceFixed(invoke, printfNode);
                    break;
            }
        }
    }

    private void lowerLocalInvokeNodeNewArray(StructuredGraph graph, int length, JavaKind elementKind, InvokeNode newArray) {
        LocalArrayNode localArrayNode;
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(OCLArchitecture.localSpace, elementType, newLengthNode));
        newArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateInvokeNodeNewArray(StructuredGraph graph, int size, JavaKind elementKind, InvokeNode newArray) {
        FixedArrayNode fixedArrayNode;
        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(OCLArchitecture.privateSpace, elementType, newLengthNode));
        newArray.replaceAtUsages(fixedArrayNode);
    }

    private void lowerInvokeNode(InvokeNode newArray) {
        CallTargetNode callTarget = newArray.callTarget();
        final StructuredGraph graph = newArray.graph();
        final ValueNode secondInput = callTarget.arguments().get(1);
        if (secondInput instanceof ConstantNode) {
            final ConstantNode lengthNode = (ConstantNode) secondInput;
            if (lengthNode.getValue() instanceof PrimitiveConstant) {
                final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                JavaKind elementKind = getJavaKindFromConstantNode((ConstantNode) callTarget.arguments().get(0));
                final int offset = metaAccess.getArrayBaseOffset(elementKind);
                final int size = offset + (elementKind.getByteCount() * length);
                // This phase should come after OCL lowering, therefore
                // OCLLoweringProvider::gpuSnippet should be set
                if (OCLLoweringProvider.isGPUSnippet()) {
                    lowerLocalInvokeNodeNewArray(graph, length, elementKind, newArray);
                } else {
                    lowerPrivateInvokeNodeNewArray(graph, size, elementKind, newArray);
                }
                newArray.clearInputs();
                GraphUtil.unlinkFixedNode(newArray);
            } else {
                shouldNotReachHere();
            }
        } else {
            unimplemented("dynamically sized array declarations are not supported");
        }
    }

    private JavaKind getJavaKindFromConstantNode(ConstantNode signatureNode) {
        switch (signatureNode.getValue().toValueString()) {
            case "Class:int":
                return JavaKind.Int;
            case "Class:long":
                return JavaKind.Long;
            case "Class:float":
                return JavaKind.Float;
            case "Class:double":
                return JavaKind.Double;
            default:
                unimplemented("Other types not supported yet: " + signatureNode.getValue().toValueString());
        }
        return null;
    }

}
