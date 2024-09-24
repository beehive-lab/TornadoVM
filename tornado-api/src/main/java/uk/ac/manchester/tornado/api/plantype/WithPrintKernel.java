package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class WithPrintKernel extends ExecutionPlanType {
    public WithPrintKernel(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withPrintKernel ";
    }
}
