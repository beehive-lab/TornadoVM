package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

@NodeInfo(shortName = "VectorMult(*)")
public class VectorMultNode extends BinaryNode implements LIRLowerable, VectorOp {

    public static final NodeClass<VectorMultNode> TYPE = NodeClass.create(VectorMultNode.class);

    public VectorMultNode(SPIRVKind kind, ValueNode x, ValueNode y) {
        super(TYPE, SPIRVStampFactory.getStampFor(kind), x, y);
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return (stampX instanceof OCLStamp) ? stampX.join(stampY) : stampY.join(stampX);
    }

    @Override
    public Node canonical(CanonicalizerTool ct, ValueNode t, ValueNode t1) {
        return this;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool ct) {
        return this;
    }

    private SPIRVLIROp genBinaryExpr(SPIRVAssembler.SPIRVBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new SPIRVBinary.Expr(op, lirKind, x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);

        final Value input1 = gen.operand(x);
        final Value input2 = gen.operand(y);
        SPIRVLogger.traceBuildLIR("emitVectorMult: %s * %s", input1, input2);

        SPIRVKind kind = (SPIRVKind) lirKind.getPlatformKind();
        SPIRVAssembler.SPIRVBinaryOp binaryOp = SPIRVAssembler.SPIRVBinaryOp.MULT_INTEGER;

        if (kind.getElementKind().isFloatingPoint()) {
            binaryOp = SPIRVAssembler.SPIRVBinaryOp.MULT_FLOAT;
        }

        gen.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, genBinaryExpr(binaryOp, lirKind, input1, input2)));
        gen.setResult(this, result);
    }
}
