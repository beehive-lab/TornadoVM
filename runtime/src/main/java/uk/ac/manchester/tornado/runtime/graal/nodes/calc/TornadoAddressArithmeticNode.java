package uk.ac.manchester.tornado.runtime.graal.nodes.calc;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class TornadoAddressArithmeticNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<TornadoAddressArithmeticNode> TYPE = NodeClass.create(TornadoAddressArithmeticNode.class);

    @Node.Input
    protected ValueNode base;

    @Node.Input
    protected ValueNode offset;


    public TornadoAddressArithmeticNode(ValueNode base, ValueNode offset) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.base = base;
        this.offset = offset;
    }

    public ValueNode getBase() {
        return base;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value base = gen.operand(this.base);
        Value offset = gen.operand(this.offset);
        gen.setResult(this, tool.getArithmetic().emitAdd(base, offset, false));
    }
}
