package uk.ac.manchester.tornado.graal.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "REDUCE(*)", cycles = CYCLES_2)
public class OCLReduceMulNode extends MulNode {

    public static final NodeClass<OCLReduceMulNode> TYPE = NodeClass.create(OCLReduceMulNode.class);

    public OCLReduceMulNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool tool, ArithmeticLIRGeneratorTool gen) {

        Value op1 = tool.operand(getX());
        Value op2 = tool.operand(getY());

        if (shouldSwapInputs(tool)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        Value resultAdd = gen.emitMul(op1, op2, false);
        tool.setResult(this, resultAdd);
    }
}
