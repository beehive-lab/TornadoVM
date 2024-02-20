package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;

@NodeInfo
public class VectorHalfRead extends FixedWithNextNode {

    public static final NodeClass<VectorHalfRead> TYPE = NodeClass.create(VectorHalfRead.class);

    int index = -1;

    public VectorHalfRead() {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
    }

    public VectorHalfRead(int index) {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

}
