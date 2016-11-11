package tornado.drivers.opencl.graal;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatConvertNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.DefaultJavaLoweringProvider;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.AtomicAddNode;
import tornado.drivers.opencl.graal.nodes.CastNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorStoreNode;
import tornado.graal.nodes.TornadoDirectCallTargetNode;
import tornado.runtime.TornadoVMConfig;

import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.INIT_LOCATION;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
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
        } else {
            super.lower(n, tool);
        }
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
            GuardingNode receiverNullCheck = null;
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                receiverNullCheck = createNullCheck(receiver, invoke.asNode(), tool);
                invoke.setGuard(receiverNullCheck);
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
//            if (InlineVTableStubs.getValue() && callTarget.invokeKind().isIndirect() && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {
//                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
//                ResolvedJavaType receiverType = invoke.getReceiverType();
//                if (hsMethod.isInVirtualMethodTable(receiverType)) {
//                    Kind wordKind = runtime.getTarget().wordKind;
//                    ValueNode hub = createReadHub(graph, receiver, receiverNullCheck);
//
//                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, hub, hsMethod, receiverType);
//                    // We use LocationNode.ANY_LOCATION for the reads that access the
//                    // compiled code entry as HotSpot does not guarantee they are final
//                    // values.
//                    int methodCompiledEntryOffset = runtime.getConfig().methodCompiledEntryOffset;
//                    ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, graph.unique(new ConstantLocationNode(any(), methodCompiledEntryOffset)), StampFactory.forKind(wordKind),
//                                    BarrierType.NONE));
//
//                    loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(),
//                                    CallingConvention.Type.JavaCall, callTarget.invokeKind()));
//
//                    graph.addBeforeFixed(invoke.asNode(), metaspaceMethod);
//                    graph.addAfterFixed(metaspaceMethod, compiledEntry);
//                }
//            }

            if (loweredCallTarget == null) {
                loweredCallTarget = graph.add(new TornadoDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, callTarget.targetMethod(), HotSpotCallingConventionType.JavaCall,
                        callTarget.invokeKind()));
            }

            callTarget.replaceAndDelete(loweredCallTarget);
        }

    }

    private void lowerFloatConvertNode(FloatConvertNode floatConvert, LoweringTool tool) {
        final StructuredGraph graph = floatConvert.graph();

        // TODO should probably create a specific floatconvert node?
        final CastNode asFloat = graph.addWithoutUnique(new CastNode((OCLKind) target.arch.getPlatformKind(floatConvert.stamp().getStackKind()), floatConvert.getValue()));

        floatConvert.replaceAtUsages(asFloat);

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

                    unimplemented("fixed array node is missing");
                    //final FixedArrayNode fixedArrayNode = graph.addWithoutUnique(new FixedArrayNode(newArray.elementType(), newLengthNode));
                    //newArray.replaceAtUsages(fixedArrayNode);
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

    @Override
    public LocationIdentity initLocationIdentity() {
        return INIT_LOCATION;
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

}
