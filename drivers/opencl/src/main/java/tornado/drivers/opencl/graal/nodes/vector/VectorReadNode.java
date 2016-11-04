package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.FloatableAccessNode;
import com.oracle.graal.nodes.memory.FloatingAccessNode;
import com.oracle.graal.nodes.memory.MemoryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

@Deprecated
@NodeInfo
public class VectorReadNode extends FloatableAccessNode implements LIRLowerable {

    public static final NodeClass<VectorReadNode> TYPE = NodeClass
            .create(VectorReadNode.class);

    public VectorReadNode(
            OCLKind vectorKind,
            ValueNode object,
            ValueNode location,
            BarrierType barrierType) {
        super(TYPE, object, location, OCLStampFactory.getStampFor(vectorKind), null, barrierType);
        setForceFixed(true);
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public FloatingAccessNode asFloatingNode(MemoryNode arg0) {
        throw GraalInternalError.shouldNotReachHere("VectorReadNode asFloatingNode");
        // return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        shouldNotReachHere();
//		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
//
//		OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(((OCLStamp)stamp).getOCLKind());
//
//		final Variable result = tool.newVariable(tool.getLIRKind(stamp));
//
//		final LocationNode location = location();
//		final Value object = gen.operand(object());
//
//		final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool, object);
////		addressOfObject.setKind(vectorKind.getElementKind());
//		
//		trace("address: type=%s, %s\n",addressOfObject.getClass().getName(),addressOfObject);
//		
//                VectorLoadStmt stmt = new VectorLoadStmt(result, intrinsic,PrimitiveConstant.INT_0, cast, addressOfObject);
//	
//		
//		tool.append(stmt);
//		trace("emitVectorLoad: %s = %s(%d, %s)", result, intrinsic.toString(), 0,
//				addressOfObject);
//		gen.setResult(this, result);

    }

}
