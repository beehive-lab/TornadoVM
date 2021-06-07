package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class SPIRVIntUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<SPIRVIntUnaryIntrinsicNode> TYPE = NodeClass.create(SPIRVIntUnaryIntrinsicNode.class);
    private SPIRVIntOperation operation;

    protected SPIRVIntUnaryIntrinsicNode(SPIRVIntOperation operation, ValueNode value, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        this.operation = operation;
    }

    public enum SPIRVIntOperation {
        ABS, CLZ, POPCOUNT;
    }

    public static ValueNode create(ValueNode x, SPIRVIntOperation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, op, kind);
        if (c != null) {
            return c;
        }
        return new SPIRVIntUnaryIntrinsicNode(op, x, kind);
    }

    private static long doCompute(long value, SPIRVIntOperation op) {
        switch (op) {
            case ABS:
                return Math.abs(value);
            case CLZ:
                return Long.numberOfLeadingZeros(value);
            case POPCOUNT:
                return Long.bitCount(value);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int value, SPIRVIntOperation op) {
        switch (op) {
            case ABS:
                return Math.abs(value);
            case CLZ:
                return Integer.numberOfLeadingZeros(value);
            case POPCOUNT:
                return Integer.bitCount(value);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    protected static ValueNode tryConstantFold(ValueNode x, SPIRVIntOperation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    public SPIRVIntOperation operation() {
        return operation;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(value, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGeneratorTool) {
        SPIRVBuiltinTool gen = ((SPIRVArithmeticTool) lirGeneratorTool).getGen().getSpirvBuiltinTool();
        Value x = builder.operand(getValue());
        Value computeIntrinsic;
        switch (operation) {
            case ABS:
                computeIntrinsic = gen.genIntAbs(x);
                break;
            case POPCOUNT:
                computeIntrinsic = gen.genIntPopcount(x);
                break;
            default:
                throw new RuntimeException("Int binary intrinsic not supported yet");
        }
        Variable result = builder.getLIRGeneratorTool().newVariable(computeIntrinsic.getValueKind());
        builder.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, computeIntrinsic));
        builder.setResult(this, result);
    }

}
