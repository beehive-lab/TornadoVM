package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.ExprStmt;
import tornado.drivers.opencl.graal.lir.OCLPrintf;

@NodeInfo(shortName = "printf")
public class PrintfNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PrintfNode> TYPE = NodeClass
            .create(PrintfNode.class);

    @Input
    private NodeInputList<ValueNode> inputs;

    public PrintfNode(ValueNode... values) {
        super(TYPE, StampFactory.forVoid());
        this.inputs = new NodeInputList<>(this, values.length);
        for (int i = 0; i < values.length; i++) {
            inputs.set(i, values[i]);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] args = new Value[inputs.size()];
        for (int i = 0; i < args.length; i++) {

            ValueNode param = inputs.get(i);
            if (param.isConstant()) {
                args[i] = gen.operand(param);
            } else {
                args[i] = gen.getLIRGeneratorTool().load(gen.operand(param));
            }
        }
        gen.getLIRGeneratorTool().append(new ExprStmt(new OCLPrintf(args)));
    }

}
