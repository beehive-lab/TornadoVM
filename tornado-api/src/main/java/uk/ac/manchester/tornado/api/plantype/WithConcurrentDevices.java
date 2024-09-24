package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithConcurrentDevices extends ExecutionPlanType {

    public WithConcurrentDevices(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withConcurrentDevices ";
    }

}
