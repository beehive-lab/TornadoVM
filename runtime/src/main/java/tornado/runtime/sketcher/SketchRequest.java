package tornado.runtime.sketcher;

import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.graal.compiler.TornadoSketchTier;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class SketchRequest implements Future<Sketch>, Runnable {

    public final ResolvedJavaMethod resolvedMethod;
    public final Providers providers;
    public final PhaseSuite<HighTierContext> graphBuilderSuite;
    public final TornadoSketchTier sketchTier;
    public Sketch result;

    public SketchRequest(ResolvedJavaMethod resolvedMethod, Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, TornadoSketchTier sketchTier) {
        this.resolvedMethod = resolvedMethod;
        this.providers = providers;
        this.graphBuilderSuite = graphBuilderSuite;
        this.sketchTier = sketchTier;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public void run() {
        TornadoSketcher.buildSketch(this);
    }

    @Override
    public Sketch get() throws InterruptedException, ExecutionException {
        while (!isDone()) {
            Thread.sleep(100);
        }
        return result;
    }

    @Override
    public Sketch get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        unimplemented();
        return null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return result != null;
    }
}
