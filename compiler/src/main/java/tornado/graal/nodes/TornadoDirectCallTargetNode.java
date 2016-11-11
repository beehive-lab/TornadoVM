package tornado.graal.nodes;

import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.ValueNode;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public class TornadoDirectCallTargetNode extends DirectCallTargetNode {

    public static final NodeClass<TornadoDirectCallTargetNode> TYPE = NodeClass.create(TornadoDirectCallTargetNode.class);

    public TornadoDirectCallTargetNode(ValueNode[] arguments, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod target, Type callType, InvokeKind invokeKind) {
        super(TYPE, arguments, returnStamp, signature, target, callType, invokeKind);
    }
}
