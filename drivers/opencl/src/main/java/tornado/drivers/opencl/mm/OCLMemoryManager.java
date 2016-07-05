package tornado.drivers.opencl.mm;

import java.lang.reflect.Method;
import tornado.api.Parallel;
import tornado.api.Write;
import tornado.common.DeviceMapping;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.enums.OCLMemFlags;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;
import tornado.runtime.TornadoRuntime;

public class OCLMemoryManager extends TornadoLogger {

    private final long callStackLimit;
    private long callStackPosition;
    private long deviceBufferAddress;
    private final OCLDeviceContext deviceContext;

    private long buffer;
    private long heapLimit;

    private long heapPosition;

    private boolean initialised;
    
    private OpenCLInstalledCode initFP64Code;
    private OpenCLInstalledCode initFP32Code;
    private OpenCLInstalledCode initU32Code;
	private OCLCallStack initCallStack;
	private DomainTree initThreads;
	private Meta initMeta;

    public OCLMemoryManager(final OCLDeviceContext device) {

        deviceContext = device;
        callStackLimit = 8192;
        initialised = false;

        reset();
    }
    
    private void initFP64(@Write double[] data, int count){
    	for(@Parallel int i=0;i<count;i++)
    		data[i] = 0;
    }
    
    private void initFP32(@Write float[] data, int count){
    	for(@Parallel int i=0;i<count;i++)
    		data[i] = -1f;
    }
    
    private void initU32(@Write int[] data, int count){
    	for(@Parallel int i=0;i<count;i++)
    		data[i] = 0;
    }
    
    private Method getMethod(final String name, Class<?> type1) {
		Method method = null;
		try {
			method = this.getClass().getDeclaredMethod(name, type1, int.class);
			method.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException e) {
			Tornado.fatal("unable to find " + name + " method: " + e.getMessage());
		}
		return method;
	}

    private void createMemoryInitializers(final OCLBackend backend){
    	initThreads = new DomainTree(1);
    	initMeta = new Meta();
    	initMeta.addProvider(DeviceMapping.class, backend.getDeviceContext().asMapping());
    	
//    	initFP64Code = OCLCompiler.compileCodeForDevice(
//				TornadoRuntime.resolveMethod(getMethod("initFP64",double[].class)), null, initMeta, (OCLProviders) backend.getProviders(), backend);
    	initFP32Code = OCLCompiler.compileCodeForDevice(
				TornadoRuntime.runtime.resolveMethod(getMethod("initFP32",float[].class)), null, initMeta, (OCLProviders) backend.getProviders(), backend);
    	initU32Code = OCLCompiler.compileCodeForDevice(
				TornadoRuntime.runtime.resolveMethod(getMethod("initU32",int[].class)), null, initMeta, (OCLProviders) backend.getProviders(), backend);
    	initCallStack = createCallStack(4);
    	
    }
    
    public void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s", deviceBufferAddress,
                RuntimeUtilities.humanReadableByteCount(heapLimit, true),
                deviceContext.getDevice().getName());
    }

    
    public long getHeapSize(){
        return heapLimit - callStackLimit;
    }
    
    private static final long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address
                + (alignment - address % alignment);
    }

    public long tryAllocate(final Class<?> type, final long bytes, final int headerSize, int alignment)
            throws TornadoOutOfMemoryException {
        long offset = heapPosition;
        if (heapPosition + bytes < heapLimit) {
            heapPosition = align(heapPosition + bytes, alignment);
            
//            final long byteCount = bytes - headerSize;
//            if(type != null && type.isArray() && RuntimeUtilities.isPrimitiveArray(type)){
//            	if(type == double[].class ){
//            		initialiseMemory(initFP64Code,offset + headerSize, (int) (byteCount / 8));
//            	} else if(type == float[].class){
//            		initialiseMemory(initFP32Code,offset + headerSize, (int) (byteCount / 4));
//            	} else {
//            		TornadoInternalError.guarantee(byteCount % 4 == 0, "array is not divisible by 4");
//            		initialiseMemory(initU32Code,offset + headerSize, (int) (byteCount / 4));
//            	}
//            } else {
//            	TornadoInternalError.guarantee(byteCount % 4 == 0, "array is not divisible by 4");
//            	initialiseMemory(initU32Code,offset + headerSize, (int) (byteCount/4));
//            }
            
            
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on device: "
                    + deviceContext.getDevice().getName());
        }

        return offset;
    }

    
	private void initialiseMemory(OpenCLInstalledCode code, long offset, int count) {
		if(count <= 0) return;
		
		initCallStack.reset();
		
		initCallStack.putLong(offset);
		initCallStack.putInt(count);
		
		final Meta meta = new Meta();
		
		
		initThreads.set(0, new IntDomain(0,1,count));
		meta.setDomain(initThreads);
//		System.out.println("init threads: " + initThreads.toString());
		
		code.execute(initCallStack, meta);
		
	}

	public OCLCallStack createCallStack(final int maxArgs) {

        OCLCallStack callStack = new OCLCallStack(callStackPosition, maxArgs,
                deviceContext);

        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(),
                    32);
        } else {
            callStack = null;
            Tornado.fatal("Out of call-stack memory");
            System.exit(-1);
        }

        return callStack;
    }

    public long getBytesRemaining() {
        return heapLimit - heapPosition;
    }

    /***
     * Returns sub-buffer that can be use to access a region managed by the
     * memory manager.
     *
     * @param offset
     *            offset within the memory managers heap
     * @param length
     *            size in bytes of the sub-buffer
     * @return
     */
    public OCLByteBuffer getSubBuffer(final int offset, final int length) {
        return new OCLByteBuffer(deviceContext, offset, length);
    }

    public void allocateRegion(long numBytes) {

        /*
         * Allocate space on the device
         */
        heapLimit = numBytes;
        buffer = deviceContext.getPlatformContext().createBuffer(
                OCLMemFlags.CL_MEM_READ_WRITE, numBytes);
    }

    public void init(OCLBackend backend, long address) {
        deviceBufferAddress = address;
        initialised = true;
        Tornado.info("Located heap @ 0x%x (%s) on %s", deviceBufferAddress,
                RuntimeUtilities.humanReadableByteCount(heapLimit, true),
                deviceContext.getDevice().getName());
        
//        createMemoryInitializers(backend);
    }

    public long toAbsoluteAddress() {
        return deviceBufferAddress;
    }

    public long toAbsoluteDeviceAddress(final long address) {
        long result = address;
        
        guarantee(address + deviceBufferAddress >= 0, "absolute address may have wrapped arround: %d + %d = %d", address,deviceBufferAddress,address+deviceBufferAddress);
        result += deviceBufferAddress;

        return result;
    }

    public long toBuffer() {
        return buffer;
    }

    public long toRelativeAddress() {
        return 0;
    }

    public long toRelativeDeviceAddress(final long address) {
        long result = address;
        if (!(Long.compareUnsigned(address, deviceBufferAddress) < 0 || Long
                .compareUnsigned(address, (deviceBufferAddress + heapLimit)) > 0)) {
            result -= deviceBufferAddress;
        }
        return result;
    }

    public boolean isInitialised() {
        return initialised;
    }
}
