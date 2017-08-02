package tornado.api.meta;

import tornado.common.TornadoDevice;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static tornado.api.meta.MetaDataUtils.resolveDevice;
import static tornado.common.Tornado.getProperty;

public abstract class AbstractMetaData {

    private final String id;
    private TornadoDevice device;
    private boolean shouldRecompile;
    private final boolean isDeviceDefined;
    private boolean deviceLoaded = false;

    public TornadoDevice getDevice() {
        if (device == null && !deviceLoaded) {
            device = resolveDevice(getProperty(id + ".device", "0:0"));
            deviceLoaded = true;
        }
        return device;
    }

    public void setDevice(TornadoDevice device) {
        this.device = device;
    }

    public String getCpuConfig() {
        return cpuConfig;
    }

    public final String getId() {
        return id;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean shouldDumpEvents() {
        return dumpEvents;
    }

    public boolean shouldDumpProfiles() {
        return dumpProfiles;
    }

    public boolean shouldDumpSchedule() {
        return dumpTaskSchedule;
    }

    public boolean shouldDebugKernelArgs() {
        return debugKernelArgs;
    }

    public boolean shouldPrintCompileTimes() {
        return printCompileTimes;
    }

    public boolean shouldRecompile() {
        return shouldRecompile;
    }

    public void setRecompiled() {
        shouldRecompile = false;
    }

    public String getOpenclCompilerFlags() {
        return openclCompilerFlags;
    }

    public int getOpenclGpuBlockX() {
        return openclGpuBlockX;
    }

    public int getOpenclGpuBlock2DX() {
        return openclGpuBlock2DX;
    }

    public int getOpenclGpuBlock2DY() {
        return openclGpuBlock2DY;
    }

    public boolean shouldUseOpenclRelativeAddresses() {
        return openclUseRelativeAddresses;
    }

    public boolean enableOpenclBifs() {
        return openclEnableBifs;
    }

    public boolean shouldUseOpenclScheduling() {
        return openclUseScheduling;
    }

    public boolean shouldUseOpenclWaitActive() {
        return openclWaitActive;
    }

    public boolean shouldUseVmWaitEvent() {
        return vmWaitEvent;
    }

    public boolean enableExceptions() {
        return enableExceptions;
    }

    public boolean enableProfiling() {
        return enableProfiling;
    }

    public boolean enableOooExecution() {
        return enableOooExecution;
    }

    public boolean shouldUseOpenclBlockingApiCalls() {
        return openclUseBlockingApiCalls;
    }

    public boolean enableParallelization() {
        return enableParallelization;
    }

    public boolean enableVectors() {
        return enableVectors;
    }

    public boolean enableMemChecks() {
        return enableMemChecks;
    }

    public boolean enableThreadCoarsener() {
        return useThreadCoarsener;
    }

    public boolean enableAutoParallelisation() {
        return enableAutoParallelisation;
    }

    public boolean shouldUseVMDeps() {
        return vmUseDeps;
    }

    /*
     * Forces the executing kernel to output its arguements before execution
     */
    private final boolean debug;
    private final boolean dumpEvents;
    private final boolean dumpProfiles;
    private final boolean debugKernelArgs;
    private final boolean printCompileTimes;
//    private final boolean forceAllToGpu;
    private boolean isOpenclCompilerFlagsDefined;
    private String openclCompilerFlags;
    private final boolean isOpenclGpuBlockXDefined;
    private final int openclGpuBlockX;
    private final boolean isOpenclGpuBlock2DXDefined;
    private final int openclGpuBlock2DX;
    private final boolean isOpenclGpuBlock2DYDefined;
    private final int openclGpuBlock2DY;
    private final boolean openclUseRelativeAddresses;
    private final boolean openclEnableBifs;


    /*
     * Allows the OpenCL driver to select the size of local work groups
     */
    private final boolean openclUseScheduling;
    private final boolean openclWaitActive;
    private final boolean vmWaitEvent;
    private final boolean enableExceptions;
    private final boolean enableProfiling;
    private final boolean enableOooExecution;
    private final boolean openclUseBlockingApiCalls;
    private final boolean enableParallelization;
    private final boolean enableVectors;
    private final boolean enableMemChecks;
    private final boolean useThreadCoarsener;
    private final boolean dumpTaskSchedule;
    private final boolean vmUseDeps;
    private final boolean coarsenWithCpuConfig;
    private final boolean enableAutoParallelisation;

    private final boolean isCpuConfigDefined;
    private final String cpuConfig;

//    private final boolean useThreadCoarsening;
    public boolean isDeviceDefined() {
        return isDeviceDefined;
    }

    public boolean isOpenclCompilerFlagsDefined() {
        return isOpenclCompilerFlagsDefined;
    }

    public void setOpenclCompilerFlags(String value) {
        openclCompilerFlags = value;
        isOpenclCompilerFlagsDefined = true;
    }

    public boolean isOpenclGpuBlockXDefined() {
        return isOpenclGpuBlockXDefined;
    }

    public boolean isOpenclGpuBlock2DXDefined() {
        return isOpenclGpuBlock2DXDefined;
    }

    public boolean isOpenclGpuBlock2DYDefined() {
        return isOpenclGpuBlock2DYDefined;
    }

    public boolean isCpuConfigDefined() {
        return isCpuConfigDefined;
    }

    public boolean shouldCoarsenWithCpuConfig() {
        return coarsenWithCpuConfig;
    }

    protected static String getDefault(String keySuffix, String id, String defaultValue) {
        if (getProperty(id + "." + keySuffix) == null) {
            return getProperty("tornado" + "." + keySuffix, defaultValue);
        } else {
            return getProperty(id + "." + keySuffix);
        }
    }

    public AbstractMetaData(String id) {
        this.id = id;
        shouldRecompile = true;

        isDeviceDefined = getProperty(id + ".device") != null;

        debugKernelArgs = parseBoolean(getDefault("debug.kernelargs", id, "False"));
        printCompileTimes = parseBoolean(getDefault("debug.compiletimes", id, "False"));
        openclUseRelativeAddresses = parseBoolean(getDefault("opencl.userelative", id, "False"));
        openclWaitActive = parseBoolean(getDefault("opencl.wait.active", id, "False"));
        coarsenWithCpuConfig = parseBoolean(getDefault("coarsener.ascpu", id, "False"));

        /*
         * Allows the OpenCL driver to select the size of local work groups
         */
        openclUseScheduling = parseBoolean(getDefault("opencl.schedule", id, "False"));
        vmWaitEvent = parseBoolean(getDefault("vm.waitevent", id, "False"));
        enableExceptions = parseBoolean(getDefault("exceptions.enable", id, "False"));
        enableProfiling = parseBoolean(getDefault("profiling.enable", id, "False"));
        enableOooExecution = parseBoolean(getDefault("ooo-execution.enable", id, "False"));
        openclUseBlockingApiCalls = parseBoolean(getDefault("opencl.blocking", id, "False"));

        enableParallelization = parseBoolean(getDefault("parallelize", id, "True"));
        enableVectors = parseBoolean(getDefault("vectors.enable", id, "True"));
        openclEnableBifs = parseBoolean(getDefault("bifs.enable", id, "False"));
        debug = parseBoolean(getDefault("debug", id, "False"));
        enableMemChecks = parseBoolean(getDefault("memory.check", id, "False"));
        dumpEvents = parseBoolean(getDefault("events.dump", id, "False"));
        dumpProfiles = parseBoolean(getDefault("profiles.print", id, "false"));
        dumpTaskSchedule = parseBoolean(getDefault("schedule.dump", id, "false"));

        openclCompilerFlags = getDefault("opencl.cflags", id, "-w");
        isOpenclCompilerFlagsDefined = getProperty(id + ".opencl.cflags") != null;

        openclGpuBlockX = parseInt(getDefault("opencl.gpu.block.x", id, "256"));
        isOpenclGpuBlockXDefined = getProperty(id + ".opencl.gpu.block.x") != null;

        openclGpuBlock2DX = parseInt(getDefault("opencl.gpu.block2d.x", id, "4"));
        isOpenclGpuBlock2DXDefined = getProperty(id + ".opencl.gpu.block2d.x") != null;

        openclGpuBlock2DY = parseInt(
                getDefault("opencl.gpu.block2d.y", id, "4"));
        isOpenclGpuBlock2DYDefined = getProperty(id + ".opencl.gpu.block2d.y") != null;

        cpuConfig = getDefault("cpu.config", id, null);
        isCpuConfigDefined = getProperty(id + ".cpu.config") != null;
        useThreadCoarsener = Boolean.parseBoolean(getDefault("coarsener", id, "False"));
        enableAutoParallelisation = Boolean.parseBoolean(getDefault("parallelise.auto", id, "False"));
        vmUseDeps = Boolean.parseBoolean(getDefault("vm.deps", id, "False"));
    }

}
