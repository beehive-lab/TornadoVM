package tornado.runtime;

import java.lang.reflect.Method;

import tornado.runtime.api.ExecutableTask;

public interface RuntimeInstance<D> {

	public <T> ObjectReference<D,T> register(final T object);
	public void sync();
	
	public void dumpEvents();
	
	public DataMovementTask read(Object... objects);
	public DataMovementTask write(Object... objects);
	public void makeVolatile(Object... objects);
	public DataMovementTask markHostDirty(Object... objects);
	public ExecutableTask createTask(Method method, Object code, boolean extractCVs, Object... args);
}
