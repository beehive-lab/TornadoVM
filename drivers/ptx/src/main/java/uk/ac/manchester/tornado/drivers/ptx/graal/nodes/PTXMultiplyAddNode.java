package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;

@NodeInfo(shortName = "MulAdd")
public class PTXMultiplyAddNode extends FloatingNode implements ArithmeticLIRLowerable {
    public static final NodeClass<PTXMultiplyAddNode> TYPE = NodeClass.create(PTXMultiplyAddNode.class);

    @Input
    protected ValueNode x;
    @Input
    protected ValueNode y;
    @Input
    protected ValueNode z;

    public PTXMultiplyAddNode(ValueNode x, ValueNode y, ValueNode z) {
        super(TYPE,  StampFactory.forKind(x.getStackKind()));

        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        Value op1 = builder.operand(x);
        Value op2 = builder.operand(y);
        Value op3 = builder.operand(z);

        PTXArithmeticTool arithmetic = (PTXArithmeticTool) gen;
        builder.setResult(this, arithmetic.emitMultiplyAdd(op1, op2, op3));
    }
}
