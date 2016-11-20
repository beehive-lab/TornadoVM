package tornado.graal.phases;

import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.meta.Meta;

public class TornadoSketchTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final Meta meta;

    public TornadoSketchTierContext(
            Providers providers,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts,
            ResolvedJavaMethod method,
            Meta meta) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.meta = meta;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Meta getMeta() {
        return meta;
    }

    public boolean hasMeta() {
        return meta != null;
    }
}
