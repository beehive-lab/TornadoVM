package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.api.meta.Value;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.common.Tornado;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;

@NodeInfo(shortName = "*")
public class VectorMulNode extends FloatingNode implements LIRLowerable, VectorOp {
    
    public static final NodeClass<VectorMulNode> TYPE = NodeClass.create(VectorMulNode.class);
    
    @Input
    ValueNode x;
    @Input
    ValueNode y;
    
    public VectorMulNode(OCLKind kind, ValueNode x, ValueNode y) {
        this(TYPE, kind, x, y);
    }
    
    protected VectorMulNode(NodeClass<? extends VectorMulNode> c, OCLKind kind, ValueNode x, ValueNode y) {
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
        
        final Value input1 = gen.operand(x);        
        final Value input2 = gen.operand(y);
        
        Tornado.trace("emitVectorMul: %s * %s", input1, input2);
        OCLStamp stamp = (OCLStamp) stamp();
        gen.setResult(this, new OCLBinary.Expr(OCLBinaryOp.MUL, stamp.getLIRKind(null), input1, input2));
    }
    
}
