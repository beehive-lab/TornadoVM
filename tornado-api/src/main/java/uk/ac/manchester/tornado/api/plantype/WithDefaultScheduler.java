package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithDefaultScheduler extends ExecutionPlanType {

    public WithDefaultScheduler(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withDefaultGridScheduler ";
    }
}
