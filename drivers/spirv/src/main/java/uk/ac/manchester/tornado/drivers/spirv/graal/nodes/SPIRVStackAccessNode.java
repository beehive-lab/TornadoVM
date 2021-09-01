package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

@NodeInfo
public class SPIRVStackAccessNode extends FloatingNode implements LIRLowerable {

    @Input
    private ConstantNode index;

    public static final NodeClass<SPIRVStackAccessNode> TYPE = NodeClass.create(SPIRVStackAccessNode.class);

    public SPIRVStackAccessNode(ConstantNode index) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.index = index;
    }

    public ConstantNode getIndex() {
        return this.index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        // We know we load an integer value
        SPIRVKind spirvKind = SPIRVKind.OP_TYPE_INT_32;
        LIRKind lirKind = LIRKind.value(spirvKind);

        Value value = gen.operand(index);
        SPIRVLIRStmt.ASSIGNIndexedParameter assignStmt = new SPIRVLIRStmt.ASSIGNIndexedParameter(result, new SPIRVUnary.LoadIndexValueFromStack(lirKind, spirvKind, value));
        gen.setResult(this, result);

        SPIRVLIRGenerator spirvlirGenerator = (SPIRVLIRGenerator) gen.getLIRGeneratorTool();
        spirvlirGenerator.append(assignStmt);
    }
}