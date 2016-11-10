/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo
public class LocalThreadIdNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<LocalThreadIdNode> TYPE = NodeClass
            .create(LocalThreadIdNode.class);

    @Node.Input
    protected ConstantNode index;

    public LocalThreadIdNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.LOCAL_ID, tool.getLIRKind(stamp), gen.operand(index))));
        gen.setResult(this, result);
    }
}
