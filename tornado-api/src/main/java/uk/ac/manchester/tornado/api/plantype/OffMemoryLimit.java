package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class OffMemoryLimit extends ExecutionPlanType {

    public OffMemoryLimit(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withoutMemoryLimit ";
    }

}
