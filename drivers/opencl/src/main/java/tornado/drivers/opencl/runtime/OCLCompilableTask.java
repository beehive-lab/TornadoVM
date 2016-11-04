package tornado.drivers.opencl.runtime;

import com.oracle.graal.api.meta.ResolvedJavaMethod;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tornado.api.Event;
import tornado.common.DeviceMapping;
import tornado.common.Tornado;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.runtime.api.CompilableTask;

public class OCLCompilableTask extends CompilableTask {

    private OpenCLInstalledCode activeCode;
    private OCLBackend activeBackend;

    private final Map<OCLBackend, OpenCLInstalledCode> codeCache;

    public OCLCompilableTask(Method method, Object thisObject,
            Object... args) {
        super(method, thisObject, args);
        this.codeCache = new HashMap<>();
    }

    public void execute() {
        if (activeCode != null && activeCode.isValid()) {
            executeOnDevice();
        } else {
//            executeFallback();
        }
    }

    @Override
    public CompilableTask mapTo(final DeviceMapping mapping) {
        super.mapTo(mapping);

        activeBackend = ((OCLDeviceMapping) mapping).getBackend();
        if (codeCache.containsKey(activeBackend)) {
            activeCode = codeCache.get(activeBackend);
        }

        return this;
    }

    public void dumpCode() {
        for (byte b : activeCode.getCode()) {
            System.out.printf("%c", b);
        }

    }

    protected void scheduleOnDevice(List<Event> waitEvents) {
//        stack.reset();
        Tornado.debug("scheduling %s...", method.getName());
        Tornado.debug(toString());
//        stack.pushArgs(resolvedArgs, argumentsAccess, waitEvents);

//        event = activeCode.submit((OCLCallStack) stack, domainTree, waitEvents);
//        event.waitOn();
        Tornado.debug("after %s...", method.getName());
        Tornado.debug(toString());
    }

    protected void executeOnDevice() {
        scheduleOnDevice(Collections.emptyList());
//        event.waitOn();
//        stack.getWriteSet().forEach(ref -> ref.read());
    }

    public void schedule() {
        scheduleOnDevice(Collections.emptyList());
    }

    public void schedule(Event... waitEvents) {
        final List<Event> events = new ArrayList<>();
        for (Event event : waitEvents) {
            events.add(event);
        }
        scheduleOnDevice(events);
    }

    public void schedule(List<Event> waitEvents) {
        scheduleOnDevice(waitEvents);
    }

    public void invalidate() {
        activeCode.invalidate();
    }

    public void disableJIT() {
        shouldCompile = false;
    }

    public void loadFromFile(String filename) {
        final Path path = Paths.get(filename);
        guarantee(path.toFile().exists(), "file does not exist: %s", filename);

        final ResolvedJavaMethod resolvedMethod = activeBackend.getProviders()
                .getMetaAccess().lookupJavaMethod(method);
        try {
            final byte[] source = Files.readAllBytes(path);
            activeCode = activeBackend.getCodeCache().addMethod(resolvedMethod,
                    source);

        } catch (IOException e) {
            shouldNotReachHere();
        }

    }

}
