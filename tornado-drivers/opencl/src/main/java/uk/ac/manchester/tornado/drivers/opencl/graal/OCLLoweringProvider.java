/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder.GPU_MEMORY_MODE;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;

import java.util.Iterator;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.ExtendableMemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.SnippetCounter;
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
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.AtomicAddNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.CastNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.calc.DivNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.ReduceCPUSnippets;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.ReduceGPUSnippets;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
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
public class OCLLoweringProvider extends DefaultJavaLoweringProvider {

    private static final boolean USE_ATOMICS = false;
    private static boolean gpuSnippet = false;
    private final ConstantReflectionProvider constantReflection;
    private final TornadoVMConfigAccess vmConfig;
    private ReduceGPUSnippets.Templates gpuReduceSnippets;
    private ReduceCPUSnippets.Templates cpuReduceSnippets;

    public OCLLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig, MetaAccessExtensionProvider metaAccessExtensionProvider,
            ConstantReflectionProvider constantReflection, TornadoVMConfigAccess vmConfig, OCLTargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, false);
        this.vmConfig = vmConfig;
        this.constantReflection = constantReflection;
    }

    /**
     * {@link OCLLoweringProvider#gpuSnippet} is set during the lowering phase.
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
        initializeSnippets(options, factory, providers);
    }

    private void initializeSnippets(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
        this.cpuReduceSnippets = new ReduceCPUSnippets.Templates(options, providers);
        this.gpuReduceSnippets = new ReduceGPUSnippets.Templates(options, providers);
    }

    @Override
    public void lower(Node node, LoweringTool tool) {
        if (node instanceof Invoke) {
            lowerInvoke((Invoke) node, tool, (StructuredGraph) node.graph());
        } else if (node instanceof AbstractDeoptimizeNode || node instanceof UnwindNode || node instanceof RemNode) {
            /*
             * No lowering, we currently generate LIR directly for these nodes.
             */
        } else if (node instanceof FloatConvertNode) {
            lowerFloatConvertNode((FloatConvertNode) node);
        } else if (node instanceof NewArrayNonVirtualizableNode) {
            lowerNewArrayNode((NewArrayNonVirtualizableNode) node);
        } else if (node instanceof AtomicAddNode) {
            lowerAtomicAddNode((AtomicAddNode) node, tool);
        } else if (node instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) node, tool);
        } else if (node instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) node, tool);
        } else if (node instanceof StoreAtomicIndexedNode) {
            lowerReduceSnippets(node, tool);
        } else if (node instanceof WriteAtomicNode) {
            lowerReduceSnippets(node, tool);
        } else if (node instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) node, tool);
        } else if (node instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) node, tool);
        } else if (node instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) node, tool);
        } else if (node instanceof IntegerDivRemNode) {
            lowerIntegerDivRemNode((IntegerDivRemNode) node);
        } else if (node instanceof InstanceOfNode) {
            // ignore InstanceOfNode nodes
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

    @Override
    public Integer smallestCompareWidth() {
        return null;
    }

    @Override
    public boolean supportsBulkZeroing() {
        unimplemented("OCLLoweringProvider::supportsBulkZeroing unimplemented");
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
        GlobalThreadIdNode oclIdNode = graph.getNodes().filter(GlobalThreadIdNode.class).first();
        GlobalThreadSizeNode oclGlobalSize = graph.getNodes().filter(GlobalThreadSizeNode.class).first();

        ValueNode threadID = null;
        Iterator<Node> usages = oclIdNode.usages().iterator();

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

            // CPU SCHEDULER
            if (n instanceof MulNode) {
                for (Node n2 : n.usages()) {
                    if (n2 instanceof PhiNode) {
                        threadID = (ValueNode) n2;
                        cpuScheduler = true;
                        break;
                    }
                }
            }
        }
        // Depending on the Scheduler, call the proper snippet factory
        if (cpuScheduler) {
            if (node instanceof StoreAtomicIndexedNode storeAtomicIndexedNode) {
                cpuReduceSnippets.lower(storeAtomicIndexedNode, threadID, oclIdNode, startIndexNode, tool);
            } else if (node instanceof WriteAtomicNode writeAtomicNode) {
                cpuReduceSnippets.lower(writeAtomicNode, threadID, oclIdNode, startIndexNode, tool);
            }
        } else {
            if (node instanceof StoreAtomicIndexedNode storeAtomicIndexedNode) {
                gpuReduceSnippets.lower(storeAtomicIndexedNode, threadID, oclGlobalSize, tool);
            } else if (node instanceof WriteAtomicNode writeAtomicNode) {
                gpuReduceSnippets.lower(writeAtomicNode, threadID, oclGlobalSize, tool);
            }
        }
    }

    private void lowerIntegerDivRemNode(IntegerDivRemNode integerDivRemNode) {
        StructuredGraph graph = integerDivRemNode.graph();
        switch (integerDivRemNode.getOp()) {
            case DIV:
                ValueNode div = graph.addOrUnique(DivNode.create(integerDivRemNode.getX(), integerDivRemNode.getY()));
                graph.replaceFixedWithFloating(integerDivRemNode, div);
                break;
            case REM:
                ValueNode rem = graph.addOrUnique(RemNode.create(integerDivRemNode.getX(), integerDivRemNode.getY(), NodeView.DEFAULT));
                graph.replaceFixedWithFloating(integerDivRemNode, rem);
                break;
        }
    }

    private void lowerThreadIdNode(ThreadIdFixedWithNextNode threadIdNode) {
        StructuredGraph graph = threadIdNode.graph();

        GlobalThreadIdNode globalThreadIdNode = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(threadIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(threadIdNode, globalThreadIdNode);
    }

    private void lowerLocalThreadIdNode(ThreadLocalIdFixedWithNextNode threadLocalIdNode) {
        StructuredGraph graph = threadLocalIdNode.graph();
        LocalThreadIdNode localThreadIdNode = graph.addOrUnique(new LocalThreadIdNode(ConstantNode.forInt(threadLocalIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(threadLocalIdNode, localThreadIdNode);
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

    @Override
    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();

        AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
        ReadNode arrayLengthRead = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE, GPU_MEMORY_MODE));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    @Override
    public void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        JavaKind elementKind = loadIndexed.elementKind();
        AddressNode address;

        Stamp loadStamp = loadIndexed.stamp(NodeView.DEFAULT);
        if (!(loadIndexed.stamp(NodeView.DEFAULT) instanceof OCLStamp)) {
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

    @Override
    public void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        JavaKind elementKind = storeIndexed.elementKind();
        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        AddressNode address = createArrayAddress(graph, array, elementKind, storeIndexed.index());
        AbstractWriteNode memoryWrite = createMemWriteNode(elementKind, value, array, address, graph, storeIndexed);
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
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
        final int headerSize = getVMConfig().getArrayBaseOffset(field.getJavaKind());
        boolean areCoopsEnabled = (headerSize == 16);
        // if coops are used and the field is a not a primitive (primitive data is not compressed)
        if (areCoopsEnabled && !field.getJavaKind().isPrimitive()) {
            OCLDecompressedReadFieldNode decompressedNode = graph.add(new OCLDecompressedReadFieldNode(object, address, loadStamp));
            loadField.replaceAtUsages(decompressedNode);
            graph.replaceFixed(loadField, decompressedNode);
        } else {
            FieldLocationIdentity fieldLocationIdentity = new FieldLocationIdentity(field);
            ReadNode memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity, loadStamp, BarrierType.NONE, GPU_MEMORY_MODE));
            loadField.replaceAtUsages(memoryRead);
            graph.replaceFixed(loadField, memoryRead);
        }
    }

    @Override
    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;
        FieldLocationIdentity fieldLocationIdentity = new FieldLocationIdentity(field);
        WriteNode memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity, storeField.value(), BarrierType.NONE, GPU_MEMORY_MODE));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
    }

    private void lowerAtomicAddNode(AtomicAddNode atomicAdd, LoweringTool tool) {
        shouldNotReachHere("need to use builtin nodes");
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
            if (!callTarget.isStatic() && receiver.stamp(NodeView.DEFAULT) instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                ValueNode nonNullReceiver = createNullCheckedValue(receiver, invoke.asFixedNode(), tool);
                parameters.set(0, nonNullReceiver);
                receiver = nonNullReceiver;
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;

            StampPair returnStampPair = callTarget.returnStamp();
            Stamp returnStamp = returnStampPair.getTrustedStamp();
            if (returnStamp instanceof ObjectStamp) {
                ObjectStamp os = (ObjectStamp) returnStamp;
                ResolvedJavaType type = os.javaType(tool.getMetaAccess());
                OCLKind oclKind = OCLKind.fromResolvedJavaType(type);
                if (oclKind != OCLKind.ILLEGAL) {
                    returnStampPair = StampPair.createSingle(OCLStampFactory.getStampFor(oclKind));
                }
            }

            loweredCallTarget = graph.add(new TornadoDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), returnStampPair, signature, callTarget.targetMethod(),
                    HotSpotCallingConventionType.JavaCall, callTarget.invokeKind()));

            callTarget.replaceAndDelete(loweredCallTarget);
        }

    }

    private void lowerFloatConvertNode(FloatConvertNode floatConvert) {
        final StructuredGraph graph = floatConvert.graph(); // TODO should probably create a specific float-convert node?
        final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.stamp(NodeView.DEFAULT), floatConvert.getFloatConvert(), floatConvert.getValue()));
        floatConvert.replaceAtUsages(asFloat);
        floatConvert.safeDelete();
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

    public int arrayBaseOffset(JavaKind kind) {
        return metaAccess.getArrayBaseOffset(kind);
    }

    @Override
    public int arrayLengthOffset() {
        return vmConfig.arrayOopDescLengthOffset();
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        unimplemented();
        return null;
    }

    @Override
    protected ValueNode newCompressionNode(CompressionNode.CompressionOp op, ValueNode value) {
        unimplemented();
        return null;
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        return field.getOffset();
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        unimplemented("Create READ hub is not supported yet");
        return null;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor) {
        return null;
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    private AddressNode createArrayLocalAddress(StructuredGraph graph, ValueNode array, ValueNode index) {
        return graph.unique(new OffsetAddressNode(array, index));
    }

    private boolean isLocalIDNode(StoreIndexedNode storeIndexed) {
        // Either the node has as input a LocalArray or has a node which will be lowered
        // to a LocalArray
        Node nd = storeIndexed.inputs().first();
        InvokeNode node = nd.inputs().filter(InvokeNode.class).first();
        boolean willLowerToLocalArrayNode = node != null && "Direct#NewArrayNode.newArray".equals(node.callTarget().targetName()) && gpuSnippet;
        return (nd instanceof MarkLocalArray || willLowerToLocalArrayNode);
    }

    private boolean isLocalIDNode(LoadIndexedNode loadIndexedNode) {
        // Either the node has as input a LocalArray or has a node which will be lowered
        // to a LocalArray
        Node nd = loadIndexedNode.inputs().first();
        InvokeNode node = nd.inputs().filter(InvokeNode.class).first();
        boolean willLowerToLocalArrayNode = node != null && "Direct#NewArrayNode.newArray".equals(node.callTarget().targetName()) && gpuSnippet;
        return (nd instanceof MarkLocalArray || willLowerToLocalArrayNode);
    }

    private boolean isPrivateIDNode(StoreIndexedNode storeIndexed) {
        Node nd = storeIndexed.inputs().first();
        return (nd instanceof FixedArrayNode);
    }

    private boolean isPrivateIDNode(LoadIndexedNode loadIndexedNode) {
        Node nd = loadIndexedNode.inputs().first();
        return (nd instanceof FixedArrayNode);
    }

    private void lowerLocalNewArray(StructuredGraph graph, int length, NewArrayNonVirtualizableNode newArray) {
        LocalArrayNode localArrayNode;
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(OCLArchitecture.localSpace, newArray.elementType(), newLengthNode));
        newArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateNewArray(StructuredGraph graph, int size, NewArrayNonVirtualizableNode newArray) {
        FixedArrayNode fixedArrayNode;
        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(OCLArchitecture.privateSpace, newArray.elementType(), newLengthNode));
        newArray.replaceAtUsages(fixedArrayNode);
    }

    private AddressNode createArrayAccess(StructuredGraph graph, LoadIndexedNode loadIndexed, JavaKind elementKind) {
        AddressNode address;
        if (isLocalIDNode(loadIndexed) || isPrivateIDNode(loadIndexed)) {
            address = createArrayLocalAddress(graph, loadIndexed.array(), loadIndexed.index());
        } else {
            address = createArrayAddress(graph, loadIndexed.array(), elementKind, loadIndexed.index());
        }
        return address;
    }

    private AbstractWriteNode createMemWriteNode(JavaKind elementKind, ValueNode value, ValueNode array, AddressNode address, StructuredGraph graph, StoreIndexedNode storeIndexed) {
        AbstractWriteNode memoryWrite;
        if (isLocalIDNode(storeIndexed) || isPrivateIDNode(storeIndexed)) {
            address = createArrayLocalAddress(graph, array, storeIndexed.index());
        }
        ValueNode storeConvertValue = value;
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        if (!(valueStamp instanceof OCLStamp) || !((OCLStamp) valueStamp).getOCLKind().isVector()) {
            storeConvertValue = implicitStoreConvert(graph, elementKind, value);
        }
        memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), storeConvertValue, BarrierType.NONE, GPU_MEMORY_MODE));
        return memoryWrite;
    }
}
