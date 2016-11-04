package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.AbstractWriteNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@Deprecated
@NodeInfo
public class VectorWriteNode extends AbstractWriteNode implements LIRLowerable {

    public static final NodeClass<VectorWriteNode> TYPE = NodeClass
            .create(VectorWriteNode.class);

    public VectorWriteNode(
            ValueNode object,
            ValueNode value,
            ValueNode location,
            BarrierType barrierType) {
        super(TYPE, object, value, location, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
//		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
//
//		final OCLTernaryIntrinsic intrinsic = VectorUtil.resolveStoreIntrinsic(vectorKind);
//
//		final LocationNode location = location();
//
//		final Value object = gen.operand(object());
//
//		final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool,
//				object);
//		addressOfObject.setKind(vectorKind.getElementKind());
//
//		final Value valueToStore = gen.operand(value());
//
//		tool.append(new OCLLIRInstruction.ExprStmt(new OCLTernary.Intrinsic(intrinsic, Kind.Illegal,
//				valueToStore, PrimitiveConstant.INT_0, addressOfObject)));
//		Tornado.trace("emitVectorWrite: %s(%s, %d, %s)", intrinsic.toString(), valueToStore, 0,
//				addressOfObject);

    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

}
