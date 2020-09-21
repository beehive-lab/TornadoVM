package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import java.util.HashMap;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
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

    private boolean ATOMIC_2_0 = false;

    // How many atomics integers per graph
    public static HashMap<StructuredGraph, Integer> globalAtomics = new HashMap<>();

    @Input
    ValueNode initialValue;

    private int indexFromGlobalMemory;

    public TornadoAtomicIntegerNode(OCLKind kind) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
        this.initialValue = ConstantNode.forInt(0);
    }

    public synchronized void setInitialValue(ValueNode valueNode) {
        initialValue = valueNode;
    }

    public synchronized void setInitialValueAtUsages(ValueNode valueNode) {
        initialValue.replaceAtUsages(valueNode);
    }

    public ValueNode getInitialValue() {
        return this.initialValue;
    }

    private void generateExpressionForOpenCL2_0(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(StampFactory.intValue()));
        tool.append(new OCLLIRStmt.RelocatedExpressionStmt(new OCLUnary.IntrinsicAtomicDeclaration(OCLAssembler.OCLUnaryIntrinsic.ATOMIC_VAR_INIT, result, gen.operand(initialValue))));
        gen.setResult(this, result);
    }

    private void generateExpressionForOpenCL1_0(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(StampFactory.intValue()));
        tool.append(new OCLLIRStmt.RelocatedExpressionStmt(new OCLUnary.IntrinsicAtomicDeclaration(OCLAssembler.OCLUnaryIntrinsic.ATOMIC_VAR_INIT, result, gen.operand(initialValue))));
        gen.setResult(this, result);
    }

    public int getIndexFromGlobalMemory() {
        return this.indexFromGlobalMemory;
    }

    private void assignIndex() {
        if (!globalAtomics.containsKey(this.graph())) {
            globalAtomics.put(this.graph(), 0);
            this.indexFromGlobalMemory = 0;
        } else {
            this.indexFromGlobalMemory = globalAtomics.get(this.graph()) + 1;
            globalAtomics.put(this.graph(), indexFromGlobalMemory);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assignIndex();
        if (ATOMIC_2_0) {
            generateExpressionForOpenCL2_0(gen);
        } else {
            generateExpressionForOpenCL1_0(gen);
        }
    }
}