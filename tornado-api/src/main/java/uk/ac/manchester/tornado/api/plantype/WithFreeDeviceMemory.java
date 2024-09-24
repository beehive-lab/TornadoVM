package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithFreeDeviceMemory extends ExecutionPlanType {

    public WithFreeDeviceMemory(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withFreeDeviceMemory ";
    }
}
