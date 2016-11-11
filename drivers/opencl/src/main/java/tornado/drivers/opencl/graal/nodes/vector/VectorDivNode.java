package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

import static tornado.graal.compiler.TornadoCodeGenerator.trace;

@NodeInfo(shortName = "/")
public class VectorDivNode extends FloatingNode implements LIRLowerable, VectorOp {

    public static final NodeClass<VectorDivNode> TYPE = NodeClass.create(VectorDivNode.class);

    @Input
    ValueNode x;
    @Input
    ValueNode y;

    public VectorDivNode(OCLKind kind, ValueNode x, ValueNode y) {
        this(TYPE, kind, x, y);
    }

    protected VectorDivNode(NodeClass<? extends VectorDivNode> c, OCLKind kind, ValueNode x, ValueNode y) {
        super(c, OCLStampFactory.getStampFor(kind));
        this.x = x;
        this.y = y;
    }

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);

        final Value input1 = gen.operand(x);
        final Value input2 = gen.operand(y);

        trace("emitVectorDiv: %s + %s", input1, input2);
        gen.getLIRGeneratorTool().append(new AssignStmt(result, new OCLBinary.Expr(OCLBinaryOp.DIV, gen.getLIRGeneratorTool().getLIRKind(stamp), input1, input2)));
        gen.setResult(this, result);
    }

}
