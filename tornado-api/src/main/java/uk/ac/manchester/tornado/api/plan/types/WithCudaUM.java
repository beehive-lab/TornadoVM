package uk.ac.manchester.tornado.api.plan.types;

import uk.ac.manchester.tornado.api.ExecutionPlanType;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithCudaUM extends ExecutionPlanType {

    public WithCudaUM(TornadoExecutionPlan parent) {
        super(parent);
    }

}
