package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class MultHalfNode extends ValueNode implements LIRLowerable {
    public static final NodeClass<MultHalfNode> TYPE = NodeClass.create(MultHalfNode.class);

    @Input
    private ValueNode x;

    @Input
    private ValueNode y;

    public MultHalfNode(ValueNode x, ValueNode y) {
        super(TYPE, new HalfFloatStamp());
        this.x = x;
        this.y = y;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(OCLKind.HALF));
        Value inputX = generator.operand(x);
        Value inputY = generator.operand(y);
        tool.append(new OCLLIRStmt.MultHalfStmt(result, inputX, inputY));
        generator.setResult(this, result);
    }
}
