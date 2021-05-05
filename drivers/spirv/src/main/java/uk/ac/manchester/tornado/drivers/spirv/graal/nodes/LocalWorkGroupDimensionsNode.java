package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class LocalWorkGroupDimensionsNode extends FloatingNode implements LIRLowerable {

    public int oneD;
    public int twoD;
    public int threeD;

    public static final NodeClass<LocalWorkGroupDimensionsNode> TYPE = NodeClass.create(LocalWorkGroupDimensionsNode.class);

    public LocalWorkGroupDimensionsNode(int valueOne, int valueTwo, int valueThree) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        oneD = valueOne;
        twoD = valueTwo;
        threeD = valueThree;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
    }

    public int getOneD() {
        return oneD;
    }

    public int getTwoD() {
        return twoD;
    }

    public int getThreeD() {
        return threeD;
    }
}