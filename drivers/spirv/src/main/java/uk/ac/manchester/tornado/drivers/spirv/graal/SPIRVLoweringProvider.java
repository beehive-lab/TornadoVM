package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

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
    public int fieldOffset(ResolvedJavaField field) {
        return 0;
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field) {
        return null;
    }

    @Override
    public int arrayLengthOffset() {
        return 0;
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        return null;
    }

    @Override
    protected ValueNode newCompressionNode(CompressionNode.CompressionOp op, ValueNode value) {
        return null;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        return null;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        return null;
    }

    @Override
    public Integer smallestCompareWidth() {
        return null;
    }

    @Override
    public boolean supportsBulkZeroing() {
        return false;
    }
}
