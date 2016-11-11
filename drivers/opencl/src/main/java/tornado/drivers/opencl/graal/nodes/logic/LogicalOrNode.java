package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import jdk.vm.ci.meta.Value;

import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp.LOGICAL_OR;

import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.graal.nodes.logic.BinaryLogicalNode;

@NodeInfo(shortName = "||")
public class LogicalOrNode extends BinaryLogicalNode {

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    public static final NodeClass<LogicalOrNode> TYPE = NodeClass.create(LogicalOrNode.class);

    public LogicalOrNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x, Value y) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new OCLBinary.Expr(LOGICAL_OR, tool.getLIRKind(stamp), x, y));
        tool.append(assign);
        return result;
    }
}
