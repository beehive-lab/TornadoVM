package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class OffConcurrentDevices extends ExecutionPlanType {

    public OffConcurrentDevices(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withoutConcurrentDevices ";
    }

}
