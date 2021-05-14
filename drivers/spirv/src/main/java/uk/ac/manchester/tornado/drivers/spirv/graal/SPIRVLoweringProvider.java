package uk.ac.manchester.tornado.drivers.spirv.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.type.StampTool;
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
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.CastNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.graal.nodes.GetGroupIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoDirectCallTargetNode;

public class SPIRVLoweringProvider extends DefaultJavaLoweringProvider {

    private ConstantReflectionProvider constantReflectionProvider;
    private TornadoVMConfig vmConfig;

    public SPIRVLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
            MetaAccessExtensionProvider metaAccessExtensionProvider, ConstantReflectionProvider constantReflectionProvider, TornadoVMConfig vmConfig, SPIRVTargetDescription target,
            boolean useCompressedOops) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, useCompressedOops);
        this.constantReflectionProvider = constantReflectionProvider;
        this.vmConfig = vmConfig;
    }

    @Override
    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> debugHandlersFactories, SnippetCounter.Group.Factory factory, Providers providers,
            SnippetReflectionProvider snippetReflection) {
        super.initialize(options, debugHandlersFactories, factory, providers, snippetReflection);
        initializeSnippets(options, debugHandlersFactories, factory, providers, snippetReflection);
    }

    private void initializeSnippets(OptionValues options, Iterable<DebugHandlersFactory> debugHandlersFactories, SnippetCounter.Group.Factory factory, Providers providers,
            SnippetReflectionProvider snippetReflection) {

    }

    @Override
    public void lower(Node node, LoweringTool tool) {
        SPIRVLogger.trace("Lowering node: %s\n", node);
        if (node instanceof Invoke) {
            lowerInvoke((Invoke) node, tool, (StructuredGraph) node.graph());
        } else if (node instanceof AbstractDeoptimizeNode || node instanceof UnwindNode || node instanceof RemNode) {
            /*
             * No lowering, we currently generate LIR directly for these nodes.
             */
        } else if (node instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) node, tool);
        } else if (node instanceof InstanceOfNode) {
            // ignore
        } else if (node instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) node, tool);
        } else if (node instanceof FloatConvertNode) {
            lowerFloatConvertNode((FloatConvertNode) node);
        } else if (node instanceof GetGroupIdFixedWithNextNode) {
            lowerGetGroupIdNode((GetGroupIdFixedWithNextNode) node);
        }

    }

    private void lowerGetGroupIdNode(GetGroupIdFixedWithNextNode getGroupIdNode) {
        StructuredGraph graph = getGroupIdNode.graph();
        GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(ConstantNode.forInt(getGroupIdNode.getDimension(), graph)));
        graph.replaceFixedWithFloating(getGroupIdNode, groupIdNode);
    }

    private void lowerFloatConvertNode(FloatConvertNode floatConvert) {
        final StructuredGraph graph = floatConvert.graph();
        final CastNode asFloat = graph.addWithoutUnique(new CastNode(floatConvert.stamp(NodeView.DEFAULT), floatConvert.getFloatConvert(), floatConvert.getValue()));
        floatConvert.replaceAtUsages(asFloat);
        floatConvert.safeDelete();
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke;
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = null;
            if (parameters.size() > 0) {
                receiver = parameters.get(0);
            }

            if (receiver != null && !callTarget.isStatic() && receiver.stamp(NodeView.DEFAULT) instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                ValueNode nonNullReceiver = createNullCheckedValue(receiver, invoke.asNode(), tool);
                parameters.set(0, nonNullReceiver);
            }

            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());
            LoweredCallTargetNode loweredCallTarget;
            StampPair returnStampPair = callTarget.returnStamp();
            Stamp returnStamp = returnStampPair.getTrustedStamp();
            if (returnStamp instanceof ObjectStamp) {
                ObjectStamp os = (ObjectStamp) returnStamp;
                ResolvedJavaType type = os.javaType(tool.getMetaAccess());
                SPIRVKind kind = SPIRVKind.fromResolvedJavaType(type);
                if (kind != SPIRVKind.ILLEGAL) {
                    returnStampPair = StampPair.createSingle(SPIRVStampFactory.getStampFor(kind));
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
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
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
    public boolean supportsBulkZeroing() {
        unimplemented("SPIRVLoweringProvider::supportsBulkZeroing unimplemented");
        return false;
    }

    @Override
    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {

    }

    private AddressNode createArrayAccess(StructuredGraph graph, LoadIndexedNode loadIndexed, JavaKind elementKind) {
        AddressNode address;
        System.out.println("Pending Local Memory");
        address = createArrayAddress(graph, loadIndexed.array(), elementKind, loadIndexed.index());
        return address;
    }

    @Override
    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        JavaKind elementKind = loadIndexed.elementKind();
        AddressNode address;

        Stamp loadStamp = loadIndexed.stamp(NodeView.DEFAULT);
        if (!(loadIndexed.stamp(NodeView.DEFAULT) instanceof OCLStamp)) {
            loadStamp = loadStamp(loadIndexed.stamp(NodeView.DEFAULT), elementKind, false);
        }
        address = createArrayAccess(graph, loadIndexed, elementKind);
        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, OnHeapMemoryAccess.BarrierType.NONE));
        loadIndexed.replaceAtUsages(memoryRead);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    private AbstractWriteNode createMemWriteNode(JavaKind elementKind, ValueNode value, ValueNode array, AddressNode address, StructuredGraph graph, StoreIndexedNode storeIndexed) {
        AbstractWriteNode memoryWrite;
        memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), value, OnHeapMemoryAccess.BarrierType.NONE));
        return memoryWrite;
    }

    @Override
    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        JavaKind elementKind = storeIndexed.elementKind();
        ValueNode valueToStore = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        ValueNode index = storeIndexed.index();
        AddressNode address = createArrayAddress(graph, array, elementKind, index);
        AbstractWriteNode memoryWrite = createMemWriteNode(elementKind, valueToStore, array, address, graph, storeIndexed);
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    @Override
    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {

    }

    @Override
    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {

    }
}
