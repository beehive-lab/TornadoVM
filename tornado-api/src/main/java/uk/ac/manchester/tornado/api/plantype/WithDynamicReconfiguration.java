package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithDynamicReconfiguration extends ExecutionPlanType {

    public WithDynamicReconfiguration(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withDynamicReconfiguration ";
    }
}
