package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXLoweringProvider extends DefaultJavaLoweringProvider {

    private TornadoVMConfig vmConfig;

    public PTXLoweringProvider(MetaAccessProvider metaAccess,
                               ForeignCallsProvider foreignCalls,
                               TargetDescription target,
                               boolean useCompressedOops,
                               TornadoVMConfig vmConfig) {
        super(metaAccess, foreignCalls, target, useCompressedOops);
        this.vmConfig = vmConfig;
    }

    @Override
    protected JavaKind getStorageKind(ResolvedJavaField field) {
        unimplemented();
        return null;
    }

    @Override
    public int fieldOffset(ResolvedJavaField field) {
        unimplemented();
        return 0;
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field) {
        unimplemented();
        return null;
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
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        unimplemented();
        return null;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        unimplemented();
        return null;
    }

    @Override
    public Integer smallestCompareWidth() {
        // For now don't use this optimization.
        return null;
    }

    @Override
    public boolean supportsBulkZeroing() {
        unimplemented();
        return false;
    }
}
