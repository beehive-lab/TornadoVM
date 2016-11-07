package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo
public class GlobalThreadId extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadId> TYPE = NodeClass
            .create(GlobalThreadId.class);

    @Input
    protected ConstantNode index;

    public GlobalThreadId(ConstantNode value) {
        super(TYPE, OCLStampFactory.getStampFor(OCLKind.INT));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new AssignStmt(result, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.GLOBAL_ID, tool.getLIRKind(stamp), (Value) index.asConstant())));
        gen.setResult(this, result);
    }

}
