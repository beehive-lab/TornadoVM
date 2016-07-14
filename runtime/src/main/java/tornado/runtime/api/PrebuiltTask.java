package tornado.runtime.api;

import tornado.common.DeviceMapping;
import tornado.common.SchedulableTask;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;

public class PrebuiltTask implements SchedulableTask {

	protected final String entryPoint;
	protected final String filename;
	protected final Object[] args;
	protected final Access[] argumentsAccess;
	protected final Meta meta;
	
	protected PrebuiltTask(String entryPoint, String filename, Object[] args, Access[] access, DeviceMapping device, DomainTree domain){
		this.entryPoint = entryPoint;
		this.filename = filename;
		this.args = args;
		this.argumentsAccess = access;
		meta = new Meta();
		meta.addProvider(DeviceMapping.class,device);
                meta.setDomain(domain);
	}
	
	public String toString(){
		final StringBuilder sb = new StringBuilder();
		
		sb.append("task: " + entryPoint + "()\n");
		for(int i=0;i<args.length;i++){
			sb.append(String.format("arg  : [%s] %s\n",argumentsAccess[i],args[i]));
		}
		
		sb.append("meta : " + meta.toString());
		
		return sb.toString();
	}

	
	@Override
	public Object[] getArguments() {
		return args;
	}

	@Override
	public Access[] getArgumentsAccess() {
		return argumentsAccess;
	}

	@Override
	public Meta meta() {
		return meta;
	}

	@Override
	public SchedulableTask mapTo(DeviceMapping mapping) {
		
		return this;
	}

	@Override
	public DeviceMapping getDeviceMapping() {
		return (meta.hasProvider(DeviceMapping.class)) ? meta
				.getProvider(DeviceMapping.class) : null;
	}

	@Override
	public String getName() {
		return "task - " + entryPoint;
	}

	public String getFilename(){
		return filename;
	}
	
	public String getEntryPoint(){
		return entryPoint;
	}
}
