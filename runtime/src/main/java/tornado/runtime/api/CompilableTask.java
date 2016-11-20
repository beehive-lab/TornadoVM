package tornado.runtime.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import tornado.common.DeviceMapping;
import tornado.common.SchedulableTask;
import tornado.common.enums.Access;
import tornado.meta.Meta;

import static com.oracle.graal.compiler.common.util.Util.guarantee;

public class CompilableTask implements SchedulableTask {

    protected final Object[] args;
//    protected final Access[] argumentsAccess;

    protected Meta meta;

    protected final Method method;
    protected final Object[] resolvedArgs;
    protected boolean shouldCompile;

    protected Access thisAccess;

    public CompilableTask(Method method, Object... args) {
        this.method = method;
        this.args = args;
        this.shouldCompile = true;
        this.resolvedArgs = args;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(method.getName()).append("()\n");
        Access[] argumentsAccess = meta.getArgumentsAccess();
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
        return meta.getArgumentsAccess();
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
        meta = mapping.createMeta(method);
        if (meta.hasProvider(DeviceMapping.class)
                && meta.getProvider(DeviceMapping.class) == mapping) {
            return this;
        }

        meta.addProvider(DeviceMapping.class, mapping);
        return this;
    }

    @Override
    public Meta meta() {
        guarantee(meta != null, "task needs to be assigned first");
        return meta;
    }

    public Method getMethod() {
        return method;
    }
}
