/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal;

import static jdk.graal.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder.GPU_MEMORY_MODE;

import java.util.Iterator;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.LoweredCallTargetNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.AbstractWriteNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.vm.ci.code.CodeUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.CastNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.snippets.ReduceGPUSnippets;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.GetGroupIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.GlobalGroupSizeFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.LocalGroupSizeFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewArrayNonVirtualizableNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ThreadIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ThreadLocalIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoDirectCallTargetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkLocalArray;

/**
 * Lower IR from one representation to another (e.g., from TornadoVM High-IR to
 * TornadoVM Mid-IR).
 */
public class SPIRVLoweringProvider extends DefaultJavaLoweringProvider {

    private static boolean gpuSnippet = false;
    private ConstantReflectionProvider constantReflectionProvider;
    private TornadoVMConfigAccess vmConfig;
    private ReduceGPUSnippets.Templates gpuReduceSnippets;

    public SPIRVLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
            MetaAccessExtensionProvider metaAccessExtensionProvider, ConstantReflectionProvider constantReflectionProvider, TornadoVMConfigAccess vmConfig, SPIRVTargetDescription target,
            boolean useCompressedOops) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, useCompressedOops);
        this.constantReflectionProvider = constantReflectionProvider;
        this.vmConfig = vmConfig;
    }

    /**
     * {@link SPIRVLoweringProvider#gpuSnippet} is set during the lowering phase.
     * Therefore, this method must be called after a lowering phase in order to get
     * the correct result.
     *
     * @return boolean
     */
    public static boolean isGPUSnippet() {
        return gpuSnippet;
    }

    @Override
    public void initialize(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
        super.initialize(options, factory, providers);
        initializeSnippets(options, providers);
    }

    @Override
    protected IdentityHashCodeSnippets.Templates createIdentityHashCodeSnippets(OptionValues options, Providers providers) {
        return null;
    }

    private void initializeSnippets(OptionValues options, Providers providers) {
        this.gpuReduceSnippets = new ReduceGPUSnippets.Templates(options, providers);
    }

    private boolean shouldIgnoreNode(Node node) {
        return (node instanceof AbstractDeoptimizeNode //
                || node instanceof UnwindNode //
                || node instanceof RemNode //
                || node instanceof InstanceOfNode //
                || node instanceof IntegerDivRemNode);
    }

    @Override
    public void lower(Node node, LoweringTool tool) {
        if (shouldIgnoreNode(node)) {
            return;
        }
        if (node instanceof Invoke) {
            lowerInvoke((Invoke) node, tool, (StructuredGraph) node.graph());
        } else if (node instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) node, tool);
        } else if (node instanceof NewArrayNonVirtualizableNode) {
            lowerNewArrayNode((NewArrayNonVirtualizableNode) node);
        } else if (node instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) node, tool);
        } else if (node instanceof StoreAtomicIndexedNode) {
            lowerReduceSnippets(node, tool);
        } else if (node instanceof WriteAtomicNode) {
            lowerReduceSnippets(node, tool);
        } else if (node instanceof FloatConvertNode) {
            lowerFloatConvertNode((FloatConvertNode) node);
        } else if (node instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) node, tool);
        } else if (node instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) node, tool);
        } else if (node instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) node, tool);
        } else if (node instanceof ThreadIdFixedWithNextNode) {
            lowerThreadIdNode((ThreadIdFixedWithNextNode) node);
        } else if (node instanceof ThreadLocalIdFixedWithNextNode) {
            lowerLocalThreadIdNode((ThreadLocalIdFixedWithNextNode) node);
        } else if (node instanceof GetGroupIdFixedWithNextNode) {
            lowerGetGroupIdNode((GetGroupIdFixedWithNextNode) node);
        } else if (node instanceof GlobalGroupSizeFixedWithNextNode) {
            lowerGlobalGroupSizeNode((GlobalGroupSizeFixedWithNextNode) node);
        } else if (node instanceof LocalGroupSizeFixedWithNextNode) {
            lowerLocalGroupSizeNode((LocalGroupSizeFixedWithNextNode) node);
        } else {
            super.lower(node, tool);
        }
    }

    private void lowerLocalThreadIdNode(ThreadLocalIdFixedWithNextNode threadLocalIdNode) {
        StructuredGraph graph = threadLocalIdNode.graph();
        LocalThreadIdNode localThreadIdNode = graph.addWithoutUnique(new LocalThreadIdNode(ConstantNode.forInt(threadLocalIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(threadLocalIdNode, localThreadIdNode);
    }

    private void lowerThreadIdNode(ThreadIdFixedWithNextNode threadIdNode) {
        StructuredGraph graph = threadIdNode.graph();
        GlobalThreadIdNode globalThreadIdNode = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(threadIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(threadIdNode, globalThreadIdNode);
    }

    private void lowerReduceSnippets(Node node, LoweringTool tool) {

        StructuredGraph graph = null;
        ValueNode startIndexNode = null;
        if (node instanceof StoreAtomicIndexedNode) {
            graph = ((StoreAtomicIndexedNode) node).graph();
            startIndexNode = ((StoreAtomicIndexedNode) node).getStartNode();
        } else if (node instanceof WriteAtomicNode) {
            graph = ((WriteAtomicNode) node).graph();
            startIndexNode = ((WriteAtomicNode) node).getStartNode();
        }

        // Find Get Global ID node and Global Size;
        GlobalThreadIdNode spirvIDNode = graph.getNodes().filter(GlobalThreadIdNode.class).first();
        GlobalThreadSizeNode spirvGlobalSize = graph.getNodes().filter(GlobalThreadSizeNode.class).first();

        ValueNode threadID = null;
        Iterator<Node> usages = spirvIDNode.usages().iterator();

        boolean cpuScheduler = false;

        while (usages.hasNext()) {
            Node n = usages.next();

            // GPU SCHEDULER
            if (n instanceof BinaryArithmeticNode) {
                if (n.usages().filter(PhiNode.class).isNotEmpty()) {
                    gpuSnippet = true;
                    threadID = n.usages().filter(PhiNode.class).first();
                    break;
                }
            }

            if (n instanceof PhiNode) {
                gpuSnippet = true;
                threadID = (ValueNode) n;
                break;
            }
        }
        // Depending on the Scheduler, call the proper snippet factory
        if (cpuScheduler) {
            throw new TornadoRuntimeException("CPU Snippets for SPIR-V not implemented yet");
        } else {
            if (node instanceof StoreAtomicIndexedNode storeIndexed) {
                gpuReduceSnippets.lower(storeIndexed, threadID, spirvGlobalSize, tool);
            } else if (node instanceof WriteAtomicNode writeAtomicNode) {
                gpuReduceSnippets.lower(writeAtomicNode, threadID, spirvGlobalSize, tool);
            }
        }

    }

    private void lowerLocalNewArray(StructuredGraph graph, int length, NewArrayNonVirtualizableNode newArray) {
        LocalArrayNode localArrayNode;
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.localSpace, newArray.elementType(), newLengthNode));
        newArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateNewArray(StructuredGraph graph, int size, NewArrayNonVirtualizableNode newArray) {
        FixedArrayNode fixedArrayNode;
        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.privateSpace, newArray.elementType(), newLengthNode));
        newArray.replaceAtUsages(fixedArrayNode);
    }

    private void lowerNewArrayNode(NewArrayNonVirtualizableNode newArray) {
        final StructuredGraph graph = newArray.graph();
        final ValueNode firstInput = newArray.length();
        if (firstInput instanceof ConstantNode) {
            if (newArray.dimensionCount() == 1) {
                final ConstantNode lengthNode = (ConstantNode) firstInput;
                if (lengthNode.getValue() instanceof PrimitiveConstant) {
                    final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                    if (gpuSnippet) {
                        lowerLocalNewArray(graph, length, newArray);
                    } else {
                        lowerPrivateNewArray(graph, length, newArray);
                    }
                    newArray.clearInputs();
                    GraphUtil.unlinkFixedNode(newArray);
                    GraphUtil.removeFixedWithUnusedInputs(newArray);
                } else {
                    shouldNotReachHere();
                }
            } else {
                unimplemented("multi-dimensional array declarations are not supported");
            }
        } else {
            unimplemented("dynamically sized array declarations are not supported");
        }
    }

    private void lowerGetGroupIdNode(GetGroupIdFixedWithNextNode getGroupIdNode) {
        StructuredGraph graph = getGroupIdNode.graph();
        GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(ConstantNode.forInt(getGroupIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(getGroupIdNode, groupIdNode);
    }

    private void lowerGlobalGroupSizeNode(GlobalGroupSizeFixedWithNextNode globalGroupSizeNode) {
        StructuredGraph graph = globalGroupSizeNode.graph();
        GlobalThreadSizeNode globalThreadSizeNode = graph.addOrUnique(new GlobalThreadSizeNode(ConstantNode.forInt(globalGroupSizeNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(globalGroupSizeNode, globalThreadSizeNode);
    }

    private void lowerLocalGroupSizeNode(LocalGroupSizeFixedWithNextNode localGroupSizeNode) {
        StructuredGraph graph = localGroupSizeNode.graph();
        LocalThreadSizeNode localThreadSizeNode = graph.addOrUnique(new LocalThreadSizeNode(ConstantNode.forInt(localGroupSizeNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(localGroupSizeNode, localThreadSizeNode);
    }

    private void lowerFloatConvertNode(FloatConvertNode floatConvert) {
        final StructuredGraph graph = floatConvert.graph();
        final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.stamp(NodeView.DEFAULT), floatConvert.getFloatConvert(), floatConvert.getValue()));
        floatConvert.replaceAtUsages(asFloat);
        floatConvert.safeDelete();
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = null;
            if (parameters.size() > 0) {
                receiver = parameters.get(0);
            }

            if (receiver != null && !callTarget.isStatic() && receiver.stamp(NodeView.DEFAULT) instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                ValueNode nonNullReceiver = createNullCheckedValue(receiver, invoke.asFixedNode(), tool);
                parameters.set(0, nonNullReceiver);
            }

            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());
            LoweredCallTargetNode loweredCallTarget;
            StampPair returnStampPair = callTarget.returnStamp();
            Stamp returnStamp = returnStampPair.getTrustedStamp();
            if (returnStamp instanceof ObjectStamp) {
                ObjectStamp os = (ObjectStamp) returnStamp;
                ResolvedJavaType type = os.javaType(tool.getMetaAccess());
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(type);
                if (kind != SPIRVKind.ILLEGAL) {
                    returnStampPair = StampPair.createSingle(uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory.getStampFor(kind));
                }
            }

            loweredCallTarget = graph.add(new TornadoDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), returnStampPair, signature, callTarget.targetMethod(),
                    HotSpotCallingConventionType.JavaCall, callTarget.invokeKind()));
            callTarget.replaceAndDelete(loweredCallTarget);
        }
    }

    @Override
    public int fieldOffset(ResolvedJavaField field) {
        HotSpotResolvedJavaField hsField = (HotSpotResolvedJavaField) field;
        return hsField.getOffset();
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field) {
        HotSpotResolvedJavaField hsField = (HotSpotResolvedJavaField) field;
        JavaConstant base = constantReflectionProvider.asJavaClass(hsField.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    @Override
    public int arrayLengthOffset() {
        return vmConfig.arrayOopDescLengthOffset();
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        unimplemented("SPIRVLoweringProvider::loadCompressedStamp");
        unimplemented();
        return null;
    }

    @Override
    protected ValueNode newCompressionNode(CompressionNode.CompressionOp op, ValueNode value) {
        unimplemented("SPIRVLoweringProvider::newCompressionNode");
        return null;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        unimplemented("SPIRVLoweringProvider::createReadHub unimplemented");
        return null;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor) {
        unimplemented("SPIRVLoweringProvider::createReadArrayComponentHub unimplemented");
        return null;
    }

    /**
     * From Graal: it indicates the smallest width for comparing an integer value on
     * the target platform.
     */
    @Override
    public Integer smallestCompareWidth() {
        return null;
    }

    @Override
    public boolean supportsBulkZeroingOfEden() {
        return false;
    }

    @Override
    public boolean supportsBulkClearArray(JavaKind elementKind) {
        return super.supportsBulkClearArray(elementKind);
    }

    @Override
    public boolean supportsBulkZeroing() {
        unimplemented("SPIRVLoweringProvider::supportsBulkZeroing unimplemented");
        return false;
    }

    @Override
    public boolean supportsRounding() {
        return false;
    }

    @Override
    public boolean writesStronglyOrdered() {
        return false;
    }

    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        return false;
    }

    @Override
    public boolean narrowsUseCastValue() {
        return false;
    }

    @Override
    public boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        return false;
    }

    @Override
    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();

        AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
        ReadNode arrayLengthRead = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE, TornadoMemoryOrder.GPU_MEMORY_MODE));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    private boolean isLocalIDNode(LoadIndexedNode loadIndexedNode) {
        // Either the node has as input a LocalArray or has a node which will be lowered
        // to a LocalArray
        Node nd = loadIndexedNode.inputs().first();
        InvokeNode node = nd.inputs().filter(InvokeNode.class).first();
        boolean willLowerToLocalArrayNode = node != null && "Direct#NewArrayNode.newArray".equals(node.callTarget().targetName()) && gpuSnippet;
        return (nd instanceof MarkLocalArray || willLowerToLocalArrayNode);
    }

    private boolean isPrivateIDNode(LoadIndexedNode loadIndexedNode) {
        Node nd = loadIndexedNode.inputs().first();
        return (nd instanceof FixedArrayNode);
    }

    private AddressNode createArrayAccess(StructuredGraph graph, LoadIndexedNode loadIndexed, JavaKind elementKind) {
        AddressNode address;
        if (isLocalIDNode(loadIndexed) || isPrivateIDNode(loadIndexed)) {
            address = createArrayLocalAddress(graph, loadIndexed.array(), loadIndexed.index());
        } else {
            address = createArrayAddressTornado(graph, loadIndexed.array(), elementKind, loadIndexed.index());
        }
        return address;
    }

    @Override
    public void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        JavaKind elementKind = loadIndexed.elementKind();
        AddressNode address;

        Stamp loadStamp = loadIndexed.stamp(NodeView.DEFAULT);
        if (!(loadIndexed.stamp(NodeView.DEFAULT) instanceof uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp)) {
            loadStamp = loadStamp(loadIndexed.stamp(NodeView.DEFAULT), elementKind, false);
        }
        address = createArrayAccess(graph, loadIndexed, elementKind);
        ReadNode memoryRead;
        if (loadIndexed instanceof LoadIndexedVectorNode) {
            memoryRead = graph.add(new ReadNode(address, LocationIdentity.any(), loadStamp, BarrierType.NONE, GPU_MEMORY_MODE));
        } else {
            memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE, GPU_MEMORY_MODE));
        }
        loadIndexed.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    private boolean isPrivateMemoryAccessNode(StoreIndexedNode storeIndexed) {
        Node node = storeIndexed.inputs().first();
        return (node instanceof FixedArrayNode);
    }

    private boolean isLocalMemoryAccessNode(StoreIndexedNode storeIndexed) {
        // Either the node has as input a LocalArray or has a node which will be lowered
        // to a LocalArray
        Node nd = storeIndexed.inputs().first();
        InvokeNode node = nd.inputs().filter(InvokeNode.class).first();
        boolean willLowerToLocalArrayNode = node != null && "Direct#NewArrayNode.newArray".equals(node.callTarget().targetName()) && gpuSnippet;
        return (nd instanceof MarkLocalArray || willLowerToLocalArrayNode);
    }

    private AddressNode createArrayLocalAddress(StructuredGraph graph, ValueNode array, ValueNode index) {
        return graph.unique(new OffsetAddressNode(array, index));
    }

    private AbstractWriteNode createMemWriteNode(JavaKind elementKind, ValueNode value, ValueNode array, AddressNode address, StructuredGraph graph, StoreIndexedNode storeIndexed) {
        AbstractWriteNode memoryWrite;
        if (isLocalMemoryAccessNode(storeIndexed) || isPrivateMemoryAccessNode(storeIndexed)) {
            address = createArrayLocalAddress(graph, array, storeIndexed.index());
        }
        ValueNode storeConvertValue = value;
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        if (!(valueStamp instanceof uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp) || !((uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp) valueStamp).getSPIRVKind().isVector()) {
            storeConvertValue = implicitStoreConvert(graph, elementKind, value);
        }
        memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), storeConvertValue, BarrierType.NONE, TornadoMemoryOrder.GPU_MEMORY_MODE));
        return memoryWrite;
    }

    @Override
    public void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        JavaKind elementKind = storeIndexed.elementKind();
        ValueNode valueToStore = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        ValueNode index = storeIndexed.index();
        AddressNode address = createArrayAddressTornado(graph, array, elementKind, index);
        AbstractWriteNode memoryWrite = createMemWriteNode(elementKind, valueToStore, array, address, graph, storeIndexed);
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    public AddressNode createArrayAddressTornado(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index) {
        int arrayBaseOffset = (int) TornadoOptions.PANAMA_OBJECT_HEADER_SIZE;
        ValueNode wordIndex;
        if (target.wordSize > 4) {
            wordIndex = graph.unique(new SignExtendNode(index, target.wordSize * 8));
        } else {
            assert target.wordSize == 4 : "unsupported word size";
            wordIndex = index;
        }
        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(wordIndex, ConstantNode.forInt(shift, graph)));
        ValueNode offset = graph.unique(new AddNode(scaledIndex, ConstantNode.forIntegerKind(target.wordJavaKind, arrayBaseOffset, graph)));
        return graph.unique(new OffsetAddressNode(array, offset));
    }

    @Override
    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        Stamp loadStamp = loadStamp(loadField.stamp(NodeView.DEFAULT), field.getJavaKind());
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();
        FieldLocationIdentity fieldLocationIdentity = new FieldLocationIdentity(field);
        ReadNode memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity, loadStamp, BarrierType.NONE, TornadoMemoryOrder.GPU_MEMORY_MODE));
        loadField.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadField, memoryRead);
    }

    @Override
    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;
        FieldLocationIdentity fieldLocationIdentity = new FieldLocationIdentity(field);
        WriteNode memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity, storeField.value(), BarrierType.NONE, TornadoMemoryOrder.GPU_MEMORY_MODE));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixed(storeField, memoryWrite);
    }
}
