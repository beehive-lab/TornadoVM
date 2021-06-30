package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVCanonicalizer implements CanonicalizerPhase.CustomSimplification {

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
    public void simplify(Node node, SimplifierTool tool) {

    }

}
