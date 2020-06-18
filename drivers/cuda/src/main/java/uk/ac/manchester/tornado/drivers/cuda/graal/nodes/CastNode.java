package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkCastNode;

@NodeInfo
public class CastNode extends FloatingNode implements LIRLowerable, MarkCastNode {

    public static final NodeClass<CastNode> TYPE = NodeClass.create(CastNode.class);

    @Input
    protected ValueNode value;

    protected FloatConvert op;

    public CastNode(Stamp stamp, FloatConvert op, ValueNode value) {
        super(TYPE, stamp);
        this.op = op;
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);
        gen.getLIRGeneratorTool().append(new PTXLIRStmt.AssignStmt(result, gen.operand(value)));

        gen.setResult(this, result);
    }
}
