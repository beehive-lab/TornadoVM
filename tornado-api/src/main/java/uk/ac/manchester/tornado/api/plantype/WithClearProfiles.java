package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithClearProfiles extends ExecutionPlanType {
    public WithClearProfiles(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withClearProfiles ";
    }
}
