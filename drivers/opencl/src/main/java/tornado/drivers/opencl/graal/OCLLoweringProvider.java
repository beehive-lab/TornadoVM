/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal;

import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.*;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.DivNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.*;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.AtomicAddNode;
import tornado.drivers.opencl.graal.nodes.CastNode;
import tornado.drivers.opencl.graal.nodes.FixedArrayNode;
import tornado.drivers.opencl.graal.nodes.OCLAtomicAdd;
import tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorStoreNode;
import tornado.graal.nodes.StoreAtomicIndexedNode;
import tornado.graal.nodes.TornadoDirectCallTargetNode;
import tornado.runtime.TornadoVMConfig;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLLoweringProvider extends DefaultJavaLoweringProvider {

    private final ConstantReflectionProvider constantReflection;
    private final TornadoVMConfig vmConfig;

    public OCLLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, ConstantReflectionProvider constantReflection, TornadoVMConfig vmConfig, OCLTargetDescription target) {
        super(metaAccess, foreignCalls, target);
        this.vmConfig = vmConfig;
        this.constantReflection = constantReflection;
//        super.initialize(providers, providers.getSnippetReflection());
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();

        if (n instanceof Invoke) {
            lowerInvoke((Invoke) n, tool, graph);
        } else if (n instanceof VectorLoadNode) {
            lowerVectorLoadNode((VectorLoadNode) n, tool);
        } else if (n instanceof VectorStoreNode) {
            lowerVectorStoreNode((VectorStoreNode) n, tool);
        } else if (n instanceof AbstractDeoptimizeNode || n instanceof UnwindNode || n instanceof RemNode) {
            /*
             * No lowering, we generate LIR directly for these nodes.
             */
        } else if (n instanceof FloatConvertNode) {
            lowerFloatConvertNode((FloatConvertNode) n, tool);
        } else if (n instanceof NewArrayNode) {
            lowerNewArrayNode((NewArrayNode) n, tool);
        } else if (n instanceof AtomicAddNode) {
            lowerAtomicAddNode((AtomicAddNode) n, tool);
        } else if (n instanceof LoadIndexedNode || n instanceof LoadIndexedVectorNode) {
            lowerLoadIndexedNode((LoadIndexedNode) n, tool);
        } else if (n instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) n, tool);
        } else if (n instanceof StoreAtomicIndexedNode) {
            lowerAtomicStoreIndexedNode((StoreAtomicIndexedNode) n, tool);
        } else if (n instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) n, tool);
        } else if (n instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) n, tool);
        } else if (n instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) n, tool);
        } else if (n instanceof IntegerDivRemNode) {
            lowerIntegerDivRemNode((IntegerDivRemNode) n, tool);
        } else {
            super.lower(n, tool);
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
    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool
    ) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();

        AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
        ReadNode arrayLengthRead = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE));
//        arrayLengthRead.setGuard(createNullCheck(array, arrayLengthNode, tool));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    @Override
    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool
    ) {
        StructuredGraph graph = loadIndexed.graph();
        JavaKind elementKind = loadIndexed.elementKind();

        Stamp loadStamp = loadIndexed.stamp();
        if (!(loadIndexed.stamp() instanceof OCLStamp)) {
            loadStamp = loadStamp(loadIndexed.stamp(), elementKind);
        }

        AddressNode address = createArrayAddress(graph, loadIndexed.array(), elementKind, loadIndexed.index());
        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE));
//        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        loadIndexed.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadIndexed, memoryRead);
    }
    
    protected void lowerAtomicStoreIndexedNode(StoreAtomicIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        
        JavaKind elementKind = storeIndexed.elementKind();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();

        AddressNode address = createArrayAddress(graph, array, elementKind, storeIndexed.index());
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, arrayStoreBarrierType(storeIndexed.elementKind())));
        //OCLAtomicAdd atomicADD = graph.add(new OCLAtomicAdd(address, null, value));
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    @Override
    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool
    ) {
        StructuredGraph graph = storeIndexed.graph();

//        GuardingNode[] nullCheckReturn = new GuardingNode[1];
//        PiNode pi = getBoundsCheckedIndex(storeIndexed, tool, nullCheckReturn);
//        ValueNode checkedIndex;
//        GuardingNode boundsCheck;
//        if (pi == null) {
//            checkedIndex = storeIndexed.index();
//            boundsCheck = null;
//        } else {
//            checkedIndex = pi;
//            boundsCheck = pi.getGuard();
//        }
        JavaKind elementKind = storeIndexed.elementKind();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
//        LogicNode condition = null;
//        if (elementKind == JavaKind.Object && !StampTool.isPointerAlwaysNull(value)) {
//            /*
//             * Array store check.
//             */
//            TypeReference arrayType = StampTool.typeReferenceOrNull(array);
//            if (arrayType != null && arrayType.isExact()) {
//                ResolvedJavaType elementType = arrayType.getType().getComponentType();
//                if (!elementType.isJavaLangObject()) {
//                    TypeReference typeReference = TypeReference.createTrusted(storeIndexed.graph().getAssumptions(), elementType);
//                    LogicNode typeTest = graph.addOrUniqueWithInputs(InstanceOfNode.create(typeReference, value));
//                    condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, GraalDirectives.UNLIKELY_PROBABILITY);
//                }
//            } else {
//                /*
//                 * The guard on the read hub should be the null check of the
//                 * array that was introduced earlier.
//                 */
//                GuardingNode nullCheck = nullCheckReturn[0];
//                assert nullCheckReturn[0] != null || createNullCheck(array, storeIndexed, tool) == null;
//                ValueNode arrayClass = createReadHub(graph, graph.unique(new PiNode(array, (ValueNode) nullCheck)), tool);
//                ValueNode componentHub = createReadArrayComponentHub(graph, arrayClass, storeIndexed);
//                LogicNode typeTest = graph.unique(InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), componentHub, value, false));
//                condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, GraalDirectives.UNLIKELY_PROBABILITY);
//            }
//        }

        AddressNode address = createArrayAddress(graph, array, elementKind, storeIndexed.index());
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value,
                arrayStoreBarrierType(storeIndexed.elementKind())));
//        memoryWrite.setGuard(boundsCheck);
//        if (condition != null) {
//            GuardingNode storeCheckGuard = tool.createGuard(storeIndexed, condition, DeoptimizationReason.ArrayStoreException, DeoptimizationAction.InvalidateReprofile);
//            memoryWrite.setStoreCheckGuard(storeCheckGuard);
//        }
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    @Override
    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool
    ) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        Stamp loadStamp = loadStamp(loadField.stamp(), field.getJavaKind());

        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();

        ReadNode memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity(field), loadStamp, fieldLoadBarrierType(field)));
//        ValueNode readValue = implicitLoadConvert(graph, field.getJavaKind(), memoryRead);
        loadField.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadField, memoryRead);

//        memoryRead.setGuard(createNullCheck(object, memoryRead, tool));
//
//        if (loadField.isVolatile()) {
//            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
//            graph.addBeforeFixed(memoryRead, preMembar);
//            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
//            graph.addAfterFixed(memoryRead, postMembar);
//        }
    }

    @Override
    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool
    ) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
//        ValueNode value = implicitStoreConvert(graph, storeField.field().getJavaKind(), storeField.value());
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;

        WriteNode memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity(field), storeField.value(), fieldStoreBarrierType(storeField.field())));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
//        memoryWrite.setGuard(createNullCheck(object, memoryWrite, tool));

//        if (storeField.isVolatile()) {
//            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
//            graph.addBeforeFixed(memoryWrite, preMembar);
//            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
//            graph.addAfterFixed(memoryWrite, postMembar);
//        }
    }

    private void lowerAtomicAddNode(AtomicAddNode atomicAdd, LoweringTool tool) {
        shouldNotReachHere("need to use builtin nodes");
//        IndexedLocationNode location = createArrayLocation(atomicAdd.graph(), atomicAdd.elementKind(), atomicAdd.index(), false);
//        final AtomicWriteNode atomicWrite = new AtomicWriteNode(OCLBinaryIntrinsic.ATOMIC_ADD, atomicAdd.array(), atomicAdd.value(), location);
//
//        atomicAdd.graph().add(atomicWrite);
//
//        atomicAdd.graph().replaceFixedWithFixed(atomicAdd, atomicWrite);

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

            if (loweredCallTarget == null) {
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

                loweredCallTarget = graph.add(new TornadoDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), returnStampPair, signature, callTarget.targetMethod(), HotSpotCallingConventionType.JavaCall,
                        callTarget.invokeKind()));
            }

            callTarget.replaceAndDelete(loweredCallTarget);
        }

    }

    private void lowerFloatConvertNode(FloatConvertNode floatConvert, LoweringTool tool) {
        final StructuredGraph graph = floatConvert.graph();
        // TODO should probably create a specific floatconvert node?

        final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.stamp(), floatConvert.getFloatConvert(), floatConvert.getValue()));

        floatConvert.replaceAtUsages(asFloat);
        floatConvert.safeDelete();

    }

    private void lowerVectorStoreNode(VectorStoreNode vectorStore, LoweringTool tool) {
        StructuredGraph graph = vectorStore.graph();
        JavaKind elementKind = vectorStore.elementKind();
//        LocationNode location = createArrayLocation(graph, elementKind, vectorStore.index(), false);
        AddressNode address = createArrayAddress(graph, vectorStore.array(), elementKind, vectorStore.index());

        WriteNode vectorWrite = graph.addWithoutUnique(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), vectorStore.value(), BarrierType.PRECISE));
        //VectorWriteNode vectorWrite = graph.addOrUnique(new VectorWriteNode(vectorStore.array(), vectorStore.value(), location, BarrierType.PRECISE));

        graph.replaceFixed(vectorStore, vectorWrite);

    }

    private void lowerVectorLoadNode(VectorLoadNode vectorLoad, LoweringTool tool) {
        StructuredGraph graph = vectorLoad.graph();
        JavaKind elementKind = vectorLoad.elementKind();
        AddressNode address = createArrayAddress(graph, vectorLoad.array(), elementKind, vectorLoad.index());

//        LocationNode location = createArrayLocation(graph, elementKind, vectorLoad.index(), false);
        ReadNode vectorRead = graph.addWithoutUnique(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), vectorLoad.stamp(), BarrierType.NONE));
//        VectorReadNode vectorRead = graph.addOrUnique(new VectorReadNode(vectorLoad.vectorKind(), vectorLoad.array(), location, BarrierType.NONE));
        //vectorLoad.replaceAtUsages(vectorRead);
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

                    final ConstantNode newLengthNode = ConstantNode.forInt(size, graph);

                    final FixedArrayNode fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(newArray.elementType(), newLengthNode));
                    newArray.replaceAtUsages(fixedArrayNode);
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
    protected ValueNode createReadArrayComponentHub(StructuredGraph arg0, ValueNode arg1,
            FixedNode arg2) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        unimplemented();
        return null;
    }

//    @Override
//    public LocationIdentity initLocationIdentity() {
//        return INIT_LOCATION;
//    }
    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

}
