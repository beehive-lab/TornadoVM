package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo(shortName = "printfString")
public class PrintfStringNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<PrintfStringNode> TYPE = NodeClass.create(PrintfStringNode.class);

    @Input
    private ValueNode inputString;

    public PrintfStringNode(ValueNode inputString) {
        super(TYPE, StampFactory.forVoid());
        this.inputString = inputString;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        PTXLIRGenerator genTool = (PTXLIRGenerator) gen.getLIRGeneratorTool();
        Value inpString = gen.operand(inputString);

        Variable destArr = genTool.newVariable(LIRKind.value(PTXKind.B8), true);
        genTool.append(new PTXLIRStmt.PrintfStringDeclarationStmt(destArr, inpString));
        gen.setResult(this, destArr);
    }
}
