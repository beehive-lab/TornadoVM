package tornado.runtime.api;

import tornado.runtime.AbstractTask;

public abstract class ExecuteCodeTask<A extends ExecuteCodeAction<?>> extends AbstractTask<A>{
	public ExecuteCodeTask(A action) {
		super(action, action.getNumberArguments());
	}
	
	abstract public String getMethodName();
	abstract public CallStack getStack();
	abstract public void resolve();
	abstract public void dumpCode();	
}
