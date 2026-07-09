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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
package uk.ac.manchester.tornado.drivers.cuda.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.Optional;

import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.graph.iterators.NodeIterable;
import tornado.graal.compiler.nodes.CallTargetNode;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.InvokeNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDALoweringProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDABarrierNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAPrintf;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoCUDAIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccess;

    public TornadoCUDAIntrinsicsReplacements(MetaAccessProvider metaAccess) {
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
                case "Direct#CUDAIntrinsics.localBarrier": {
                    CUDABarrierNode barrier = graph.addOrUnique(new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#CUDAIntrinsics.globalBarrier": {
                    CUDABarrierNode barrier = graph.addOrUnique(new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, barrier);
                    break;
                }
                case "Direct#CUDAIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIDFixedNode localIDNode = graph.addOrUnique(new LocalThreadIDFixedNode(dimension));
                    graph.replaceFixed(invoke, localIDNode);
                    break;
                }
                case "Direct#CUDAIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSizeNode = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSizeNode);
                    break;
                }
                case "Direct#CUDAIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadId = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadId);
                    break;
                }
                case "Direct#CUDAIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalSize = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalSize);
                    break;
                }
                case "Direct#CUDAIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#CUDAIntrinsics.printEmpty":
                    CUDAPrintf printfNode = graph.addOrUnique(new CUDAPrintf("\"\""));
                    graph.replaceFixed(invoke, printfNode);
                    break;
                // KernelContext.allocate*LocalArray: normally intrinsified by an invocation plugin, but on the
                // JVMCI-absent (reflection) path Graal's InvocationPlugins.lookupInvocation misses these
                // array-returning methods, so the invoke survives and its result reaches address lowering as an
                // InvokeNode. Rewrite it here to a LocalArrayNode, exactly as the plugin would have.
                case "Direct#KernelContext.allocateIntLocalArray":
                    lowerLocalArrayAllocation(graph, invoke, JavaKind.Int);
                    break;
                case "Direct#KernelContext.allocateLongLocalArray":
                    lowerLocalArrayAllocation(graph, invoke, JavaKind.Long);
                    break;
                case "Direct#KernelContext.allocateFloatLocalArray":
                    lowerLocalArrayAllocation(graph, invoke, JavaKind.Float);
                    break;
                case "Direct#KernelContext.allocateDoubleLocalArray":
                    lowerLocalArrayAllocation(graph, invoke, JavaKind.Double);
                    break;
                case "Direct#KernelContext.allocateByteLocalArray":
                    lowerLocalArrayAllocation(graph, invoke, JavaKind.Byte);
                    break;
                case "Direct#KernelContext.allocateHalfFloatLocalArray":
                    lowerHalfFloatLocalArrayAllocation(graph, invoke);
                    break;
                // Same reflection-path plugin-lookup miss: KernelContext.local/globalBarrier survives as a
                // device-function call passing the KernelContext object. Intrinsify to the CUDA barrier so the
                // context receiver is dropped.
                case "Direct#KernelContext.localBarrier": {
                    CUDABarrierNode kcLocalBarrier = graph.addOrUnique(new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, kcLocalBarrier);
                    break;
                }
                case "Direct#KernelContext.globalBarrier": {
                    CUDABarrierNode kcGlobalBarrier = graph.addOrUnique(new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, kcGlobalBarrier);
                    break;
                }
            }
        }
    }

    /**
     * Rewrite a surviving {@code KernelContext.allocate*LocalArray(size)} invoke into a {@link LocalArrayNode}
     * (CUDA {@code __shared__} memory), matching what the invocation plugin emits. Argument 0 is the
     * {@code KernelContext} receiver; argument 1 is the size.
     */
    private void lowerLocalArrayAllocation(StructuredGraph graph, InvokeNode invoke, JavaKind elementKind) {
        ValueNode size = invoke.callTarget().arguments().get(1);
        LocalArrayNode localArrayNode = graph.addWithoutUnique(new LocalArrayNode(CUDAArchitecture.localSpace, elementKind, size));
        invoke.replaceAtUsages(localArrayNode);
        invoke.clearInputs();
        GraphUtil.unlinkFixedNode(invoke);
    }

    /**
     * Half-float variant of {@link #lowerLocalArrayAllocation}: {@code KernelContext.allocateHalfFloatLocalArray}
     * returns {@code HalfFloat[]}, so the {@link LocalArrayNode} is built with a {@code short} element type tagged
     * {@link CUDAKind#HALF}.
     */
    private void lowerHalfFloatLocalArrayAllocation(StructuredGraph graph, InvokeNode invoke) {
        ValueNode size = invoke.callTarget().arguments().get(1);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(short.class);
        LocalArrayNode localArrayNode = graph.addWithoutUnique(new LocalArrayNode(CUDAArchitecture.localSpace, elementType, size, CUDAKind.HALF));
        invoke.replaceAtUsages(localArrayNode);
        invoke.clearInputs();
        GraphUtil.unlinkFixedNode(invoke);
    }

    private void lowerLocalInvokeNodeNewArray(StructuredGraph graph, int length, JavaKind elementKind, InvokeNode newArray) {
        LocalArrayNode localArrayNode;
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(CUDAArchitecture.localSpace, elementType, newLengthNode));
        newArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateInvokeNodeNewArray(StructuredGraph graph, int size, JavaKind elementKind, InvokeNode newArray) {
        FixedArrayNode fixedArrayNode;
        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementKind.toJavaClass());
        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(CUDAArchitecture.privateSpace, elementType, newLengthNode));
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
                // This phase should come after CUDA lowering, therefore
                // CUDALoweringProvider::gpuSnippet should be set
                if (CUDALoweringProvider.isGPUSnippet()) {
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
