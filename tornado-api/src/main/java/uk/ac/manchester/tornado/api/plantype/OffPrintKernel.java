package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public final class OffPrintKernel extends ExecutionPlanType {
    public OffPrintKernel(TornadoExecutionPlan parent) {
        super(parent);
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withoutPrintKernel ";
    }
}
