package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.runtime.common.exceptions.TornadoUnsupportedError;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkCastNode;

@NodeInfo(shortName = "SPIRVCastNode")
public class CastNode extends FloatingNode implements LIRLowerable, MarkCastNode {

    public static final NodeClass<CastNode> TYPE = NodeClass.create(CastNode.class);

    @Input
    protected ValueNode value;
    protected FloatConvert op;

    public CastNode(Stamp stamp, FloatConvert op, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
        this.op = op;
    }

    private SPIRVAssembler.SPIRVUnaryOp resolveOp() {
        switch (op) {
            case I2D:
            case F2D:
            case L2D:
                return SPIRVAssembler.SPIRVUnaryOp.CAST_TO_DOUBLE();
            case D2L:
            case F2L:
                return SPIRVAssembler.SPIRVUnaryOp.CAST_TO_LONG();
            default:
                TornadoUnsupportedError.unsupported("Conversion unimplemented: ", op.toString());
                break;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        throw new RuntimeException("Not supported");
    }
}
