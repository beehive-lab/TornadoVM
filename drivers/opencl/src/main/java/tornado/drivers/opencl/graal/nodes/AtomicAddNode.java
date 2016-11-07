package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.AccessIndexedNode;
import com.oracle.graal.nodes.spi.Lowerable;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(shortName = "Atomic Add")
public class AtomicAddNode extends AccessIndexedNode implements Lowerable {

    public static final NodeClass<AtomicAddNode> TYPE = NodeClass
            .create(AtomicAddNode.class);

    @Input
    ValueNode value;

    public AtomicAddNode(
            ValueNode array,
            ValueNode index,
            JavaKind elementKind,
            ValueNode value
    ) {
        super(TYPE, StampFactory.forVoid(), array, index, elementKind);
        this.value = value;
    }

    public ValueNode value() {
        return value;
    }

}
