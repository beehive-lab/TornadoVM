package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class SPIRVStackAccessNode extends FloatingNode implements LIRLowerable {

    @Input
    private ConstantNode index;

    public static final NodeClass<SPIRVStackAccessNode> TYPE = NodeClass.create(SPIRVStackAccessNode.class);

    public SPIRVStackAccessNode(ConstantNode index) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.index = index;
    }

    public ConstantNode getIndex() {
        return this.index;
    }

    /**
     * OpenCL Equivalent:
     * 
     * <code>
     *     val a = _frame[idx]; 
     * </code>
     * 
     * @param gen
     *            {@link NodeLIRBuilderTool gen}
     */
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        // tool.append(new OCLLIRStmt.AssignStmt(result, new
        // OCLUnary.LoadOCLStack(OCLAssembler.OCLUnaryIntrinsic.OCL_STACK_ACCESS,
        // tool.getLIRKind(stamp), gen.operand(index))));
        gen.setResult(this, result);

        throw new RuntimeException("SPIRV Operation not supported yet");

    }
}