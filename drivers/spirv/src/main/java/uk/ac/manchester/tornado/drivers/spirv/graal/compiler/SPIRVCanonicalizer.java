package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorElementOpNode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVCanonicalizer implements CanonicalizerPhase.CustomCanonicalization {

    protected MetaAccessProvider metaAccess;
    protected ResolvedJavaMethod method;
    protected TaskMetaData meta;
    protected Object[] args;

    public void setContext(MetaAccessProvider metaAccess, ResolvedJavaMethod method, Object[] args, TaskMetaData meta) {
        this.metaAccess = metaAccess;
        this.method = method;
        this.meta = meta;
        this.args = args;
    }

    @Override
    public Node canonicalize(Node node) {
        if (node instanceof VectorElementOpNode) {
            return canonicalizeVectorElementOp((VectorElementOpNode) node);
        }
        return node;
    }

    private Node canonicalizeVectorElementOp(VectorElementOpNode node) {
        return node;
    }

    public enum VectorOp {
        MULT, ADD, SUB, DIV, ILLEGAL
    };
}
