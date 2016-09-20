package tornado.runtime.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.api.Write;
import tornado.common.DeviceMapping;
import tornado.common.SchedulableTask;
import tornado.common.enums.Access;
import tornado.meta.Meta;

public class CompilableTask implements SchedulableTask {

    protected final Object[] args;
    protected final Access[] argumentsAccess;

    protected final Meta meta;

    protected final Method method;
    protected final Object[] resolvedArgs;
    protected boolean shouldCompile;

    protected Access thisAccess;

    public CompilableTask(Method method, Object... args) {
        this.method = method;
        this.args = args;
        this.shouldCompile = true;
        this.meta = new Meta();

        this.resolvedArgs = args;

        argumentsAccess = new Access[resolvedArgs.length];
        readTaskMetadata();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(method.getName()).append("()\n");
        for (int i = 0; i < args.length; i++) {
            sb.append(String.format("arg  : [%s] %s -> %s\n", argumentsAccess[i], args[i], resolvedArgs[i]));
        }

        sb.append("meta : ").append(meta.toString());

        return sb.toString();
    }

    protected Object[] copyToArguments() {
        final int argOffset = (Modifier.isStatic(method.getModifiers())) ? 0
                : 1;
        final int numArgs = args.length + argOffset;
        final Object[] arguments = new Object[numArgs];

        for (int i = 0; i < args.length; i++) {
            final Object object = args[i];
            arguments[i + argOffset] = object;
        }
        return arguments;
    }

    @Override
    public Object[] getArguments() {
        return resolvedArgs;
    }

    @Override
    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

    @Override
    public DeviceMapping getDeviceMapping() {
        return (meta.hasProvider(DeviceMapping.class)) ? meta
                .getProvider(DeviceMapping.class) : null;
    }

    public String getMethodName() {
        return method.getName();
    }

    @Override
    public String getName() {
        return "task - " + method.getName();
    }

    @Override
    public CompilableTask mapTo(final DeviceMapping mapping) {
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

        thisAccess = Access.NONE;
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

    public Method getMethod() {
        return method;
    }
}
