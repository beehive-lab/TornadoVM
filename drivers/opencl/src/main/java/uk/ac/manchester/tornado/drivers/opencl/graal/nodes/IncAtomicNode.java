package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo(shortName = "INCREMENT_ATOMIC")
public class IncAtomicNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<IncAtomicNode> TYPE = NodeClass.create(IncAtomicNode.class);

    @Input
    ValueNode atomicNode;

    public IncAtomicNode(ValueNode atomicValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        OCLUnary.IntrinsicAtomicFetch intrinsicAtomicFetch = new OCLUnary.IntrinsicAtomicFetch( //
                OCLAssembler.OCLUnaryIntrinsic.ATOMIC_FETCH_ADD_EXPLICIT, //
                tool.getLIRKind(stamp), //
                generator.operand(atomicNode));

        OCLLIRStmt.AssignStmt assignStmt = new OCLLIRStmt.AssignStmt(result, intrinsicAtomicFetch);
        tool.append(assignStmt);
        generator.setResult(this, result);
    }
}
