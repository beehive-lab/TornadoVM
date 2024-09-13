package uk.ac.manchester.tornado.api;

final class WarmUpPlan extends TornadoExecutionPlan {

    TornadoExecutionPlan parent;

    WarmUpPlan(TornadoExecutionPlan parent) {
        this.parent = parent;
        this.child = this;
    }

    @Override
    public String toString() {
        return super.toString() + "\n -> withWarmUp ";
    }
}
