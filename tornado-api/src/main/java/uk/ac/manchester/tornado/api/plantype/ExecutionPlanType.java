package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public sealed class ExecutionPlanType extends TornadoExecutionPlan //
        permits OffConcurrentDevices, OffMemoryLimit, OffPrintKernel, OffProfiler, OffThreadInfo, WithWarmUp, WithBatch, WithClearProfiles, WithCompilerFlags, WithConcurrentDevices,
        WithDefaultScheduler, WithDevicePlan, WithDynamicReconfiguration, WithFreeDeviceMemory, WithGridScheduler, WithMemoryLimit, WithPrintKernel, WithProfiler, WithResetDevice, WithThreadInfo {

    TornadoExecutionPlan parent;

    public ExecutionPlanType(TornadoExecutionPlan parent) {
        this.parent = parent;
        this.child = this;
    }

}
