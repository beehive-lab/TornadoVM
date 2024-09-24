package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithProfiler extends ExecutionPlanType {

    public WithProfiler(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withProfiler ";
    }
}
