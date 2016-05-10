package tornado.drivers.opencl.graal.nodes.vector;

import tornado.common.Tornado;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;

import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "+")
public class VectorAddNode extends FloatingNode implements LIRLowerable, VectorOp {

    public static final NodeClass<VectorAddNode> TYPE = NodeClass.create(VectorAddNode.class);

    @Input VectorValueNode x;
    @Input VectorValueNode y;

    
    public VectorAddNode(VectorValueNode x, VectorValueNode y) {
        this(TYPE, x, y);
    }

    protected VectorAddNode(NodeClass<? extends VectorAddNode> c, VectorValueNode x, VectorValueNode y) {
        super(c, StampFactory.forKind(x.getVectorKind().getElementKind()));
        this.x = x;
        this.y = y;
    }

	public VectorValueNode getX(){
		return x;
	}
	
	public VectorValueNode getY(){
		return y;
	}

	@Override
	public void generate(NodeLIRBuilderTool gen) {

		final Value input1 = gen.operand(x); 
		final Value input2 = gen.operand(y);

		Tornado.trace("emitVectorAdd: %s + %s",input1,input2);
		gen.setResult(this, new OCLBinary.Expr(OCLBinaryOp.ADD,LIRKind.value(x.getVectorKind()),input1,input2));
	}

	
}
