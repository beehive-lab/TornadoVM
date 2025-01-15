package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.Constant;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

@NodeInfo
public class HalfFloatConstantNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<HalfFloatConstantNode> TYPE = NodeClass.create(HalfFloatConstantNode.class);

    @Input
    private ConstantNode halfFloatValue;

    public HalfFloatConstantNode(ConstantNode halfFloatValue) {
        super(TYPE, new HalfFloatStamp());
        this.halfFloatValue = halfFloatValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, new ConstantValue(LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_16), halfFloatValue.asConstant()));
    }
}
