package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.graal.nodes.logic.UnaryLogicalNode;

import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp.LOGICAL_NOT;

@NodeInfo(shortName = "!")
public class LogicalNotNode extends UnaryLogicalNode {

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    public static final NodeClass<LogicalNotNode> TYPE = NodeClass.create(LogicalNotNode.class);

    public LogicalNotNode(LogicNode value) {
        super(TYPE, value);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new OCLUnary.Expr(LOGICAL_NOT, tool.getLIRKind(stamp), x));
        tool.append(assign);
        return result;
    }

}
