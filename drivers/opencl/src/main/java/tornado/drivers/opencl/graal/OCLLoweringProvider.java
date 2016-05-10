package tornado.drivers.opencl.graal;

import tornado.drivers.opencl.graal.nodes.CastNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorReadNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorStoreNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorWriteNode;
import tornado.graal.nodes.TornadoDirectCallTargetNode;

import com.oracle.graal.api.code.ForeignCallsProvider;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.JavaType;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LocationIdentity;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.ResolvedJavaField;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.HotSpotVMConfig;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaField;
import com.oracle.graal.nodes.AbstractDeoptimizeNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.LoweredCallTargetNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatConvertNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.LocationNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.DefaultJavaLoweringProvider;
import com.oracle.graal.api.code.CallingConvention;

public class OCLLoweringProvider extends DefaultJavaLoweringProvider {
	
	protected final HotSpotGraalRuntimeProvider runtime;
	protected final ForeignCallsProvider foreignCalls;
	
	public OCLLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, TargetDescription target){
		super(metaAccess,target);
		this.runtime = runtime;
		this.foreignCalls = foreignCalls;
	}
	
	public void initialize(HotSpotProviders providers, HotSpotVMConfig config){
		super.initialize(providers, providers.getSnippetReflection());
	}

	 @Override
	    public void lower(Node n, LoweringTool tool) {
	        StructuredGraph graph = (StructuredGraph) n.graph();
	  
	        	if(n instanceof Invoke){
	        		lowerInvoke((Invoke) n, tool, graph);
	        	} else if(n instanceof VectorLoadNode){
	        		lowerVectorLoadNode((VectorLoadNode) n, tool);
	        	} else if(n instanceof VectorStoreNode){
	        		lowerVectorStoreNode((VectorStoreNode) n, tool);
	        	} else if (n instanceof AbstractDeoptimizeNode || n instanceof UnwindNode || n instanceof RemNode) {
	                /* No lowering, we generate LIR directly for these nodes. */
	            } else if (n instanceof FloatConvertNode ) {
	            	lowerFloatConvertNode((FloatConvertNode) n, tool);
	            } else {
	        		super.lower(n, tool);
	        	}
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
                loweredCallTarget = graph.add(new TornadoDirectCallTargetNode(parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall,
                                callTarget.invokeKind()));
            }
            
            callTarget.replaceAndDelete(loweredCallTarget);
        }
		
	}

	private void lowerFloatConvertNode(FloatConvertNode floatConvert, LoweringTool tool) {
		final StructuredGraph graph = floatConvert.graph();
		final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.getKind(),floatConvert.getValue()));
		
		floatConvert.replaceAtUsages(asFloat);
		
	}

	private void lowerVectorStoreNode(VectorStoreNode vectorStore, LoweringTool tool) {
		StructuredGraph graph = vectorStore.graph();
        Kind elementKind = vectorStore.elementKind();
        LocationNode location = createArrayLocation(graph, elementKind, vectorStore.index(), false);
        
        VectorWriteNode vectorWrite = graph.addOrUnique(new VectorWriteNode(vectorStore.vectorKind(),vectorStore.array(),vectorStore.value(),location,BarrierType.PRECISE));
        
        graph.replaceFixed(vectorStore, vectorWrite);
		
	}

	private void lowerVectorLoadNode(VectorLoadNode vectorLoad, LoweringTool tool) {
		StructuredGraph graph = vectorLoad.graph();
        Kind elementKind = vectorLoad.elementKind();
        LocationNode location = createArrayLocation(graph, elementKind, vectorLoad.index(), false);
        
        VectorReadNode vectorRead = graph.addOrUnique(new VectorReadNode(vectorLoad.vectorKind(),vectorLoad.array(),location,BarrierType.NONE));
        //vectorLoad.replaceAtUsages(vectorRead);
        graph.replaceFixed(vectorLoad, vectorRead);
	}

	@Override
	protected int arrayBaseOffset(Kind kind) {
		return runtime.getArrayBaseOffset(kind);
	}

	@Override
	protected int arrayLengthOffset() {
		return runtime.getConfig().arrayLengthOffset;
	}
	
	@Override
	protected int fieldOffset(ResolvedJavaField f) {
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
	protected ValueNode createReadHub(StructuredGraph arg0, ValueNode arg1, GuardingNode arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected LocationIdentity initLocationIdentity() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ValueNode staticFieldBase(StructuredGraph arg0, ResolvedJavaField arg1) {
		// TODO Auto-generated method stub
		return null;
	}

}
