package tornado.runtime.api;

import java.lang.reflect.Method;

import tornado.common.enums.Access;
import tornado.meta.Meta;

public interface ExecuteCodeAction<T extends ExecuteCodeTask<?>> extends Action{
	public boolean isResolved();
	public boolean resolveByArgs(Object...args);
	public boolean resolveByTypes(Class<?>...types);
	
	public T invoke(Object...args);
	//public Object execute(Object...args) throws TornadoRuntimeException;
	
	
	//public void dumpCode();
	public Method method();
	public int getNumberArguments();
	
	//public void compile(Object[] parameters, Access[] access, Meta meta);
	//public void loadFromFile(String filename, Object[] parameters, Access[] access, Meta meta);
	//public CallStack getStack();
	//public void setStack(CallStack stack);
	//public void disableJIT();
}
