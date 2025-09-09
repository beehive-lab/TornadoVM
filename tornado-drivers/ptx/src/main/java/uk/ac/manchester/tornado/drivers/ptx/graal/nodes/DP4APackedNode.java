package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;

@NodeInfo
public class DP4APackedNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<DP4APackedNode> TYPE = NodeClass.create(DP4APackedNode.class);

    @Input
    private ValueNode a;

    @Input
    private ValueNode b;

    @Input
    private ValueNode c;


    public DP4APackedNode(ValueNode a, ValueNode b, ValueNode c) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        // variable to store the result of -> dp4a.s32.s32 result, a, b, c;
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value a_value = generator.operand(a);
        Value b_value = generator.operand(b);
        Value accumulator_c = generator.operand(c);

        tool.append(new PTXLIRStmt.Dp4aPackedStmt(result, a_value, b_value, accumulator_c));
        generator.setResult(this, result);

    }

}
