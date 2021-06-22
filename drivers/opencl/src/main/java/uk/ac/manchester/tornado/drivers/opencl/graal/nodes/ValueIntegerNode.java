package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

public class ValueIntegerNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<ValueIntegerNode> TYPE = NodeClass.create(ValueIntegerNode.class);

    @Input
    protected ConstantNode valueIndex;

    public ValueIntegerNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        valueIndex = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result, gen.operand(valueIndex)));
        gen.setResult(this, result);
    }
}
