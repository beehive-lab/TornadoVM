package uk.ac.manchester.tornado.api;

final class WithDevicePlan extends TornadoExecutionPlan {

    TornadoExecutionPlan parent;

    WithDevicePlan(TornadoExecutionPlan parent) {
        this.parent = parent;
        this.child = this;
    }

    @Override
    public String toString() {
        return parent.toString() + "\n -> withDevice ";
    }
}
