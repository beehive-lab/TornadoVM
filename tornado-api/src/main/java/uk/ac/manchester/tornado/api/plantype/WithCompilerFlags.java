package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithCompilerFlags extends ExecutionPlanType {
    public WithCompilerFlags(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withCompilerFlags ";
    }
}
