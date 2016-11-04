package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo
public class GroupIdNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GroupIdNode> TYPE = NodeClass
            .create(GroupIdNode.class);

    @Input
    protected ConstantNode index;

    public GroupIdNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(Kind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRInstruction.AssignStmt(result, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.GROUP_ID, Kind.Int, (Value) index.asConstant())));
        gen.setResult(this, result);
    }

}
