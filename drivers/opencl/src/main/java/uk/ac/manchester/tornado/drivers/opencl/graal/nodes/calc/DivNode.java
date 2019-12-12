package uk.ac.manchester.tornado.drivers.opencl.graal.nodes.calc;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;


/*
    This implementation is copied from the Graal compiler 0:22. We need to do this because on later versions of the compiler,
    the DivNode as a child of FloatingNode does not exist any longer.
 */

@NodeInfo(
        shortName = "div_node",
        cycles = NodeCycles.CYCLES_32
)
public class DivNode extends BinaryArithmeticNode<ArithmeticOpTable.BinaryOp.Div> {
    public static final NodeClass<DivNode> TYPE = NodeClass.create(DivNode.class);

    public DivNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getDiv(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y) {
        ArithmeticOpTable.BinaryOp<ArithmeticOpTable.BinaryOp.Div> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getDiv();
        Stamp stamp = op.foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, NodeView.DEFAULT);
        return (ValueNode)(tryConstantFold != null ? tryConstantFold : canonical((DivNode)null, op, x, y));
    }

    @Override
    protected ArithmeticOpTable.BinaryOp<ArithmeticOpTable.BinaryOp.Div> getOp(ArithmeticOpTable table) {
        return table.getDiv();
    }

    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        return ret != this ? ret : canonical(this, this.getOp(forX, forY), forX, forY);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonical(tool, getX(), getY());
    }

    private static ValueNode canonical(DivNode self, ArithmeticOpTable.BinaryOp<ArithmeticOpTable.BinaryOp.Div> op, ValueNode forX, ValueNode forY) {
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }

            if (c instanceof PrimitiveConstant && ((PrimitiveConstant)c).getJavaKind().isNumericInteger()) {
                long i = ((PrimitiveConstant)c).asLong();
                boolean signFlip = false;
                if (i < 0L) {
                    i = -i;
                    signFlip = true;
                }

                ValueNode divResult = null;
                if (CodeUtil.isPowerOf2(i)) {
                    divResult = new RightShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i)));
                }

                if (divResult != null) {
                    if (signFlip) {
                        return NegateNode.create(divResult, NodeView.DEFAULT);
                    }

                    return divResult;
                }
            }
        }

        return self != null ? self : new DivNode(forX, forY);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder) {
        generate(builder, builder.getLIRGeneratorTool().getArithmetic());
    }

    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitDiv(nodeValueMap.operand(this.getX()), nodeValueMap.operand(this.getY()), (LIRFrameState)null));
    }
}
