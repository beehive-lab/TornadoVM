package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class OffThreadInfo extends ExecutionPlanType {
    public OffThreadInfo(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withoutThreadInfo ";
    }
}
