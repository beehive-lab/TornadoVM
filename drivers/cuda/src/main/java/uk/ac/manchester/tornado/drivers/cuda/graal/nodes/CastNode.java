package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.Value;
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
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXUnary;
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

        Value value = gen.operand(this.value);
        PTXKind valueKind = (PTXKind) value.getPlatformKind();
        PTXKind resultKind = (PTXKind) result.getPlatformKind();

        PTXAssembler.PTXUnaryOp opcode = null;
        if (!resultKind.isFloating() && (valueKind.isFloating() || valueKind.getElementKind().isFloating())) {
            opcode = PTXAssembler.PTXUnaryOp.CVT_INT_RNI;
        } else if (resultKind.isF64() && valueKind.isF32()) {
            opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT;
        } else {
            opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT_RNE;
        }
        gen.getLIRGeneratorTool().append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(opcode, lirKind, value)));

        gen.setResult(this, result);
    }
}
