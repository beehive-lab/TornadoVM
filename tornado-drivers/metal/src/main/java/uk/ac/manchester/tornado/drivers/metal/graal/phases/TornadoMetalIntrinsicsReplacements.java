/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.Optional;

import tornado.graal.compiler.core.common.type.ObjectStamp;
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.graph.iterators.NodeIterable;
import tornado.graal.compiler.nodes.CallTargetNode;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.InvokeNode;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.AddNode;
import tornado.graal.compiler.nodes.calc.MulNode;
import tornado.graal.compiler.nodes.calc.SignExtendNode;
import tornado.graal.compiler.nodes.memory.address.OffsetAddressNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalLoweringProvider;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalBarrierNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalPrintf;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoMetalIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccess;

    public TornadoMetalIntrinsicsReplacements(MetaAccessProvider metaAccess) {
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
                case "Direct#MetalIntrinsics.localBarrier": {
                    MetalBarrierNode barrier = graph.addOrUnique(new MetalBarrierNode(MetalBarrierNode.MetalMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#MetalIntrinsics.globalBarrier": {
                    MetalBarrierNode barrier = graph.addOrUnique(new MetalBarrierNode(MetalBarrierNode.MetalMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#MetalIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIDFixedNode localIDNode = graph.addOrUnique(new LocalThreadIDFixedNode(dimension));
                    graph.replaceFixed(invoke, localIDNode);
                    break;
                }
                case "Direct#MetalIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSizeNode = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSizeNode);
                    break;
                }
                case "Direct#MetalIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadId = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadId);
                    break;
                }
                case "Direct#MetalIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalSize = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalSize);
                    break;
                }
                case "Direct#MetalIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#MetalIntrinsics.printEmpty":
                    MetalPrintf printfNode = graph.addOrUnique(new MetalPrintf("\"\""));
                    graph.replaceFixed(invoke, printfNode);
                    break;
                // KernelContext.atomicAdd: on the reflection path the invoke misses its InvocationPlugin and
                // survives, and its default body lowers to an address whose base is a ConstantNode that
                // MetalAddressLowering rejects ("address origin unimplemented") / a NewInstance for LongArray.
                // Rewrite to the AtomAddNodeTemplate the invocation plugin emits (mirrors the OpenCL backend).
                case "Direct#KernelContext.atomicAdd":
                    lowerAtomicAdd(graph, invoke);
                    break;
            }
        }
    }

    /**
     * Rewrite a surviving {@code KernelContext.atomicAdd(array, index, inc)} invoke into an {@link AtomAddNodeTemplate},
     * mirroring the invocation plugin. Argument 0 is the {@code KernelContext} receiver; 1 the array segment, 2 the
     * element index, 3 the increment. The element address uses the Panama array header (16) the kernel addresses with,
     * matching the buffer laid out by MetalArrayWrapper on the reflection path.
     */
    private void lowerAtomicAdd(StructuredGraph graph, InvokeNode invoke) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        ValueNode segment = args.get(1);
        ValueNode index = args.get(2);
        ValueNode inc = args.get(3);
        JavaKind kind = atomicElementKind(segment);
        int headerElements = (int) TornadoOptions.PANAMA_OBJECT_HEADER_SIZE / kind.getByteCount();
        AddNode newIndex = graph.addOrUniqueWithInputs(new AddNode(index, ConstantNode.forInt(headerElements, graph)));
        SignExtendNode signExtend = graph.addOrUniqueWithInputs(new SignExtendNode(newIndex, JavaKind.Long.getBitCount()));
        MulNode offset = graph.addOrUniqueWithInputs(new MulNode(signExtend, ConstantNode.forInt(kind.getByteCount(), graph)));
        OffsetAddressNode address = graph.addWithoutUnique(new OffsetAddressNode(segment, offset));
        AtomAddNodeTemplate atomicAdd = graph.add(new AtomAddNodeTemplate(address, inc, kind));
        graph.replaceFixed(invoke, atomicAdd);
    }

    /**
     * Element kind of the array argument to {@code atomicAdd}, from its stamp type: IntArray/int[]-&gt;Int,
     * LongArray-&gt;Long, FloatArray-&gt;Float, DoubleArray-&gt;Double.
     */
    private static JavaKind atomicElementKind(ValueNode segment) {
        Stamp stamp = GraphUtil.unproxify(segment).stamp(NodeView.DEFAULT);
        String name = (stamp instanceof ObjectStamp objectStamp && objectStamp.type() != null) ? objectStamp.type().toJavaName() : "";
        if (name.contains("LongArray")) {
            return JavaKind.Long;
        }
        if (name.contains("FloatArray")) {
            return JavaKind.Float;
        }
        if (name.contains("DoubleArray")) {
            return JavaKind.Double;
        }
        return JavaKind.Int;
    }

    private void lowerLocalInvokeNodeNewArray(StructuredGraph graph, int length, JavaKind elementKind, InvokeNode newArray) {
        LocalArrayNode localArrayNode;
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(MetalArchitecture.localSpace, elementType, newLengthNode));
        newArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateInvokeNodeNewArray(StructuredGraph graph, int size, JavaKind elementKind, InvokeNode newArray) {
        FixedArrayNode fixedArrayNode;
        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(MetalArchitecture.privateSpace, elementType, newLengthNode));
        newArray.replaceAtUsages(fixedArrayNode);
    }

    private void lowerInvokeNode(InvokeNode newArray) {
        CallTargetNode callTarget = newArray.callTarget();
        final StructuredGraph graph = newArray.graph();
        final ValueNode secondInput = callTarget.arguments().get(1);
        if (secondInput instanceof ConstantNode lengthNode) {
            if (lengthNode.getValue() instanceof PrimitiveConstant) {
                final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                JavaKind elementKind = getJavaKindFromConstantNode((ConstantNode) callTarget.arguments().get(0));
                final int offset = metaAccess.getArrayBaseOffset(elementKind);
                final int size = offset + (elementKind.getByteCount() * length);
                // This phase should come after Metal lowering, therefore
                // MetalLoweringProvider::gpuSnippet should be set
                if (MetalLoweringProvider.isGPUSnippet()) {
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
            case "Class:int", "Class:uk.ac.manchester.tornado.api.types.arrays.IntArray":
                return JavaKind.Int;
            case "Class:long", "Class:uk.ac.manchester.tornado.api.types.arrays.LongArray":
                return JavaKind.Long;
            case "Class:float", "Class:uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                return JavaKind.Float;
            case "Class:double", "Class:uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                return JavaKind.Double;
            default:
                unimplemented("Other types not supported yet: " + signatureNode.getValue().toValueString());
        }
        return null;
    }

}
