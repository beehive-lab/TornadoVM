package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkFloatingPointIntrinsicsNode;

@NodeInfo(shortName = "SPIRV-FMA")
public class SPIRVFMANode extends FloatingNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<SPIRVFMANode> TYPE = NodeClass.create(SPIRVFMANode.class);

    @Input
    protected ValueNode x;
    @Input
    protected ValueNode y;
    @Input
    protected ValueNode z;

    public SPIRVFMANode(ValueNode x, ValueNode y, ValueNode z) {
        super(TYPE, StampFactory.forKind(x.getStackKind()));
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        Value op1 = builder.operand(x);
        Value op2 = builder.operand(y);
        Value op3 = builder.operand(z);

        SPIRVArithmeticTool spirvArithmeticTool = (SPIRVArithmeticTool) gen;
        builder.setResult(this, spirvArithmeticTool.emitFMAInstruction(op1, op2, op3));
    }

    @Override
    public String getOperation() {
        return "FMA";
    }
}
