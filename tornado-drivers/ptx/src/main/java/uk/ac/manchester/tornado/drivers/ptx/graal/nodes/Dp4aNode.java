package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
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
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class Dp4aNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<Dp4aNode> TYPE = NodeClass.create(Dp4aNode.class);

    @Input
    private ValueNode a;

    @Input
    private ValueNode b;

    @Input
    private ValueNode c;

    @Input
    private ValueNode offset;

    private static int HEADER_SIZE = (int) TornadoNativeArray.ARRAY_HEADER;

    public Dp4aNode(ValueNode a, ValueNode b, ValueNode c, ValueNode offset) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.a = a;
        this.b = b;
        this.c = c;
        this.offset = offset;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        // variable to store the result of -> dp4a.s32.s32 result, a, b, c;
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        // variables to calculate the offsets
        Value offset_value = generator.operand(offset);
        Variable cnv_offset = tool.newVariable(LIRKind.value(PTXKind.U64));
        Variable add_header_offset = tool.newVariable(LIRKind.value(PTXKind.U32));

        // address variables for a
        Value base_address_int8_a = generator.operand(a);
        Variable offseted_address_a = tool.newVariable(LIRKind.value(PTXKind.U64));
        Variable load_four_int8_bytes_a = tool.newVariable(tool.getLIRKind(stamp));

        // address variables for b
        Value base_address_int8_b = generator.operand(b);
        Variable offseted_address_b = tool.newVariable(LIRKind.value(PTXKind.U64));
        Variable load_four_int8_bytes_b = tool.newVariable(tool.getLIRKind(stamp));

        // variable for accumulator
        Value accumulator_c = generator.operand(c);

        tool.append(new PTXLIRStmt.Dp4aStmt(result, base_address_int8_a, load_four_int8_bytes_a, base_address_int8_b, load_four_int8_bytes_b, accumulator_c, offset_value, cnv_offset, add_header_offset, offseted_address_a, offseted_address_b, HEADER_SIZE));
        generator.setResult(this, result);
    }

}
