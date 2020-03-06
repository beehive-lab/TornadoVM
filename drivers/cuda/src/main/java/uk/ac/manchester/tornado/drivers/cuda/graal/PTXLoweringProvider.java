package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.java.*;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.calc.DivNode;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;

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
        return field.getJavaKind();
    }

    @Override
    public int fieldOffset(ResolvedJavaField field) {
        return field.getOffset();
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

    @Override
    public void lower(Node node, LoweringTool tool) {
        if (node instanceof IntegerDivRemNode) {
            lowerIntegerDivRemNode((IntegerDivRemNode) node);
        } else {
            super.lower(node, tool);
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
}
