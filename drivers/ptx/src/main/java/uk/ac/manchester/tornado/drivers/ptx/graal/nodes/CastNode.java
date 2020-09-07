package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkCastNode;

import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

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

    /**
     * Generates the PTX LIR instructions for a cast between two variables. It covers the following cases:
     *  - if the result is not a FPU number and the value is a FPU number, then we perform a conversion which rounds towards zero (as Java does).
     *  Also, we check if the value is an exceptional case such as NaN, +/- infinity. For this case, we put 0 in the result.
     *  - if both operands are FPU, then we do a simple convert operation with the proper rounding modifier (if needed).
     */
    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
        trace("emitCast: convertOp=%s, value=%s", op, value);
        PTXLIRGenerator gen = (PTXLIRGenerator) nodeLIRBuilderTool.getLIRGeneratorTool();
        LIRKind lirKind = gen.getLIRKind(stamp);
        final Variable result = gen.newVariable(lirKind);

        Value value = nodeLIRBuilderTool.operand(this.value);
        PTXKind valueKind = (PTXKind) value.getPlatformKind();
        PTXKind resultKind = (PTXKind) result.getPlatformKind();

        PTXAssembler.PTXUnaryOp opcode;
        if (!resultKind.isFloating() && (valueKind.isFloating() || valueKind.getElementKind().isFloating())) {
            opcode = PTXAssembler.PTXUnaryOp.CVT_INT_RTZ;

            Variable nanPred = gen.newVariable(LIRKind.value(PTXKind.PRED));
            gen.append(new PTXLIRStmt.AssignStmt(nanPred, new PTXUnary.Expr(PTXAssembler.PTXUnaryOp.TESTP_NORMAL, LIRKind.value(valueKind), value)));
            gen.append(new PTXLIRStmt.ConditionalStatement(
                    new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(opcode, lirKind, value)), nanPred, false));
            gen.append(new PTXLIRStmt.ConditionalStatement(
                    new PTXLIRStmt.AssignStmt(result, new ConstantValue(LIRKind.value(resultKind), PrimitiveConstant.INT_0)), nanPred, true));
        } else {
            if (resultKind.isF64() && valueKind.isF32()) {
                opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT;
            } else {
                opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT_RNE;
            }
            gen.append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(opcode, lirKind, value)));
        }

        nodeLIRBuilderTool.setResult(this, result);
    }
}
