/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.Optional;

import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLoweringProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVBarrierNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoSPIRVIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccessProvider;

    public TornadoSPIRVIntrinsicsReplacements(MetaAccessProvider metaAccessProvider) {
        this.metaAccessProvider = metaAccessProvider;
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
                case "Direct#SPIRVOCLIntrinsics.localBarrier": {
                    SPIRVBarrierNode barrierNode = graph.addOrUnique(new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, barrierNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.globalBarrier": {
                    SPIRVBarrierNode barrierNode = graph.addOrUnique(new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, barrierNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIdFixedNode localIdNode = graph.addOrUnique(new LocalThreadIdFixedNode(dimension));
                    graph.replaceFixed(invoke, localIdNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSize = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSize);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadIdNode = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadIdNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalThreadSizeNode = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalThreadSizeNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#SPIRVOCLIntrinsics.printEmpty": {
                    throw new TornadoRuntimeException("Unimplemented");
                }
            }
        }
    }

    private void lowerLocalInvokeNodeNewArray(StructuredGraph graph, int length, JavaKind elementKind, InvokeNode invokeWithNewArray) {
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        ResolvedJavaType elementType = metaAccessProvider.lookupJavaType(elementKind.toJavaClass());
        LocalArrayNode localArrayNode = graph.addOrUnique(new LocalArrayNode(SPIRVArchitecture.localSpace, elementType, newLengthNode));
        invokeWithNewArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateInvokeNodeNewArray(StructuredGraph graph, int size, JavaKind elementKind, InvokeNode invokeWithNewArray) {
        final ConstantNode newLenghNode = ConstantNode.forInt(size, graph);
        ResolvedJavaType elementType = metaAccessProvider.lookupJavaType(elementKind.toJavaClass());
        FixedArrayNode fixedArrayNode = graph.addOrUnique(new FixedArrayNode(SPIRVArchitecture.kernelContextSpace, elementType, newLenghNode));
        invokeWithNewArray.replaceAtUsages(fixedArrayNode);
    }

    private void lowerInvokeNode(InvokeNode invokeWithArrayAlloc) {
        CallTargetNode callTarget = invokeWithArrayAlloc.callTarget();
        final StructuredGraph graph = invokeWithArrayAlloc.graph();
        final ValueNode secondInput = callTarget.arguments().get(1);

        if (secondInput instanceof ConstantNode lengthNode) {
            if (lengthNode.getValue() instanceof PrimitiveConstant) {
                final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                JavaKind elementKind = getJavaKindFromConstantNode((ConstantNode) callTarget.arguments().get(0));
                final int offset = metaAccessProvider.getArrayBaseOffset(elementKind);
                final int size = offset + (elementKind.getByteCount() * length);

                if (SPIRVLoweringProvider.isGPUSnippet()) {
                    lowerLocalInvokeNodeNewArray(graph, length, elementKind, invokeWithArrayAlloc);
                } else {
                    lowerPrivateInvokeNodeNewArray(graph, size, elementKind, invokeWithArrayAlloc);
                }

                invokeWithArrayAlloc.clearInputs();
                GraphUtil.unlinkFixedNode(invokeWithArrayAlloc);
            } else {
                throw new TornadoRuntimeException("Unimplemented");
            }
        } else {
            throw new TornadoRuntimeException("dynamically sized array declarations are not supported");
        }
    }

    private JavaKind getJavaKindFromConstantNode(ConstantNode signatureNode) {
        switch (signatureNode.getValue().toValueString()) {
            case "Class:int":
            case "Class:uk.ac.manchester.tornado.api.types.arrays.IntArray":
                return JavaKind.Int;
            case "Class:long":
            case "Class:uk.ac.manchester.tornado.api.types.arrays.LongArray":
                return JavaKind.Long;
            case "Class:float":
            case "Class:uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                return JavaKind.Float;
            case "Class:double":
            case "Class:uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                return JavaKind.Double;
            default:
                unimplemented("Other types not supported yet: " + signatureNode.getValue().toValueString());
        }
        return null;
    }
}
