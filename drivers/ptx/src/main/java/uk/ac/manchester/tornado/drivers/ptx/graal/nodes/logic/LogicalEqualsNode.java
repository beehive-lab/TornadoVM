package uk.ac.manchester.tornado.drivers.ptx.graal.nodes.logic;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.logic.BinaryLogicalNode;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.RELATIONAL_EQ;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

@NodeInfo(shortName = "==")
public class LogicalEqualsNode extends BinaryLogicalNode {

    public static final NodeClass<LogicalEqualsNode> TYPE = NodeClass.create(LogicalEqualsNode.class);

    public LogicalEqualsNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x, Value y) {
        trace("emitEquals: x=%s, y=%s", x, y);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        PTXLIRStmt.AssignStmt assign = new PTXLIRStmt.AssignStmt(result, new PTXBinary.Expr(RELATIONAL_EQ, tool.getLIRKind(stamp), x, y));
        tool.append(assign);
        return result;
    }
}
