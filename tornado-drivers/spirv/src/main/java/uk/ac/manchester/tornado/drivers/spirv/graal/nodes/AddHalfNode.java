package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;

@NodeInfo
public class AddHalfNode extends ValueNode implements ArithmeticLIRLowerable {
    public static final NodeClass<AddHalfNode> TYPE = NodeClass.create(AddHalfNode.class);

    @Input
    private ValueNode x;

    @Input
    private ValueNode y;

    public AddHalfNode(ValueNode x, ValueNode y) {
        super(TYPE, new HalfFloatStamp());
        this.x = x;
        this.y = y;
    }

    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool generator) {
        Value inputX = builder.operand(x);
        Value inputY = builder.operand(y);

        SPIRVArithmeticTool spirvArithmeticTool = (SPIRVArithmeticTool) generator;
        builder.setResult(this, spirvArithmeticTool.emitAnd(inputX, inputY));
    }
}
