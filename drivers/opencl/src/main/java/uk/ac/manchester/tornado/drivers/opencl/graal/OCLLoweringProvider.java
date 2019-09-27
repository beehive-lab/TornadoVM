/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.Iterator;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.DivNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.SnippetCounter;

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
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode.ATOMIC_OPERATION;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.AtomicAddNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.CastNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorLoadNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorStoreNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.ReduceCPUSnippets;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.ReduceGPUSnippets;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoDirectCallTargetNode;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkLocalArray;

public class OCLLoweringProvider extends DefaultJavaLoweringProvider {

    private static final boolean USE_ATOMICS = false;
    private final ConstantReflectionProvider constantReflection;
    private final TornadoVMConfig vmConfig;
    private static boolean isAGPUSnippet = false;

    private ReduceGPUSnippets.Templates GPUReduceSnippets;
    private ReduceCPUSnippets.Templates CPUReduceSnippets;

    public OCLLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, ConstantReflectionProvider constantReflection, TornadoVMConfig vmConfig, OCLTargetDescription target) {
        super(metaAccess, foreignCalls, target);
        this.vmConfig = vmConfig;
        this.constantReflection = constantReflection;
    }

    @Override
    public void initialize(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers, SnippetReflectionProvider snippetReflection) {
        super.initialize(options, factory, providers, snippetReflection);
        initializeSnippets(options, factory, providers, snippetReflection);
    }

    private void initializeSnippets(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers, SnippetReflectionProvider snippetReflection) {
        this.GPUReduceSnippets = new ReduceGPUSnippets.Templates(options, providers, snippetReflection, target);
        this.CPUReduceSnippets = new ReduceCPUSnippets.Templates(options, providers, snippetReflection, target);
    }

    @Override
    public void lower(Node node, LoweringTool tool) {

        if (node instanceof Invoke) {
            lowerInvoke((Invoke) node, tool, (StructuredGraph) node.graph());
        } else if (node instanceof VectorLoadNode) {
            lowerVectorLoadNode((VectorLoadNode) node, tool);
        } else if (node instanceof VectorStoreNode) {
            lowerVectorStoreNode((VectorStoreNode) node, tool);
        } else if (node instanceof AbstractDeoptimizeNode || node instanceof UnwindNode || node instanceof RemNode) {
            /*
             * No lowering, we currently generate LIR directly for these nodes.
             */
        } else if (node instanceof FloatConvertNode) {
            lowerFloatConvertNode((FloatConvertNode) node, tool);
        } else if (node instanceof NewArrayNode) {
            lowerNewArrayNode((NewArrayNode) node, tool);
        } else if (node instanceof AtomicAddNode) {
            lowerAtomicAddNode((AtomicAddNode) node, tool);
        } else if (node instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) node, tool);
        } else if (node instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) node, tool);
        } else if (node instanceof StoreAtomicIndexedNode) {
            lowerStoreAtomicsReduction(node, tool);
        } else if (node instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) node, tool);
        } else if (node instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) node, tool);
        } else if (node instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) node, tool);
        } else if (node instanceof IntegerDivRemNode) {
            lowerIntegerDivRemNode((IntegerDivRemNode) node, tool);
        } else {
            super.lower(node, tool);
        }
    }

    private void lowerReduceSnippets(StoreAtomicIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        ValueNode startIndexNode = storeIndexed.getStartNode();

        // Find Get Global ID node and Global Size;
        GlobalThreadIdNode oclIdNode = graph.getNodes().filter(GlobalThreadIdNode.class).first();
        GlobalThreadSizeNode oclGlobalSize = graph.getNodes().filter(GlobalThreadSizeNode.class).first();

        ValueNode threadID = null;
        Iterator<Node> usages = oclIdNode.usages().iterator();

        boolean cpuScheduler = false;

        while (usages.hasNext()) {
            Node n = usages.next();

            // GPU SCHEDULER
            if (n instanceof PhiNode) {
                isAGPUSnippet = true;
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
            CPUReduceSnippets.lower(storeIndexed, threadID, oclIdNode, startIndexNode, tool);
        } else {
            GPUReduceSnippets.lower(storeIndexed, threadID, oclGlobalSize, tool);
        }
    }

    private void lowerStoreAtomicsReduction(Node node, LoweringTool tool) {
        if (USE_ATOMICS) {
            lowerAtomicStoreIndexedNode((StoreAtomicIndexedNode) node, tool);
        } else {
            lowerReduceSnippets((StoreAtomicIndexedNode) node, tool);
        }
    }

    private void lowerIntegerDivRemNode(IntegerDivRemNode integerDivRemNode, LoweringTool tool) {
        StructuredGraph graph = integerDivRemNode.graph();
        switch (integerDivRemNode.getOp()) {
            case DIV:
                DivNode div = graph.addOrUnique(new DivNode(integerDivRemNode.getX(), integerDivRemNode.getY()));
                graph.replaceFixedWithFloating(integerDivRemNode, div);
                break;
            case REM:
                RemNode rem = graph.addOrUnique(new RemNode(integerDivRemNode.getX(), integerDivRemNode.getY()));
                graph.replaceFixedWithFloating(integerDivRemNode, rem);
                break;
        }
    }

    @Override
    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();

        AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
        ReadNode arrayLengthRead = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    @Override
    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        JavaKind elementKind = loadIndexed.elementKind();
        AddressNode address;

        Stamp loadStamp = loadIndexed.stamp();
        if (!(loadIndexed.stamp() instanceof OCLStamp)) {
            loadStamp = loadStamp(loadIndexed.stamp(), elementKind);
        }

        if (isLocalIdNode(loadIndexed)) {
            address = createArrayLocalAddress(graph, loadIndexed.array(), loadIndexed.index());
        } else {
            address = createArrayAddress(graph, loadIndexed.array(), elementKind, loadIndexed.index());
        }
        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE));
        loadIndexed.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    private void lowerAtomicStoreIndexedNode(StoreAtomicIndexedNode storeIndexed, LoweringTool tool) {

        StructuredGraph graph = storeIndexed.graph();
        JavaKind elementKind = storeIndexed.elementKind();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        ValueNode accumulator = storeIndexed.getAccumulator();

        ATOMIC_OPERATION operation = ATOMIC_OPERATION.CUSTOM;
        if (value instanceof OCLReduceAddNode) {
            operation = ATOMIC_OPERATION.ADD;
        } else if (value instanceof OCLReduceSubNode) {
            operation = ATOMIC_OPERATION.SUB;
        } else if (value instanceof OCLReduceMulNode) {
            operation = ATOMIC_OPERATION.MUL;
        }

        AddressNode address = createArrayAddress(graph, array, elementKind, storeIndexed.index());
        OCLWriteAtomicNode memoryWrite = graph.add(new OCLWriteAtomicNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, arrayStoreBarrierType(storeIndexed.elementKind()),
                accumulator, accumulator.stamp(), storeIndexed.elementKind(), operation));
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    private boolean isSimpleCharOrShort(JavaKind elementKind, ValueNode value) {
        return (elementKind == JavaKind.Char && value.getStackKind() != JavaKind.Object) || (elementKind == JavaKind.Short && value.getStackKind() != JavaKind.Object);
    }

    @Override
    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        JavaKind elementKind = storeIndexed.elementKind();
        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        AddressNode address = createArrayAddress(graph, array, elementKind, storeIndexed.index());

        AbstractWriteNode memoryWrite = null;
        if (isSimpleCharOrShort(elementKind, value)) {
            // XXX: This call is due to an error in Graal when storing a
            // variable of type char or short. In future integrations with JVMCI
            // and Graal, this issue is completely solved.
            memoryWrite = graph.add(new OCLWriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, arrayStoreBarrierType(storeIndexed.elementKind()), elementKind));
        } else if (isLocalIdNode(storeIndexed)) {
            address = createArrayLocalAddress(graph, array, storeIndexed.index());
            memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, arrayStoreBarrierType(storeIndexed.elementKind())));
        } else {
            memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, arrayStoreBarrierType(storeIndexed.elementKind())));
        }

        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    @Override
    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        Stamp loadStamp = loadStamp(loadField.stamp(), field.getJavaKind());
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();
        ReadNode memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity(field), loadStamp, fieldLoadBarrierType(field)));
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
        WriteNode memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity(field), storeField.value(), fieldStoreBarrierType(storeField.field())));
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
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                ValueNode nonNullReceiver = createNullCheckedValue(receiver, invoke.asNode(), tool);
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

    private void lowerFloatConvertNode(FloatConvertNode floatConvert, LoweringTool tool) {
        final StructuredGraph graph = floatConvert.graph();
        // TODO should probably create a specific float-convert node?

        final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.stamp(), floatConvert.getFloatConvert(), floatConvert.getValue()));
        floatConvert.replaceAtUsages(asFloat);
        floatConvert.safeDelete();
    }

    private void lowerVectorStoreNode(VectorStoreNode vectorStore, LoweringTool tool) {
        StructuredGraph graph = vectorStore.graph();
        JavaKind elementKind = vectorStore.elementKind();
        AddressNode address = createArrayAddress(graph, vectorStore.array(), elementKind, vectorStore.index());
        WriteNode vectorWrite = graph.addWithoutUnique(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), vectorStore.value(), BarrierType.PRECISE));
        graph.replaceFixed(vectorStore, vectorWrite);

    }

    private void lowerVectorLoadNode(VectorLoadNode vectorLoad, LoweringTool tool) {
        StructuredGraph graph = vectorLoad.graph();
        JavaKind elementKind = vectorLoad.elementKind();
        AddressNode address = createArrayAddress(graph, vectorLoad.array(), elementKind, vectorLoad.index());
        ReadNode vectorRead = graph.addWithoutUnique(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), vectorLoad.stamp(), BarrierType.NONE));
        graph.replaceFixed(vectorLoad, vectorRead);
    }

    private void lowerNewArrayNode(NewArrayNode newArray, LoweringTool tool) {
        final StructuredGraph graph = newArray.graph();
        final ValueNode firstInput = newArray.length();
        if (firstInput instanceof ConstantNode) {
            if (newArray.dimensionCount() == 1) {

                final ConstantNode lengthNode = (ConstantNode) firstInput;
                if (lengthNode.getValue() instanceof PrimitiveConstant) {
                    final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                    ResolvedJavaType elementType = newArray.elementType();
                    JavaKind elementKind = elementType.getJavaKind();
                    final int offset = arrayBaseOffset(elementKind);
                    final int size = offset + (elementKind.getByteCount() * length);
                    if (isAGPUSnippet) {
                        LocalArrayNode localArrayNode;
                        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
                        localArrayNode = graph.addWithoutUnique(new LocalArrayNode(OCLArchitecture.lp, newArray.elementType(), newLengthNode));
                        newArray.replaceAtUsages(localArrayNode);
                    } else {
                        FixedArrayNode fixedArrayNode;
                        final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);
                        fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(OCLArchitecture.hp, newArray.elementType(), newLengthNode));
                        newArray.replaceAtUsages(fixedArrayNode);
                    }
                    newArray.clearInputs();
                    GraphUtil.unlinkFixedNode(newArray);
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

    @Override
    public int arrayBaseOffset(JavaKind kind) {
        return getArrayBaseOffset(kind);
    }

    @Override
    public int arrayLengthOffset() {
        return vmConfig.arrayOopDescLengthOffset();
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        return field.offset();
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph arg0, ValueNode arg1, FixedNode arg2) {
        return null;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        unimplemented("Create READ hub not supported yet");
        return null;
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    public AddressNode createArrayLocalAddress(StructuredGraph graph, ValueNode array, ValueNode index) {

        return (AddressNode) graph.unique(new OffsetAddressNode(array, index));

    }

    public boolean isLocalIdNode(StoreIndexedNode storeIndexed) {
        Node nd = storeIndexed.inputs().first().asNode();
        if (nd instanceof MarkLocalArray) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isLocalIdNode(LoadIndexedNode loadIndexedNode) {
        Node nd = loadIndexedNode.inputs().first().asNode();
        if (nd instanceof MarkLocalArray) {
            return true;
        } else {
            return false;
        }
    }
}
