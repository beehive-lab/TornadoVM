package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

@NodeInfo
public class CastNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<CastNode> TYPE = NodeClass
            .create(CastNode.class);

    @Input
    protected ValueNode value;

    public CastNode(OCLKind kind, ValueNode value) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.value = value;
    }

    private OCLUnaryOp resolveOp() {
        OCLKind kind = ((OCLStamp) stamp()).getOCLKind();
        switch (kind) {
            case INT:
                return OCLUnaryOp.CAST_TO_INT;
            case FLOAT:
                return OCLUnaryOp.CAST_TO_FLOAT;
            default:
                unimplemented("kind: " + kind.toString());
                break;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /*
         * using as_T reinterprets the data as type T - consider: float x =
         * (float) 1; and int value = 1, float x = &(value);
         */
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        OCLKind oclKind = ((OCLStamp) stamp()).getOCLKind();
        if (oclKind.isFloating()) {
            gen.setResult(this, new OCLUnary.Expr(resolveOp(), lirKind, gen.operand(value)));
        } else {
            final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);
            final OCLLIRInstruction.AssignStmt assign = new OCLLIRInstruction.AssignStmt(result, new OCLUnary.FloatCast(OCLUnaryOp.CAST_TO_INT, lirKind, gen.operand(value)));
            gen.getLIRGeneratorTool().append(assign);
            gen.setResult(this, result);
        }

    }
}
