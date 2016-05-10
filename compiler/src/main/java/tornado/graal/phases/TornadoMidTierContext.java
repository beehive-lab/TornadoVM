package tornado.graal.phases;

import tornado.meta.Meta;

import com.oracle.graal.api.code.SpeculationLog;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.ProfilingInfo;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.tiers.MidTierContext;
import com.oracle.graal.phases.util.Providers;

public class TornadoMidTierContext extends MidTierContext {

	protected final ResolvedJavaMethod method;
	protected final Object[] args;
	protected final Meta meta;
	
	public TornadoMidTierContext(
			Providers copyFrom,
			TargetDescription target,
			OptimisticOptimizations optimisticOpts,
			ProfilingInfo profilingInfo,
			SpeculationLog log,
			ResolvedJavaMethod method, Object[] args, Meta meta) {
		super(copyFrom, target, optimisticOpts, profilingInfo, log);
		this.method = method;
		this.args = args;
		this.meta = meta;
	}
	
	public ResolvedJavaMethod getMethod(){
		return method;
	}
	
	public Object[] getArgs(){
		return args;
	}
	
	public boolean hasArgs(){
		return args != null;
	}
	
	public Object getArg(int index){
		return args[index];
	}
	
	public int getNumArgs(){
		return (hasArgs()) ? args.length : 0;
	}
	
	public Meta getMeta(){
		return meta;
	}

}
