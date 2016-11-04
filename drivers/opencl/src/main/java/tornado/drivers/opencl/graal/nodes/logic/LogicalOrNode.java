package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.OCLKind;
import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp.LOGICAL_OR;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLStmt;
import tornado.graal.nodes.logic.BinaryLogicalNode;

@NodeInfo(shortName = "||")
public class LogicalOrNode extends BinaryLogicalNode {

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    public static final NodeClass<LogicalOrNode> TYPE = NodeClass.create(LogicalOrNode.class);

    public LogicalOrNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(com.oracle.graal.compiler.common.LIRKind.value(OCLKind.BOOL));
        OCLStmt.AssignStmt assign = new OCLStmt.AssignStmt(result, new OCLBinary.Expr(LOGICAL_OR, gen.operand(getX()), gen.operand(getY())));
        tool.append(assign);
        gen.setResult(this, result);

    }
}
