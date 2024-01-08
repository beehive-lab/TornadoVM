package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.nodes.ValueNode;

@NodeInfo
public class NewHalfFloatInstance extends FixedWithNextNode {

    public static final NodeClass<NewHalfFloatInstance> TYPE = NodeClass.create(NewHalfFloatInstance.class);

    @Input
    private ValueNode value;

    public NewHalfFloatInstance(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.value = value;
    }

    public ValueNode getValue() {
        return this.value;
    }
}
