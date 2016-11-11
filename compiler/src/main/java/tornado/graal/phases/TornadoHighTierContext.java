package tornado.graal.phases;

import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.common.DeviceMapping;
import tornado.meta.Meta;

public class TornadoHighTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final Meta meta;
    protected final boolean isKernel;

    public TornadoHighTierContext(
            Providers providers,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts,
            ResolvedJavaMethod method,
            Object[] args,
            Meta meta,
            boolean isKernel) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.args = args;
        this.meta = meta;
        this.isKernel = isKernel;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getNumArgs() {
        return (hasArgs()) ? args.length : 0;
    }

    public Meta getMeta() {
        return meta;
    }

    public boolean hasDeviceMapping() {
        return meta != null && meta.hasProvider(DeviceMapping.class);
    }

    public DeviceMapping getDeviceMapping() {
        return meta.getProvider(DeviceMapping.class);
    }

    public boolean hasMeta() {
        return meta != null;
    }

    public boolean isKernel() {
        return isKernel;
    }

}
