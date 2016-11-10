package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.graal.nodes.logic.BinaryLogicalNode;

import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp.RELATIONAL_EQ;

@NodeInfo(shortName = "==")
public class LogicalEqualsNode extends BinaryLogicalNode {

    public static final NodeClass<LogicalEqualsNode> TYPE = NodeClass.create(LogicalEqualsNode.class);

    public LogicalEqualsNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x, Value y) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new OCLBinary.Expr(RELATIONAL_EQ, tool.getLIRKind(stamp), x, y));
        tool.append(assign);
        return result;
    }
}
