package tornado.graal.phases;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.nodes.java.StoreFieldNode;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.phases.BasePhase;
import java.util.ArrayDeque;
import java.util.Queue;
import tornado.common.enums.Access;
import tornado.meta.Meta;

import static tornado.common.Tornado.debug;

public class TornadoDataflowAnalysis extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        if (!context.hasMeta()) {
            return;
        }

        Meta meta = context.getMeta();
        Access[] accesses = meta.getArgumentsAccess();

        for (int i = 0; i < accesses.length; i++) {
            accesses[i] = Access.NONE;
            ParameterNode param = graph.getParameter(i);

            // Only interested in objects
            if (param != null && param.stamp() instanceof ObjectStamp) {
                accesses[i] = processUseages(param);
            }

            debug("access: parameter %d -> %s\n", i, accesses[i]);
        }

    }

    private Access processUseages(Node parameter) {
//        NodeBitMap nodes = graph.createNodeBitMap();
//        nodes.clearAll();

        boolean isRead = false;
        boolean isWritten = false;

        Queue<Node> nf = new ArrayDeque<>();
        parameter.usages().forEach(nf::add);

        while (!nf.isEmpty()) {
            Node currentNode = nf.remove();
            if (currentNode instanceof LoadIndexedNode) {
                isRead = true;
            } else if (currentNode instanceof StoreIndexedNode) {
                isWritten = true;
            } else if (currentNode instanceof LoadFieldNode) {
                LoadFieldNode loadField = (LoadFieldNode) currentNode;
                if (loadField.stamp() instanceof ObjectStamp) {
                    loadField.usages().forEach(nf::add);
                }
                isRead = true;
            } else if (currentNode instanceof StoreFieldNode) {
                isWritten = true;
            } else if (currentNode instanceof PiNode) {
                currentNode.usages().forEach(nf::add);
            }
        }

        Access result = Access.NONE;
        if (isRead && isWritten) {
            result = Access.READ_WRITE;
        } else if (isRead) {
            result = Access.READ;
        } else if (isWritten) {
            result = Access.WRITE;
        }

        return result;
    }
}
