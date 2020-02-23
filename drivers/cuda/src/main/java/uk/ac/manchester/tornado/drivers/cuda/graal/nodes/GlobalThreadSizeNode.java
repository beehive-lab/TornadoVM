package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

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
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt;

import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXBuiltInRegisterArray;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp.MUL_LU;

@NodeInfo
public class GlobalThreadSizeNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadSizeNode> TYPE = NodeClass.create(GlobalThreadSizeNode.class);

    @Input protected ConstantNode index;

    public GlobalThreadSizeNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        LIRKind kind = tool.getLIRKind(stamp);
        Variable result = tool.newVariable(kind);

        PTXBuiltInRegisterArray builtIns = new PTXBuiltInRegisterArray(((ConstantValue)gen.operand(index)).getJavaConstant().asInt());
        Variable gridDim = builtIns.gridDim.getAllocatedTo(tool);
        Variable blockDim = builtIns.blockDim.getAllocatedTo(tool);

        tool.append(new PTXLIRStmt.AssignStmt(result, new PTXBinary.Expr(MUL_LU, kind, gridDim, blockDim)));
        gen.setResult(this, result);
    }

}
