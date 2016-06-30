package tornado.drivers.opencl.runtime;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static tornado.common.Tornado.USE_OPENCL_SCHEDULING;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.CallStack;
import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;
import tornado.common.ObjectBuffer;
import tornado.common.SchedulableTask;
import tornado.common.TornadoInstalledCode;
import tornado.common.exceptions.TornadoInternalError;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.drivers.opencl.mm.OCLByteArrayWrapper;
import tornado.drivers.opencl.mm.OCLDoubleArrayWrapper;
import tornado.drivers.opencl.mm.OCLFloatArrayWrapper;
import tornado.drivers.opencl.mm.OCLIntArrayWrapper;
import tornado.drivers.opencl.mm.OCLLongArrayWrapper;
import tornado.drivers.opencl.mm.OCLShortArrayWrapper;
import tornado.drivers.opencl.mm.OCLObjectWrapper;
import tornado.runtime.EmptyEvent;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.PrebuiltTask;

import com.oracle.graal.api.meta.ResolvedJavaMethod;

public class OCLDeviceMapping implements DeviceMapping {
	private final OCLDevice device;
	private final int deviceIndex;
	private final int platformIndex;

	private final static OCLDriver findDriver() {
		for (int i = 0; i < TornadoRuntime.runtime.getNumDrivers(); i++) {
			if (TornadoRuntime.runtime.getDriver(i) instanceof OCLDriver) {
				return (OCLDriver) TornadoRuntime.runtime.getDriver(i);
			}
		}
		TornadoInternalError.shouldNotReachHere("unable to find OpenCL driver");
		return null;
	}

	public OCLDeviceMapping(final int platformIndex, final int deviceIndex) {
		this.platformIndex = platformIndex;
		this.deviceIndex = deviceIndex;

		final OCLDriver driver = findDriver();
		device = driver.getPlatformContext(platformIndex).devices()
				.get(deviceIndex);

	}

	public OCLDevice getDevice() {
		return device;
	}

	public int getDeviceIndex() {
		return deviceIndex;
	}

	public int getPlatformIndex() {
		return platformIndex;
	}

	public OCLDeviceContext getDeviceContext() {
		return getBackend().getDeviceContext();
	}

	public OCLBackend getBackend() {
		return findDriver().getBackend(platformIndex, deviceIndex);
	}

	public void reset() {
		getBackend().reset();
	}

	@Override
	public String toString() {
		return String.format(device.getName());
	}

	@Override
	public TornadoSchedulingStrategy getPreferedSchedule() {
		if (device.getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_GPU)
			return TornadoSchedulingStrategy.PER_ITERATION;
		else if (device.getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU)
			return TornadoSchedulingStrategy.PER_BLOCK;
		else
			return TornadoSchedulingStrategy.PER_ITERATION;
	}

	@Override
	public boolean isDistibutedMemory() {
		return true;
	}

	@Override
	public void ensureLoaded() {
		final OCLBackend backend = getBackend();
		if (!backend.isInitialised()) {
			backend.init();
		}
	}

	@Override
	public CallStack createStack(int numArgs) {	
		return getDeviceContext().getMemoryManager().createCallStack(numArgs);
	}

	@Override
	public TornadoInstalledCode installCode(SchedulableTask task) {
		if (task instanceof CompilableTask) {
			final CompilableTask executable = (CompilableTask) task;
//			final long t0 = System.nanoTime();
			final ResolvedJavaMethod resolvedMethod = TornadoRuntime.runtime
					.resolveMethod(executable.getMethod());
//			final long t1 = System.nanoTime();
			final OpenCLInstalledCode code = OCLCompiler.compileCodeForDevice(
					resolvedMethod, task.getArguments(), task.meta(),
					(OCLProviders) getBackend().getProviders(), getBackend());
//			final long t2 = System.nanoTime();
//			System.out.printf("resolve : %.9f s\n",(t1-t0)*1e-9);
//			System.out.printf("compiler: %.9f s\n",(t2-t1)*1e-9);
			if (OCLBackend.SHOW_OPENCL) {
				String filename = getFile(executable.getMethodName());
				// Tornado.info("Generated code for device %s - %s\n",
				// deviceContext.getDevice().getName(), filename);
				try {
					PrintWriter fileOut = new PrintWriter(filename);
					String source = new String(code.getCode(), "ASCII");
					fileOut.println(source.trim());
					fileOut.close();
				} catch (UnsupportedEncodingException | FileNotFoundException e) {
					e.printStackTrace();
					// Tornado.warn("Unable to write source to file: %s",
					// e.getMessage());
				}
			}
			return code;
		} else if (task instanceof PrebuiltTask){
			final PrebuiltTask executable = (PrebuiltTask) task;
			final Path path = Paths.get(executable.getFilename());
	        guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
	        try {
	            final byte[] source = Files.readAllBytes(path);
	            return getBackend().getCodeCache().addMethod(null,executable.getEntryPoint(),
	                    source);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		}

		TornadoInternalError.shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
		return null;
	}

	private static String getFile(String name) {
		return String.format("%s/%s.cl", OCLBackend.OPENCL_PATH.trim(), name.trim());
	}

	private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg,
			OCLDeviceContext device) throws TornadoOutOfMemoryException {
//		System.out.printf("creating bufffer: type=%s, arg=%s, device=%s\n",type.getSimpleName(),arg,device);
		ObjectBuffer result = null;
		if (type.isArray()) {

			if (type == int[].class) {
				result = new OCLIntArrayWrapper(device);
			} else if (type == short[].class){
				result = new OCLShortArrayWrapper(device);
			} else if (type == byte[].class){
				result = new OCLByteArrayWrapper(device);
			} else if (type == float[].class) {
				result = new OCLFloatArrayWrapper(device);
			} else if (type == double[].class) {
				result = new OCLDoubleArrayWrapper(device);
			} else if (type == long[].class) {
				result = new OCLLongArrayWrapper(device);
			}

		} else if (!type.isPrimitive() && !type.isArray()) {
//			System.out.println("creating object wrapper...good");
			result = new OCLObjectWrapper(device, arg);
		}

		TornadoInternalError.guarantee(result != null,
				"Unable to create buffer for object: " + type);
		return result;
	}

	@Override
	public Event ensureAllocated(Object object, DeviceObjectState state) {
		if (!state.hasBuffer()) {
			try {
				final ObjectBuffer buffer = createDeviceBuffer(
						object.getClass(), object, getDeviceContext());
				buffer.allocate(object);
				state.setBuffer(buffer);
				
				final Class<?> type = object.getClass();
				if(type == float[].class){
					
				} else if (type == double.class){
					
				} else if (!type.isArray()){
					buffer.write(object);
				}
				
				state.setValid(true);
			} catch (TornadoOutOfMemoryException e) {
				e.printStackTrace();
			}
		}

		if (!state.isValid()) {
			try {
				state.getBuffer().allocate(object);
				final Class<?> type = object.getClass();
				if(type == float[].class){
					
				} else if (type == double.class){
					
				} else if (!type.isArray()){
					state.getBuffer().write(object);
				}
				state.setValid(true);
			} catch (TornadoOutOfMemoryException e) {
				e.printStackTrace();
			}
		}
		return new EmptyEvent();
	}

	@Override
	public Event ensurePresent(Object object, DeviceObjectState state) {
		if(!state.isValid()){
			ensureAllocated(object, state);
		}
		
		if (!state.hasContents()) {
			state.setContents(true);
			return state.getBuffer().enqueueWrite(object);
		}
		return new EmptyEvent();
	}

	@Override
	public Event streamIn(Object object, DeviceObjectState state) {
		if(!state.isValid()){
			ensureAllocated(object, state);
		}
		
		state.setContents(true);
		return state.getBuffer().enqueueWrite(object);

	}

	@Override
	public Event streamOut(Object object, DeviceObjectState state,
			List<Event> list) {
		TornadoInternalError.guarantee(state.isValid(), "invalid variable");

		return state.getBuffer().enqueueReadAfterAll(object, list);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof OCLDeviceMapping){
			final OCLDeviceMapping other = (OCLDeviceMapping) obj;
			return (other.deviceIndex == deviceIndex && other.platformIndex == platformIndex) ? true : false;
		}
		return false;
	}

	@Override
	public void flush() {
		getDeviceContext().sync();
	}
	
	

}
