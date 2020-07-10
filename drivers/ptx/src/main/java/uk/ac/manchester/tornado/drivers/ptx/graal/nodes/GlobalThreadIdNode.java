package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXBuiltInRegisterArray;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXTernary;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkGlobalThreadID;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXTernaryOp;

@NodeInfo
public class GlobalThreadIdNode extends FloatingNode implements LIRLowerable, MarkGlobalThreadID {
    public static final NodeClass<GlobalThreadIdNode> TYPE = NodeClass.create(GlobalThreadIdNode.class);

    @Input
    protected ConstantNode index;

    public GlobalThreadIdNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        LIRKind kind = tool.getLIRKind(stamp);
        PTXNodeLIRBuilder ptxNodeBuilder = (PTXNodeLIRBuilder) gen;

        PTXBuiltInRegisterArray builtIns = new PTXBuiltInRegisterArray(((ConstantValue) gen.operand(index)).getJavaConstant().asInt());
        Variable threadID = ptxNodeBuilder.getBuiltInAllocation(builtIns.threadID);
        Variable blockDim = ptxNodeBuilder.getBuiltInAllocation(builtIns.blockDim);
        Variable blockID = ptxNodeBuilder.getBuiltInAllocation(builtIns.blockID);
        Variable result = tool.newVariable(kind);

        tool.append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXTernaryOp.MAD_LO, kind, blockID, blockDim, threadID)));
        gen.setResult(this, result);
    }
}
