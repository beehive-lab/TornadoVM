package uk.ac.manchester.tornado.drivers.spirv.runtime;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVProxy;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SPIRVTornadoDevice implements TornadoAcceleratorDevice {

    private static final boolean BENCHMARKING_MODE = Boolean.parseBoolean(System.getProperties().getProperty("tornado.benchmarking", "False"));

    private SPIRVDevice device;
    private static SPIRVDriver driver = null;
    private int deviceIndex;
    private int platformIndex;

    public SPIRVTornadoDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        device = SPIRVProxy.getPlatform(platformIndex).getDevice(deviceIndex);
    }

    public SPIRVTornadoDevice(SPIRVDevice lowLevelDevice) {
        this.platformIndex = lowLevelDevice.getPlatformIndex();
        this.deviceIndex = lowLevelDevice.getDeviceIndex();
        device = lowLevelDevice;
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return null;
    }

    @Override
    public CallStack createStack(int numArgs) {
        return null;
    }

    @Override
    public ObjectBuffer createBuffer(int[] buffer) {
        return null;
    }

    @Override
    public ObjectBuffer createOrReuseBuffer(int[] arr) {
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        return null;
    }

    @Override
    public boolean isFullJITMode(SchedulableTask task) {
        return false;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task) {
        return new int[0];
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value) {
        return new int[0];
    }

    @Override
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, DeviceObjectState objectState) {
        return new int[0];
    }

    @Override
    public int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex) {
        return 0;
    }

    @Override
    public boolean checkAtomicsParametersForTask(SchedulableTask task) {
        return false;
    }

    @Override
    public void enableThreadSharing() {

    }

    @Override
    public void setAtomicRegion(ObjectBuffer bufferAtomics) {

    }

    @Override
    public int ensureAllocated(Object object, long batchSize, TornadoDeviceObjectState state) {
        return 0;
    }

    @Override
    public List<Integer> ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long batchSize, long hostOffset) {
        return null;
    }

    @Override
    public List<Integer> streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        return null;
    }

    @Override
    public int streamOut(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        return 0;
    }

    @Override
    public int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        return 0;
    }

    @Override
    public Event resolveEvent(int event) {
        return null;
    }

    @Override
    public void ensureLoaded() {

    }

    @Override
    public void flushEvents() {

    }

    @Override
    public int enqueueBarrier() {
        return 0;
    }

    @Override
    public int enqueueBarrier(int[] events) {
        return 0;
    }

    @Override
    public int enqueueMarker() {
        return 0;
    }

    @Override
    public int enqueueMarker(int[] events) {
        return 0;
    }

    @Override
    public void sync() {

    }

    @Override
    public void flush() {

    }

    @Override
    public void reset() {

    }

    @Override
    public void dumpEvents() {

    }

    @Override
    public void dumpMemory(String file) {

    }

    @Override
    public String getDeviceName() {
        return "spirv-" + device.getDeviceIndex();
    }

    @Override
    public String getDescription() {
        return String.format("%s %s", device.getName(), device.getTornadoDeviceType());
    }

    @Override
    public String getPlatformName() {
        return device.getPlatformName();
    }

    @Override
    public TornadoDeviceContext getDeviceContext() {
        return device.getSPIRVContext().getDeviceContext(deviceIndex);
    }

    @Override
    public SPIRVDevice getPhysicalDevice() {
        return device;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return null;
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        return device.getTornadoDeviceType();
    }

    @Override
    public long getMaxAllocMemory() {
        return device.getMaxAllocMemory();
    }

    @Override
    public long getMaxGlobalMemory() {
        return device.getDeviceGlobalMemorySize();
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return device.getDeviceLocalMemorySize();
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return device.getDeviceMaxWorkgroupDimensions();
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return device.getDeviceOpenCLCVersion();
    }

    @Override
    public Object getDeviceInfo() {
        return null;
    }

    @Override
    public int getDriverIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getDriverIndex(SPIRVDriver.class);
    }

    @Override
    public Object getAtomic() {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.SPIRV;
    }

    @Override
    public String toString() {
        return device.getName();
    }

}
