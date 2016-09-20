package tornado.common;

import tornado.meta.Meta;

public interface TornadoInstalledCode {

	public int launch(CallStack stack, Meta meta, int[] waitEvents);
	
}
