package uk.ac.manchester.tornado.api.plan.types;

import uk.ac.manchester.tornado.api.ExecutionPlanType;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithCUDAGraph extends ExecutionPlanType {

    public WithCUDAGraph(TornadoExecutionPlan parent) {
        super(parent);
    }

}
