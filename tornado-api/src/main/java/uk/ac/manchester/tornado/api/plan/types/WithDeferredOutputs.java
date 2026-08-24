package uk.ac.manchester.tornado.api.plan.types;

import uk.ac.manchester.tornado.api.ExecutionPlanType;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithDeferredOutputs extends ExecutionPlanType {

    public WithDeferredOutputs(TornadoExecutionPlan parent) {
        super(parent);
    }

}
