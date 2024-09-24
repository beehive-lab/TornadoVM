package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public sealed class ExecutionPlanType extends TornadoExecutionPlan //
        permits OffConcurrentDevices, OffMemoryLimit, OffPrintKernel, //
        OffProfiler, OffThreadInfo, WithWarmUp, WithBatch, WithClearProfiles,  //
        WithCompilerFlags, WithConcurrentDevices, WithDefaultScheduler, //
        WithDevicePlan, WithDynamicReconfiguration, WithFreeDeviceMemory, //
        WithGridScheduler, WithMemoryLimit, WithPrintKernel, WithProfiler, //
        WithResetDevice, WithThreadInfo {

    TornadoExecutionPlan parentLink;

    public ExecutionPlanType(TornadoExecutionPlan parent) {

        // Set link between the previous action (parent) and the new one
        this.parentLink = parent;

        // Copy the reference for the executor
        this.tornadoExecutor = parent.getTornadoExecutor();

        // Copy the reference for the execution frame
        this.executionFrame = parent.getExecutionFrame();

        // Copy the reference for the executor list
        this.executorList = parent.getExecutionList();

        // Set child reference to this instance
        this.child = this;
    }

}
