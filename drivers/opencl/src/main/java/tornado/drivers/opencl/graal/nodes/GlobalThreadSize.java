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
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo
public class GlobalThreadSize extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadSize> TYPE = NodeClass.create(GlobalThreadSize.class);

    @Input
    protected ConstantNode index;

    public GlobalThreadSize(ConstantNode value) {
        super(TYPE, StampFactory.forKind(Kind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRInstruction.AssignStmt(result, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.GLOBAL_SIZE, Kind.Int, (Value) index.asConstant())));
        gen.setResult(this, result);
    }

}
