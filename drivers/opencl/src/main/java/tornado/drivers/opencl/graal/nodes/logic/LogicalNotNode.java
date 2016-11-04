package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.OCLKind;
import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp.LOGICAL_NOT;
import tornado.drivers.opencl.graal.lir.OCLStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.graal.nodes.logic.UnaryLogicalNode;

@NodeInfo(shortName = "!")
public class LogicalNotNode extends UnaryLogicalNode {

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    public static final NodeClass<LogicalNotNode> TYPE = NodeClass.create(LogicalNotNode.class);

    public LogicalNotNode(LogicNode value) {
        super(TYPE, value);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(com.oracle.graal.compiler.common.LIRKind.value(OCLKind.BOOL));
        OCLStmt.AssignStmt assign = new OCLStmt.AssignStmt(result, new OCLUnary.Expr(LOGICAL_NOT, gen.operand(getValue())));
        tool.append(assign);
        gen.setResult(this, result);
    }

}
