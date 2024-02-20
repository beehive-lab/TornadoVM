package uk.ac.manchester.tornado.runtime.graal;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;

@NodeInfo()
public class HalfFloatConstant extends ValueNode {

    public static final NodeClass<HalfFloatConstant> TYPE = NodeClass.create(HalfFloatConstant.class);

    @Node.Input
    ValueNode input;

    public HalfFloatConstant(ValueNode input) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.input = input;
    }

    public ValueNode getValue() {
        return input;
    }

}
