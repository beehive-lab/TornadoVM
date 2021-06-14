package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVVectorAssign;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkVectorValueNode;

@NodeInfo(nameTemplate = "{p#kind/s}")
public class SPIRVVectorValueNode extends FloatingNode implements LIRLowerable, MarkVectorValueNode {

    public static final NodeClass<SPIRVVectorValueNode> TYPE = NodeClass.create(SPIRVVectorValueNode.class);

    @OptionalInput(InputType.Association)
    private ValueNode origin;

    private SPIRVKind kind;

    @Input
    NodeInputList<ValueNode> values;

    public SPIRVVectorValueNode(SPIRVKind spirvVectorKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(spirvVectorKind));
        this.kind = spirvVectorKind;
        this.values = new NodeInputList<>(this, kind.getVectorLength());
    }

    public void initialiseToDefaultValues(StructuredGraph graph) {
        final ConstantNode defaultValue = ConstantNode.forPrimitive(kind.getElementKind().getDefaultValue(), graph);
        for (int i = 0; i < kind.getVectorLength(); i++) {
            setElement(i, defaultValue);
        }
    }

    public void setElement(int index, ValueNode value) {
        if (values.get(index) != null) {
            values.get(index).replaceAtUsages(value);
        } else {
            values.set(index, value);
        }
    }

    public ValueNode length() {
        return ConstantNode.forInt(kind.getVectorLength());
    }

    public SPIRVKind getSPIRVKind() {
        return kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        if (origin instanceof InvokeNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin instanceof ValuePhiNode) {

            final ValuePhiNode phi = (ValuePhiNode) origin;

            final Value phiOperand = ((OCLNodeLIRBuilder) gen).operandForPhi(phi);
            final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this) : tool.newVariable(LIRKind.value(getSPIRVKind()));
            tool.append(new SPIRVLIRStmt.AssignStmt(result, phiOperand));
            gen.setResult(this, result);
        } else if (origin instanceof ParameterNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin == null) {
            final AllocatableValue result = tool.newVariable(LIRKind.value(getSPIRVKind()));

            /*
             * Two cases:
             *
             * 1. when this vector state has elements assigned individually.
             *
             * 2.when this vector is assigned by a vector operation
             *
             */
            final int numValues = values.count();
            final ValueNode firstValue = values.first();

            if (firstValue instanceof VectorValueNode || firstValue instanceof VectorOp) {
                tool.append(new SPIRVLIRStmt.AssignStmt(result, gen.operand(values.first())));
                gen.setResult(this, result);
            } else if (numValues > 0 && gen.hasOperand(firstValue)) {
                generateVectorAssign(gen, tool, result);
            } else {
                gen.setResult(this, result);
            }
        }
    }

    private Value getParam(NodeLIRBuilderTool gen, LIRGeneratorTool tool, int index) {
        final ValueNode valueNode = values.get(index);
        return (valueNode == null) ? new ConstantValue(LIRKind.value(kind), kind.getDefaultValue()) : tool.load(gen.operand(valueNode));
    }

    // THis construct generates the equivalent of the following OpenCL Code:
    // vtype = (a, b, c, d);
    private void generateVectorAssign(NodeLIRBuilderTool gen, LIRGeneratorTool tool, AllocatableValue result) {
        SPIRVLIROp assignExpr = null;
        Value s0;
        Value s1;
        switch (kind.getVectorLength()) {
            case 2:
                s0 = getParam(gen, tool, 0);
                s1 = getParam(gen, tool, 1);
                LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.Assign2Expr(lirKind, s0, s1);
                break;
            default:
                throw new RuntimeException("Operation type not supported");
        }
        tool.append(new SPIRVLIRStmt.AssignStmt(result, assignExpr));
        gen.setResult(this, result);
    }
}
