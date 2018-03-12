package uk.ac.manchester.tornado.graal.nodes;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "REDUCE(-)")
public class OCLReduceSubNode extends SubNode {

    public static final NodeClass<OCLReduceSubNode> TYPE = NodeClass.create(OCLReduceSubNode.class);

    public OCLReduceSubNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool tool, ArithmeticLIRGeneratorTool gen) {

        Value op1 = tool.operand(getX());
        assert op1 != null : getX() + ", this=" + this;
        Value op2 = tool.operand(getY());

        if (shouldSwapInputs(tool)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        Value resultAdd = gen.emitSub(op1, op2, false);
        tool.setResult(this, resultAdd);

    }
}
