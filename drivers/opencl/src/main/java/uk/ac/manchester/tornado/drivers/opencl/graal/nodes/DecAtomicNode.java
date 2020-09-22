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
public class DecAtomicNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<DecAtomicNode> TYPE = NodeClass.create(DecAtomicNode.class);

    private static boolean ATOMIC_2_0 = false;

    @Input
    ValueNode atomicNode;

    public DecAtomicNode(ValueNode atomicValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
    }

    private void generateExpressionForOpenCL2_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        OCLUnary.IntrinsicAtomicFetch intrinsicAtomicFetch = new OCLUnary.IntrinsicAtomicFetch( //
                OCLAssembler.OCLUnaryIntrinsic.ATOMIC_FETCH_SUB_EXPLICIT, //
                tool.getLIRKind(stamp), //
                generator.operand(atomicNode));

        OCLLIRStmt.AssignStmt assignStmt = new OCLLIRStmt.AssignStmt(result, intrinsicAtomicFetch);
        tool.append(assignStmt);
        generator.setResult(this, result);
    }

    private void generateExpressionForOpenCL1_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        if (atomicNode instanceof TornadoAtomicIntegerNode) {
            TornadoAtomicIntegerNode atomicIntegerNode = (TornadoAtomicIntegerNode) atomicNode;

            int indexFromGlobal = atomicIntegerNode.getIndexFromGlobalMemory();

            OCLUnary.IntrinsicAtomicInc intrinsicAtomicAdd = new OCLUnary.IntrinsicAtomicInc( //
                    OCLAssembler.OCLUnaryIntrinsic.ATOMIC_DEC, //
                    tool.getLIRKind(stamp), //
                    generator.operand(atomicNode), //
                    indexFromGlobal);

            OCLLIRStmt.AssignStmt assignStmt = new OCLLIRStmt.AssignStmt(result, intrinsicAtomicAdd);
            tool.append(assignStmt);
            generator.setResult(this, result);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (ATOMIC_2_0) {
            generateExpressionForOpenCL2_0(generator);
        } else {
            generateExpressionForOpenCL1_0(generator);
        }
    }
}
