package uk.ac.manchester.tornado.api.plan.types;

import uk.ac.manchester.tornado.api.ExecutionPlanType;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithCUDAPendingLaunchCount extends ExecutionPlanType {

    public WithCUDAPendingLaunchCount(TornadoExecutionPlan parent) {
        super(parent);
    }

}
