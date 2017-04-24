package tornado.runtime.api;

import com.oracle.graal.phases.util.Providers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Event;
import tornado.common.SchedulableTask;
import tornado.common.TornadoDevice;
import tornado.graal.compiler.TornadoSuitesProvider;
import tornado.runtime.TornadoVM;
import tornado.runtime.graph.*;
import tornado.runtime.sketcher.SketchRequest;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.RuntimeUtilities.isBoxedPrimitiveClass;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public abstract class AbstractTaskGraph {

    private final ExecutionContext graphContext;

    public final static byte D2HCPY = 20; // D2HCPY(device, host, index)
    public final static byte H2DCPY = 21; // H2DCPY(host, device, index)
    public final static byte MODIFY = 30; // HMODIFY(index)
    public final static byte LOAD_REF = 8; // LOAD_REF(index)
    public final static byte LOAD_PRIM = 9; // LOAD_PRIM(index)
    public final static byte LAUNCH = 10; // LAUNCH() (args [, events])
    public final static byte DSYNC = 22; // DSYNC(device)
    public final static byte ARG_LIST = 11; // ARG_LIST(size)
    public final static byte CONTEXT = 12; // FRAME(tasktodevice_index, task_index)

    private byte[] hlcode = new byte[2048];
    private ByteBuffer hlBuffer;

    private GraphCompilationResult result;
    private TornadoVM vm;
    private Event event;

    public AbstractTaskGraph(String name) {
        graphContext = new ExecutionContext(name);

        hlBuffer = ByteBuffer.wrap(hlcode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        result = null;
        event = null;
    }

    public TornadoDevice getDefaultDevice() {
        return graphContext.getDefaultDevice();
    }

    public TornadoDevice getDeviceForTask(String id) {
        return graphContext.getDeviceForTask(id);
    }

    protected void addInner(SchedulableTask task) {
        Providers providers = getTornadoRuntime().getDriver(0).getProviders();
        TornadoSuitesProvider suites = getTornadoRuntime().getDriver(0).getSuitesProvider();

        if (task instanceof CompilableTask) {
            CompilableTask compilableTask = (CompilableTask) task;
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                    .resolveMethod(compilableTask.getMethod());
//                getTornadoExecutor().execute(new SketchRequest(resolvedMethod, compilableTask.meta(), providers, suites.getGraphBuilderSuite(), suites.getSketchTier()));
            new SketchRequest(resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier()).run();

        }

        hlBuffer.put(CONTEXT);
        int globalTaskId = graphContext.getTaskCount();
        hlBuffer.putInt(globalTaskId);
        graphContext.incrGlobalTaskCount();

        int index = graphContext.addTask(task);
        hlBuffer.putInt(index);
//		System.out.printf("inserting: 0x%x 0x%x 0x%x\n", CONTEXT, globalTaskId,
//				index);
        // insert parameters into variable tables

        // create parameter list
        final Object[] args = task.getArguments();
//		for(Object arg: args){
//			System.out.println("- arg: " + arg);
//		}
        hlBuffer.put(ARG_LIST);
        hlBuffer.putInt(args.length);

        for (int i = 0; i < args.length; i++) {
            final Object arg = args[i];
            index = graphContext.insertVariable(arg);
            if (arg.getClass().isPrimitive()
                    || isBoxedPrimitiveClass(arg.getClass())) {
                hlBuffer.put(LOAD_PRIM);
            } else {
                hlBuffer.put(LOAD_REF);
            }
            hlBuffer.putInt(index);
        }

        // launch code
        hlBuffer.put(LAUNCH);
    }

    private void compile() {
//		dump();

        final ByteBuffer buffer = ByteBuffer.wrap(hlcode);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(hlBuffer.position());

//		final long t0 = System.nanoTime();
        final Graph graph = GraphBuilder.buildGraph(graphContext, buffer);
//		final long t1 = System.nanoTime();
        result = GraphCompiler.compile(graph, graphContext);
//		final long t2 = System.nanoTime();
        vm = new TornadoVM(graphContext, result.getCode(), result.getCodeSize());
//		final long t3 = System.nanoTime();

//		System.out.printf("task graph: build graph %.9f s\n",(t1-t0)*1e-9);
//		System.out.printf("task graph: compile     %.9f s\n",(t2-t1)*1e-9);
//		System.out.printf("task graph: vm          %.9f s\n",(t3-t2)*1e-9);
    }

    protected void scheduleInner() {

        if (result == null) {
            graphContext.assignToDevices();
            compile();
        }

        event = vm.execute();

    }

    public void apply(Consumer<SchedulableTask> consumer) {
        graphContext.apply(consumer);
    }

    protected void mapAllToInner(TornadoDevice device) {
        graphContext.mapAllTo(device);
    }

    public void dumpTimes() {
//		System.out.printf("Task Graph: %d tasks\n", events.size());
//		apply(task -> System.out
//				.printf("\t%s: status=%s, execute=%.8f s, total=%.8f s, queued=%.8f s\n",
//						task.getName(), task.getStatus(),
//						task.getExecutionTime(), task.getTotalTime(),
//						task.getQueuedTime()));
        vm.dumpTimes();
    }

    public void dumpProfiles() {
        vm.dumpProfiles();
    }

    public void dumpEvents() {
        vm.dumpEvents();
    }

    public void clearProfiles() {
        vm.clearProfiles();
    }

    public void waitOn() {
        if (event != null) {
            event.waitOn();
        }
    }

    protected void streamInInner(Object... objects) {
        for (Object object : objects) {
            graphContext.getObjectState(object).setStreamIn(true);
        }
    }

    protected void streamOutInner(Object... objects) {
        for (Object object : objects) {
            graphContext.getObjectState(object).setStreamOut(true);
        }
    }

    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s \n",
                humanReadableByteCount(hlBuffer.capacity(), true),
                humanReadableByteCount(hlBuffer.position(), true));
        for (int i = 0; i < hlBuffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i);
            for (int j = 0; j < Math.min(hlBuffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.printf(" ");
                }
                if (j < hlBuffer.position() - i) {
                    System.out.printf("%02x", hlBuffer.get(i + j));
                } else {
                    System.out.printf("..");
                }
            }
            System.out.println();
        }
    }

    public void warmup() {
        if (result == null) {
            graphContext.assignToDevices();
            compile();
        }

        vm.warmup();
    }

    public void invalidateObjects() {
        if (vm != null) {
            vm.invalidateObjects();
        }
    }

}
