package tornado.graal.nodes;

import java.util.List;

import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.JavaType;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo
public class TornadoDirectCallTargetNode extends DirectCallTargetNode {
	public static final NodeClass<TornadoDirectCallTargetNode> TYPE = NodeClass.create(TornadoDirectCallTargetNode.class);

    public TornadoDirectCallTargetNode(List<ValueNode> arguments, Stamp returnStamp, JavaType[] signature, ResolvedJavaMethod target, Type callType, InvokeKind invokeKind) {
        super(TYPE, arguments, returnStamp, signature, target, callType, invokeKind);
    }
}
