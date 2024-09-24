package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithBatch extends ExecutionPlanType {

    public WithBatch(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withBatch ";
    }
}
