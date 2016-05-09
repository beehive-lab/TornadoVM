package tornado.runtime;

import tornado.runtime.api.BarrierAction;

public class BarrierTask extends AbstractTask<BarrierAction> {

	public BarrierTask(BarrierAction action) {
		super(action, 0);
	}

}
