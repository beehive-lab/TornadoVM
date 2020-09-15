package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo(shortName = "ATOMIC_INTEGER")
public class TornadoAtomicIntegerNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<TornadoAtomicIntegerNode> TYPE = NodeClass.create(TornadoAtomicIntegerNode.class);

    private final OCLKind kind;

    @Input(InputType.Extension)
    ValueNode initialValue;

    public TornadoAtomicIntegerNode(OCLKind kind) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
        // this.initialValue = initialValue;
    }

    public void setInitialValue(ValueNode valueNode) {
        this.initialValue = valueNode;
    }

    public ValueNode getInitialValue() {
        return this.initialValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(StampFactory.intValue()));
        tool.append(new OCLLIRStmt.ExprStmt(new OCLUnary.IntrinsicAtomicDeclaration(OCLAssembler.OCLUnaryIntrinsic.ATOMIC_VAR_INIT, result)));
        gen.setResult(this, result);

    }
}