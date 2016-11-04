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
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;

@NodeInfo(shortName = "-")
public class VectorSubNode extends FloatingNode implements LIRLowerable, VectorOp {
    
    public static final NodeClass<VectorSubNode> TYPE = NodeClass.create(VectorSubNode.class);
    
    @Input
    ValueNode x;
    @Input
    ValueNode y;
    
    public VectorSubNode(OCLKind kind, ValueNode x, ValueNode y) {
        this(TYPE, kind, x, y);
    }
    
    protected VectorSubNode(NodeClass<? extends VectorSubNode> c, OCLKind kind, ValueNode x, ValueNode y) {
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
        
        Tornado.trace("emitVectorSub: %s - %s", input1, input2);
        OCLStamp stamp = (OCLStamp) stamp();
        gen.setResult(this, new OCLBinary.Expr(OCLBinaryOp.SUB, stamp.getLIRKind(null), input1, input2));
    }
    
}
