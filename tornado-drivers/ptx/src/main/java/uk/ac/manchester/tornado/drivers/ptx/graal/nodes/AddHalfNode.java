package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class AddHalfNode extends ValueNode implements LIRLowerable {
    public static final NodeClass<AddHalfNode> TYPE = NodeClass.create(AddHalfNode.class);

    @Input
    private ValueNode x;

    @Input
    private ValueNode y;

    public AddHalfNode(ValueNode x, ValueNode y) {
        super(TYPE, new HalfFloatStamp());
        this.x = x;
        this.y = y;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(PTXKind.F16));
        Value inputX = generator.operand(x);
        Value inputY = generator.operand(y);
        tool.append(new PTXLIRStmt.AddHalfStmt(result, inputX, inputY));
        generator.setResult(this, result);
    }
}
