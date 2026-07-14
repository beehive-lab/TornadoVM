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
import tornado.graal.compiler.core.common.type.ObjectStamp;
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.nodes.InvokeNode;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.AddNode;
import tornado.graal.compiler.nodes.calc.MulNode;
import tornado.graal.compiler.nodes.calc.SignExtendNode;
import tornado.graal.compiler.nodes.java.LoadFieldNode;
import tornado.graal.compiler.nodes.memory.address.OffsetAddressNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDALoweringProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAComputeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAFragmentNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadANode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadAInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAShuffleDownNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASimdBroadcastFirstNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASimdSumNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASwizzledLoadFP16Stride32Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASwizzledStoreFP16Stride32Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDABarrierNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAPrintf;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoObjectConstant;

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
                // Tensor-core (WMMA) intrinsics: same reflection-path plugin-lookup miss as the KernelContext
                // helpers above. The mma* calls survive as device-function invokes on the KernelContext receiver;
                // rewrite each to the dedicated MMA node the invocation plugin would have produced.
                case "Direct#KernelContext.mmaFragment":
                    lowerMMAFragment(graph, invoke, false);
                    break;
                case "Direct#KernelContext.mmaFragmentInt":
                    lowerMMAFragment(graph, invoke, true);
                    break;
                case "Direct#KernelContext.mmaLoadA":
                    lowerMMALoad(graph, invoke, MMALoadKind.A);
                    break;
                case "Direct#KernelContext.mmaLoadB":
                    lowerMMALoad(graph, invoke, MMALoadKind.B);
                    break;
                case "Direct#KernelContext.mmaLoadBSwizzled":
                    lowerMMALoad(graph, invoke, MMALoadKind.B_SWIZZLED);
                    break;
                case "Direct#KernelContext.mmaLoadAInt8":
                    lowerMMALoad(graph, invoke, MMALoadKind.A_INT8);
                    break;
                case "Direct#KernelContext.mmaLoadBInt8":
                    lowerMMALoad(graph, invoke, MMALoadKind.B_INT8);
                    break;
                case "Direct#KernelContext.mma":
                case "Direct#KernelContext.mmaInt8":
                    lowerMMACompute(graph, invoke);
                    break;
                case "Direct#KernelContext.mmaStore":
                    lowerMMAStore(graph, invoke, JavaKind.Float, false);
                    break;
                case "Direct#KernelContext.mmaStoreInt":
                    lowerMMAStore(graph, invoke, JavaKind.Int, true);
                    break;
                case "Direct#KernelContext.mmaStoreBSwizzled":
                    lowerMMAStoreBSwizzled(graph, invoke);
                    break;
                case "Direct#KernelContext.swizzleLoadFp16Stride32":
                    lowerSwizzleLoad(graph, invoke);
                    break;
                case "Direct#KernelContext.swizzleStoreFp16Stride32":
                    lowerSwizzleStore(graph, invoke);
                    break;
                // KernelContext.atomicAdd: plugin misses on the reflection path, so the call survives and the
                // KernelContext default body is compiled instead (its address reaches CUDAAddressLowering with an
                // unhandled ConstantNode origin -> "address origin unimplemented"). Rewrite to the AtomAddNode the
                // plugin would build.
                case "Direct#KernelContext.atomicAdd":
                    lowerAtomicAdd(graph, invoke);
                    break;
                // SIMD-group (warp-shuffle) reductions: their InvocationPlugins miss on the reflection path, so the
                // call survives to a device function whose KernelContext default body is a no-op (returns the input
                // unchanged) - producing a silently wrong reduction. Rewrite to the warp-shuffle nodes.
                case "Direct#KernelContext.simdShuffleDown": {
                    NodeInputList<ValueNode> a = invoke.callTarget().arguments();
                    graph.replaceFixed(invoke, graph.add(new CUDAShuffleDownNode(a.get(1), a.get(2))));
                    break;
                }
                case "Direct#KernelContext.simdSum": {
                    NodeInputList<ValueNode> a = invoke.callTarget().arguments();
                    graph.replaceFixed(invoke, graph.add(new CUDASimdSumNode(a.get(1))));
                    break;
                }
                case "Direct#KernelContext.simdBroadcastFirst": {
                    NodeInputList<ValueNode> a = invoke.callTarget().arguments();
                    graph.replaceFixed(invoke, graph.add(new CUDASimdBroadcastFirstNode(a.get(1))));
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

    private enum MMALoadKind {
        A, B, B_SWIZZLED, A_INT8, B_INT8
    }

    /**
     * Rewrite a surviving {@code KernelContext.mmaFragment(init)} / {@code mmaFragmentInt(init)} invoke into a
     * {@link CUDAMMAFragmentNode}. Argument 0 is the {@code KernelContext} receiver; argument 1 is the init value.
     */
    private void lowerMMAFragment(StructuredGraph graph, InvokeNode invoke, boolean isInt8) {
        ValueNode initValue = invoke.callTarget().arguments().get(1);
        CUDAMMAFragmentNode node = graph.add(new CUDAMMAFragmentNode(initValue, isInt8));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Rewrite a surviving {@code KernelContext.mmaLoad*} invoke into the matching MMA load node. Argument 0 is the
     * receiver, 1 the tile, 2 the wmmaK; an optional argument 3 is the per-lane byte offset (the offset-carrying
     * plugin overload).
     */
    private void lowerMMALoad(StructuredGraph graph, InvokeNode invoke, MMALoadKind kind) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        ValueNode tile = args.get(1);
        ValueNode wmmaK = args.get(2);
        ValueNode byteOffset = args.size() > 3 ? args.get(3) : null;
        ValueNode node = switch (kind) {
            case A -> new CUDAMMALoadANode(tile, wmmaK, byteOffset);
            case B -> new CUDAMMALoadBNode(tile, wmmaK, byteOffset);
            case B_SWIZZLED -> new CUDAMMALoadBSwizzledNode(tile, wmmaK, byteOffset);
            case A_INT8 -> new CUDAMMALoadAInt8Node(tile, wmmaK, byteOffset);
            case B_INT8 -> new CUDAMMALoadBInt8Node(tile, wmmaK, byteOffset);
        };
        graph.replaceFixed(invoke, graph.add(node));
    }

    /**
     * Rewrite a surviving {@code KernelContext.mma(...)} / {@code mmaInt8(...)} invoke into a
     * {@link CUDAMMAComputeNode}. Arguments 1-3 are fragA/fragB/fragC; argument 4 is the {@link MMAShape} constant.
     */
    private void lowerMMACompute(StructuredGraph graph, InvokeNode invoke) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        MMAShape shape = resolveShape(args.get(4));
        CUDAMMAComputeNode node = graph.add(new CUDAMMAComputeNode(args.get(1), args.get(2), args.get(3), shape));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Rewrite a surviving {@code KernelContext.mmaStore(...)} / {@code mmaStoreInt(...)} invoke into a
     * {@link CUDAMMAStoreNode}. Arguments 1-5 are fragD/target/tileRow/tileCol/dimN.
     */
    private void lowerMMAStore(StructuredGraph graph, InvokeNode invoke, JavaKind elementKind, boolean isInt) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        // The target is a Panama native array (FloatArray/IntArray), whose data begins after PANAMA_OBJECT_HEADER_SIZE
        // (16) bytes - not the JVM array-base offset that getVMConfig().getArrayBaseOffset returns on the reflection
        // path (12), which would place every store 4 bytes early and overrun the buffer for larger tiles.
        int headerElements = (int) TornadoOptions.PANAMA_OBJECT_HEADER_SIZE / elementKind.getByteCount();
        CUDAMMAStoreNode node = graph.add(new CUDAMMAStoreNode(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5), headerElements, isInt));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Rewrite a surviving {@code KernelContext.mmaStoreBSwizzled(...)} invoke into a {@link CUDAMMAStoreBSwizzledNode}.
     * Arguments 1-6 are arr/row/col/stride/value/byteOffset.
     */
    private void lowerMMAStoreBSwizzled(StructuredGraph graph, InvokeNode invoke) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        CUDAMMAStoreBSwizzledNode node = graph.add(new CUDAMMAStoreBSwizzledNode(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5), args.get(6)));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Rewrite a surviving {@code KernelContext.swizzleLoadFp16Stride32(local, row, col, stride)} invoke into a
     * {@link CUDASwizzledLoadFP16Stride32Node} (arguments 1-4 after the receiver).
     */
    /**
     * Rewrite a surviving {@code KernelContext.atomicAdd(array, index, inc)} invoke into an {@link AtomAddNodeTemplate},
     * mirroring the invocation plugin. Argument 0 is the {@code KernelContext} receiver; 1 the array segment, 2 the
     * element index, 3 the increment. The element address uses the Panama array header (16) that the kernel addresses
     * with, so it matches the buffer laid out by CUDAArrayWrapper.
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
     * Element kind of the array argument to {@code atomicAdd}, from its stamp type: IntArray/int[]->Int,
     * LongArray->Long, FloatArray->Float, DoubleArray->Double.
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

    private void lowerSwizzleLoad(StructuredGraph graph, InvokeNode invoke) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        CUDASwizzledLoadFP16Stride32Node node = graph.add(new CUDASwizzledLoadFP16Stride32Node(args.get(1), args.get(2), args.get(3), args.get(4)));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Rewrite a surviving {@code KernelContext.swizzleStoreFp16Stride32(local, row, col, stride, value)} invoke into a
     * {@link CUDASwizzledStoreFP16Stride32Node} (arguments 1-5 after the receiver).
     */
    private void lowerSwizzleStore(StructuredGraph graph, InvokeNode invoke) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        CUDASwizzledStoreFP16Stride32Node node = graph.add(new CUDASwizzledStoreFP16Stride32Node(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        graph.replaceFixed(invoke, node);
    }

    /**
     * Resolve an {@link MMAShape} enum argument to its compile-time constant. The enum reaches this phase as a
     * static {@code LoadFieldNode} whose field name is the enum-constant name (e.g. {@code M16N16K16}); resolve it
     * by name. We cannot use {@code ConstantReflectionProvider.readFieldValue} here because on the reflection path
     * the constant-reflection provider has no host {@code MetaAccessProvider} backing.
     */
    private MMAShape resolveShape(ValueNode shapeNode) {
        ValueNode node = GraphUtil.unproxify(shapeNode);
        // Unfolded static enum-field load: the field name is the enum-constant name (e.g. M16N16K16).
        if (node instanceof LoadFieldNode load && load.field().isStatic()) {
            return MMAShape.valueOf(load.field().getName());
        }
        // Folded to an object constant: on the reflection path this is a TornadoObjectConstant that holds the
        // actual enum instance, which we can read directly (SnippetReflection.asObject is unimplemented here).
        JavaConstant constant = node.asJavaConstant();
        if (constant instanceof TornadoObjectConstant tornadoConstant && tornadoConstant.getObject() instanceof MMAShape shape) {
            return shape;
        }
        throw new IllegalStateException("MMAShape argument to ctx.mma() must be a compile-time constant; got " + shapeNode);
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
