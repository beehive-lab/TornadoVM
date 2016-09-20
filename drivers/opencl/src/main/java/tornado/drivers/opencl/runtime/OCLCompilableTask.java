package tornado.drivers.opencl.runtime;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

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
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.mm.OCLCallStack;
import tornado.meta.domain.DomainTree;
import tornado.runtime.api.CompilableTask;

import com.oracle.graal.api.meta.ResolvedJavaMethod;

public class OCLCompilableTask extends CompilableTask {

    private OpenCLInstalledCode activeCode;
    private OCLBackend activeBackend;

    private final Map<OCLBackend, OpenCLInstalledCode> codeCache;


    public OCLCompilableTask(Method method, Object thisObject,
            Object... args) {
    	super(method,thisObject,args);
        this.codeCache = new HashMap<OCLBackend, OpenCLInstalledCode>();
    }

    public void execute() {
        if (activeCode != null && activeCode.isValid()) {
            executeOnDevice();
        } else {
//            executeFallback();
        }
    }

//    public void compile() {
//        long start = System.nanoTime();
//        activeCode = activeBackend.compile(method, resolvedArgs,
//                argumentsAccess, meta);
//        long end = System.nanoTime();
//
//        compileTime = RuntimeUtilities.elapsedTimeInSeconds(start, end);
//
//        domainTree = meta.getProvider(DomainTree.class);
//
//        stack = activeBackend.getDeviceContext().getMemoryManager()
//                .createCallStack(resolvedArgs.length);
//
//        // stack.pushArgs(arguments, argumentsAccess, Collections.emptyList());
//
//        Tornado.info("stack: %s", stack.toString());
//    }

    public CompilableTask mapTo(final DeviceMapping mapping) {
    	super.mapTo(mapping);

    	activeBackend = ((OCLDeviceMapping)mapping).getBackend();
        if (codeCache.containsKey(activeBackend))
            activeCode = codeCache.get(activeBackend);
//        else if (shouldCompile)
//            compile();

        return this;
    }

    public void dumpCode() {
        for (byte b : activeCode.getCode())
            System.out.printf("%c", b);

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
        final List<Event> events = new ArrayList<Event>();
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

        // System.out.printf("load file: resolved=%s, task=%s\n",
        // action.isResolved(), action.method().getName());
        final ResolvedJavaMethod resolvedMethod = activeBackend.getProviders()
                .getMetaAccess().lookupJavaMethod(method);
        try {
            final byte[] source = Files.readAllBytes(path);
            activeCode = activeBackend.getCodeCache().addMethod(resolvedMethod,
                    source);
//            domainTree = meta.getProvider(DomainTree.class);
//            stack = activeBackend.getDeviceContext().getMemoryManager()
//                    .createCallStack(resolvedArgs.length);

            Tornado.debug("building call stack for %s...", method.getName());
            // stack.pushArgs(arguments, argumentsAccess,
            // Collections.emptyList());
            // Tornado.debug("execute task %s: call stack @ 0x%x\n",action.method().getName(),((OCLCallStack)
            // stack).toRelativeAddress());

//            Tornado.info("stack: %s", stack.toString());
        } catch (IOException e) {
            TornadoInternalError.shouldNotReachHere();
        }

    }

}
