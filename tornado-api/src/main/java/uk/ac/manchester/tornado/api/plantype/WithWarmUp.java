package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithWarmUp extends ExecutionPlanType {

    public WithWarmUp(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return super.toString() + "\n -> withWarmUp ";
    }
}
