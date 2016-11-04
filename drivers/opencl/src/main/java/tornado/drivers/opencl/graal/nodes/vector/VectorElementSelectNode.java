package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.api.meta.*;
import static com.oracle.graal.compiler.common.util.Util.guarantee;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = ".{p#selection}")
public class VectorElementSelectNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<VectorElementSelectNode> TYPE = NodeClass
            .create(VectorElementSelectNode.class);

    @Input(InputType.Extension)
    ValueNode vector;
    
    @Input
    ValueNode selection;


    public VectorElementSelectNode(OCLKind kind, ValueNode vector, ValueNode selection) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.vector = vector;
        this.selection = selection;
    }

    @Override
    public boolean inferStamp() {
        return true;
        //return updateStamp(createStamp(vector, kind.getElementKind()));
    }

    public ValueNode getSelection() {
        return selection;
    }

    public ValueNode getVector() {
        return vector;
    }

    public OCLKind getOCLKind() {
        return ((OCLStamp) stamp).getOCLKind();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector operand is null");
        Value targetVector = gen.operand(getVector());
        Value selectValue =  gen.operand(getSelection());

        guarantee(targetVector != null, "vector value is null 2");
         guarantee(selectValue != null, "select value is null");
        final OCLBinary.Selector expr = new OCLBinary.Selector(OCLBinaryOp.VECTOR_SELECT,gen.getLIRGeneratorTool().getLIRKind(stamp), targetVector, selectValue);
        gen.setResult(this, expr);

    }

}
