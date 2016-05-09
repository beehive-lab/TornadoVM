package tornado.runtime.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.api.Write;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.RuntimeUtilities;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import tornado.runtime.ObjectReference;
import tornado.runtime.SchedulableTask;
import tornado.runtime.TornadoRuntime;

public abstract class ExecutableTask<D> implements SchedulableTask {

	protected final Object[] args;

	protected final Access[] argumentsAccess;

	protected double compileTime;

	protected DomainTree domainTree;
	protected Event event;
	protected final Meta meta;

	protected final Method method;
	protected final Object[] resolvedArgs;
	protected boolean shouldCompile;

	protected CallStack<D> stack;

	protected Access thisAccess;
	protected final Object thisObject;

	public ExecutableTask(Method method, Object thisObject, Object... args) {
		this.method = method;
		this.thisObject = thisObject;
		this.args = args;
		this.shouldCompile = true;
		this.meta = new Meta();

		this.resolvedArgs = copyToArguments();

		argumentsAccess = new Access[resolvedArgs.length];
		readTaskMetadata();
	}

	public abstract void compile();

	protected Object[] copyToArguments() {
		final int argOffset = (Modifier.isStatic(method.getModifiers())) ? 0
				: 1;
		final int numArgs = args.length + argOffset;
		final Object[] arguments = new Object[numArgs];

		if (!Modifier.isStatic(method.getModifiers())) {

			final ObjectReference<?, ?> ref = TornadoRuntime
					.resolveObject(thisObject);
			arguments[0] = ref;
		}

		for (int i = 0; i < args.length; i++) {
			final Object object = args[i];
			if (object != null && !RuntimeUtilities.isBoxedPrimitive(object)
					&& !object.getClass().isPrimitive()) {
				final ObjectReference<?, ?> ref = TornadoRuntime
						.resolveObject(object);
				arguments[i + argOffset] = ref;
				// System.out.printf("task - arg[%d]: 0x%x (device: 0x%x)\n",i,object.hashCode(),ref.getLastWrite().getBuffer().toAbsoluteAddress());
			} else {
				arguments[i + argOffset] = object;
				// System.out.printf("task - arg[%d]: 0x%x\n",i,object.hashCode());
			}
			// System.out.printf("task - arg[%d]: 0x%x (device: 0x%x)\n",i,object.hashCode());
		}
		return arguments;
	}

	public abstract void disableJIT();

	public abstract void execute();

	protected void executeFallback() {
		try {
			method.invoke(thisObject, args);
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Object[] getArguments() {
		return resolvedArgs;
	}

	@Override
	public Access[] getArgumentsAccess() {
		return argumentsAccess;
	}

	public double getCompileTime() {
		return compileTime;
	}

	// public void dumpCode() {
	// for (byte b : activeCode.getCode())
	// System.out.printf("%c", b);
	//
	// }

	@Override
	public DeviceMapping getDeviceMapping() {
		return (meta.hasProvider(DeviceMapping.class)) ? meta
				.getProvider(DeviceMapping.class) : null;
	}

	@Override
	public Event getEvent() {
		return event;
	}

	@Override
	public double getExecutionTime() {
		return event.getExecutionTime();
	}

	public String getMethodName() {
		return method.getName();
	}

	@Override
	public String getName() {
		return "task - " + method.getName();
	}

	@Override
	public double getQueuedTime() {
		return event.getQueuedTime();
	}

	public CallStack<D> getStack() {
		return stack;
	}

	@Override
	public TornadoExecutionStatus getStatus() {
		return event.getStatus();
	}

	@Override
	public double getTotalTime() {
		return event.getTotalTime();
	}

	public abstract void invalidate();

	public abstract void loadFromFile(String filename);

	@Override
	public ExecutableTask<D> mapTo(final DeviceMapping mapping) {
		if (meta.hasProvider(DeviceMapping.class)
				&& meta.getProvider(DeviceMapping.class) == mapping) {
			return this;
		}

		meta.addProvider(DeviceMapping.class, mapping);
		return this;
	}

	@Override
	public Meta meta() {
		return meta;
	}

	protected final void readStaticMethodMetadata() {

		final int paramCount = method.getParameterCount();

		final Annotation[][] paramAnnotations = method
				.getParameterAnnotations();

		for (int i = 0; i < paramCount; i++) {
			Access access = Access.UNKNOWN;
			for (final Annotation an : paramAnnotations[i]) {
				if (an instanceof Read) {
					access = Access.READ;
				} else if (an instanceof ReadWrite) {
					access = Access.READ_WRITE;
				} else if (an instanceof Write) {
					access = Access.WRITE;
				}
				if (access != Access.UNKNOWN) {
					break;
				}
			}
			argumentsAccess[i] = access;
		}
	}

	protected final void readTaskMetadata() {
		if (Modifier.isStatic(method.getModifiers())) {
			readStaticMethodMetadata();
		} else {
			readVirtualMethodMetadata();
		}
	}

	protected final void readVirtualMethodMetadata() {
		final int paramCount = method.getParameterCount();

		Access thisAccess = Access.NONE;
		for (final Annotation an : method.getAnnotatedReceiverType()
				.getAnnotations()) {
			if (an instanceof Read) {
				thisAccess = Access.READ;
			} else if (an instanceof ReadWrite) {
				thisAccess = Access.READ_WRITE;
			} else if (an instanceof Write) {
				thisAccess = Access.WRITE;
			}
			if (thisAccess != Access.UNKNOWN) {
				break;
			}
		}

		argumentsAccess[0] = thisAccess;

		final Annotation[][] paramAnnotations = method
				.getParameterAnnotations();

		for (int i = 0; i < paramCount; i++) {
			Access access = Access.UNKNOWN;
			for (final Annotation an : paramAnnotations[i]) {
				if (an instanceof Read) {
					access = Access.READ;
				} else if (an instanceof ReadWrite) {
					access = Access.READ_WRITE;
				} else if (an instanceof Write) {
					access = Access.WRITE;
				}
				if (access != Access.UNKNOWN) {
					break;
				}
			}
			argumentsAccess[i + 1] = access;
		}

	}

	@Override
	public abstract void schedule();

	@Override
	public abstract void schedule(Event... waitEvents);

	@Override
	public abstract void schedule(List<Event> waitEvents);

	@Override
	public void waitOn() {
		event.waitOn();
	}
}
