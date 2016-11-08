package tornado.drivers.opencl.graal.nodes;

public class CastNode extends FloatingNode implements LIRLowerable {

//    public static final NodeClass<CastNode> TYPE = NodeClass
//            .create(CastNode.class);
//
//    @Input
//    protected ValueNode value;
//
//    public CastNode(OCLKind kind, ValueNode value) {
//        super(TYPE, OCLStampFactory.getStampFor(kind));
//        this.value = value;
//    }
//
//    private OCLUnaryOp resolveOp() {
//        OCLKind kind = ((OCLStamp) stamp()).getOCLKind();
//        switch (kind) {
//            case INT:
//                return OCLUnaryOp.CAST_TO_INT;
//            case FLOAT:
//                return OCLUnaryOp.CAST_TO_FLOAT;
//            default:
//                unimplemented("kind: " + kind.toString());
//                break;
//        }
//        return null;
//    }
//
//    @Override
//    public void generate(NodeLIRBuilderTool gen) {
//        /*
//         * using as_T reinterprets the data as type T - consider: float x =
//         * (float) 1; and int value = 1, float x = &(value);
//         */
//        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
//        if (oclKind.isFloating()) {
//            gen.setResult(this, new OCLUnary.Expr(resolveOp(), lirKind, gen.operand(value)));
//        } else {
//            final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);
//            final OCLLIRInstruction.AssignStmt assign = new OCLLIRInstruction.AssignStmt(result, new OCLUnary.FloatCast(OCLUnaryOp.CAST_TO_INT, lirKind, gen.operand(value)));
//            gen.getLIRGeneratorTool().append(assign);
//            gen.setResult(this, result);
//        }
//
//    }
}
