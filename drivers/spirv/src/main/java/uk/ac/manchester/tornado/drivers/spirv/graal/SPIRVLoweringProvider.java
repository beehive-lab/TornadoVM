package uk.ac.manchester.tornado.drivers.spirv.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.SnippetCounter;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import uk.ac.manchester.tornado.drivers.opencl.graal.snippets.ReduceGPUSnippets;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

public class SPIRVLoweringProvider extends DefaultJavaLoweringProvider {

    private ConstantReflectionProvider constantReflectionProvider;
    private TornadoVMConfig vmConfig;

    private ReduceGPUSnippets.Templates GPUReduceSnippets;

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
        this.GPUReduceSnippets = new ReduceGPUSnippets.Templates(options, debugHandlersFactories, providers, snippetReflection, target);
    }

    @Override
    public void lower(Node node, LoweringTool tool) {
        System.out.println("Lowering node: " + node);
        if (node instanceof AbstractDeoptimizeNode || node instanceof UnwindNode || node instanceof RemNode) {
            /*
             * No lowering, we currently generate LIR directly for these nodes.
             */
        } else if (node instanceof InstanceOfNode) {
            // ignore
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

    @Override
    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {

    }

    @Override
    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {

    }

    @Override
    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {

    }

    @Override
    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {

    }
}
