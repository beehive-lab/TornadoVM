package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

@NodeInfo
public class LoadIndexedVectorNode extends LoadIndexedNode {

    public static final NodeClass<LoadIndexedVectorNode> TYPE = NodeClass.create(LoadIndexedVectorNode.class);

    public LoadIndexedVectorNode(OCLKind oclKind, ValueNode array, ValueNode index, JavaKind elementKind) {
        super(TYPE, OCLStampFactory.getStampFor(oclKind), array, index, elementKind);
    }

    @Override
    public boolean inferStamp() {
        return false;
    }

}
