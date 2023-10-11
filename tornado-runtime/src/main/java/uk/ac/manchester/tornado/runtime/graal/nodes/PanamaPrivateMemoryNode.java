package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "TornadoPrivateMemoryNode")
public class PanamaPrivateMemoryNode extends FixedWithNextNode {

    public static final NodeClass<PanamaPrivateMemoryNode> TYPE = NodeClass.create(PanamaPrivateMemoryNode.class);
    private ResolvedJavaType elementType;
    @Input
    private ValueNode length;

    public PanamaPrivateMemoryNode(ResolvedJavaType elementType, ValueNode length) {
        super(TYPE, StampFactory.forKind(elementType.getJavaKind())); //, length, true, null);
        this.elementType = elementType;
        this.length = length;
    }

    public ResolvedJavaType getResolvedJavaType() {
        return elementType;
    }

    public ValueNode getLength() {
        return length;
    }
}
