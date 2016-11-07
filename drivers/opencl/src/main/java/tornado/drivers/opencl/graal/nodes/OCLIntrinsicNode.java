package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.graal.nodes.Floatable;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLIntrinsicNode {

    public enum FloatingOp {
        ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
    }

    public enum IntegerOp {
        ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
    }

    public enum GeometricOp {
        CROSS, DISTANCE, DOT, LENGTH, NORMALISE, FAST_DISTANCE, FAST_LENGTH, FAST_NORMALISE
    }

    @NodeInfo(nameTemplate = "{p#operation}")
    public static final class FixedBinaryGeometricOp extends FixedNode implements Floatable {

        public static final NodeClass<FixedBinaryGeometricOp> TYPE = NodeClass
                .create(FixedBinaryGeometricOp.class);
        private final GeometricOp operation;
        @Input
        private ValueNode x;
        @Input
        private ValueNode y;

        public FixedBinaryGeometricOp(OCLKind kind, GeometricOp op, ValueNode x, ValueNode y) {
            super(TYPE, OCLStampFactory.getStampFor(kind.getElementKind()));
            this.operation = op;
            this.x = x;
            this.y = y;
        }

        @Override
        public FloatingNode asFloating() {
            return new BinaryGeometricOp(stamp, operation, x, y);
        }
    }

    @NodeInfo(nameTemplate = "{p#operation}")
    public static class BinaryGeometricOp extends BinaryNode implements ArithmeticLIRLowerable {

        public static final NodeClass<BinaryGeometricOp> TYPE = NodeClass
                .create(BinaryGeometricOp.class);
        protected final GeometricOp operation;

        public BinaryGeometricOp(Stamp stamp, GeometricOp op, ValueNode x, ValueNode y) {
            super(TYPE, stamp, x, y);
            this.operation = op;
        }

        @Override
        public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
            return this;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            return this;
        }

        @Override
        public Stamp foldStamp(Stamp stamp, Stamp stamp1) {
            return stamp;
        }

        @Override
        public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool arithmeticTool) {
            OCLBuiltinTool gen = ((OCLArithmeticTool) builder).getGen().getOCLBuiltinTool();
            final Value x = builder.operand(getX());
            final Value y = builder.operand(getY());
            Value result = null;
            switch (operation) {
                case CROSS:
                    result = gen.genGeometricCross(x, y);
                    break;
                case DISTANCE:
                    break;
                case DOT:
                    result = gen.genGeometricDot(x, y);
                    break;
                case FAST_DISTANCE:

                case FAST_LENGTH:

                case FAST_NORMALISE:

                case LENGTH:

                case NORMALISE:

                default:
                    unimplemented();
                    break;

            }

            Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
            builder.getLIRGeneratorTool().append(new AssignStmt(var, result));

            //System.out.printf("result: kind=%s, kind=%s\n",result.getKind(), vectorKind.getElementKind());
            builder.setResult(this, var);

        }

        @Override
        public void generate(NodeLIRBuilderTool builder) {
            generate(builder, builder.getLIRGeneratorTool().getArithmetic());
        }

    }

}
