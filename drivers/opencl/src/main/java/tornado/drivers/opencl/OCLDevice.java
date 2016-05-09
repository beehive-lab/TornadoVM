package tornado.drivers.opencl;


import tornado.common.RuntimeUtilities;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLDeviceInfo;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.enums.OCLLocalMemType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class OCLDevice extends TornadoLogger {
	private final long			id;
	private final int 			index;

	private final ByteBuffer	buffer;

	public OCLDevice(int index, long id) {
		this.index = index;
		this.id = id;
		this.buffer = ByteBuffer.allocate(8192);
		this.buffer.order(OpenCL.BYTE_ORDER);
	}

	static {
		System.loadLibrary(OpenCL.OPENCL_LIBRARY);
	}

	native static void clGetDeviceInfo(long id, int info, byte[] buffer);

	public long getId() {
		return id;
	}
	
	public int getIndex(){
		return index;
	}

	public OCLDeviceType getDeviceType() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_TYPE.getValue(),
				buffer.array());

		long type = buffer.getLong();
		return OCLDeviceType.toDeviceType(type);
	}

	public int getVendorId() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_VENDOR_ID.getValue(),
				buffer.array());

		return buffer.getInt();
	}

	public int getMemoryBaseAlignment(){
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN.getValue(),
				buffer.array());

		return buffer.getInt();
	}
	
	public boolean isAvailable() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_AVAILABLE.getValue(),
				buffer.array());

		return (buffer.getInt() == 1) ? true : false;
	}

	public String getName() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_NAME.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}
	
	public String getVendor() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_VENDOR.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}

	public String getDriverVersion() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DRIVER_VERSION.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}
	
	public String getDeviceVersion() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_VERSION.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}
	
	public String getOpenCLCVersion() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_OPENCL_C_VERSION.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}


	
	public String getExtensions() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_EXTENSIONS.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "unknown";
		}
		return name.trim();
	}

	public int getMaxComputeUnits() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id,
				OCLDeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS.getValue(),
				buffer.array());

		return buffer.getInt();
	}

	public int getMaxClockFrequency() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id,
				OCLDeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY.getValue(),
				buffer.array());

		return buffer.getInt();
	}

	public long getGlobalMemorySize() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE.getValue(),
				buffer.array());

		return buffer.getLong();
	}

	public long getLocalMemorySize() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE.getValue(),
				buffer.array());

		return buffer.getLong();
	}

	public int getMaxWorkItemDimensions() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,
				OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS.getValue(),
				buffer.array());

		return buffer.getInt();
	}

	public long[] getMaxWorkItemSizes() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		final int elements = getMaxWorkItemDimensions();
		
		clGetDeviceInfo(id,
				OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES.getValue(),
				buffer.array());
		
		buffer.rewind();

		final long[] sizes = new long[elements];
		for (int i = 0; i < elements; i++){
			sizes[i] = buffer.getLong();
		}

		return sizes;
	}

	public long getMaxWorkGroupSize() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,
				OCLDeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE.getValue(),
				buffer.array());

		return buffer.getLong();
	}
	
	

	public int getDeviceAddressBits(){
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,OCLDeviceInfo.CL_DEVICE_ADDRESS_BITS.getValue(),buffer.array());
		return buffer.getInt();
	}
	
	public boolean hasUnifiedMemory(){
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,OCLDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue(),buffer.array());
		return buffer.getInt() == OpenCL.CL_TRUE ? true : false;
	}
	
	public OCLLocalMemType getLocalMemoryType(){
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,OCLDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue(),buffer.array());
		return OCLLocalMemType.toLocalMemType(buffer.getInt());
	}
	
	boolean isLittleEndian(){
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id,OCLDeviceInfo.CL_DEVICE_ENDIAN_LITTLE.getValue(),buffer.array());
		return buffer.getInt() == OpenCL.CL_TRUE ? true : false;
	}
	
	public int getWordSize(){
		return getDeviceAddressBits() >> 3;
	}
	
	public ByteOrder getByteOrder(){
		return isLittleEndian()? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("id=0x%x, name=%s, type=%s, available=%s", id,
				getName(), getDeviceType().toString(), isAvailable()));
		// sb.append(String.format("freq=%s, max compute units=%d\n",
		// RuntimeUtilities.humanReadableFreq(getMaxClockFrequency()),
		// getMaxComputeUnits()));
		// sb.append(String.format("global mem. size=%s, local mem. size=%s\n",
		// RuntimeUtilities.humanReadableByteCount(getGlobalMemorySize(),
		// false), RuntimeUtilities.humanReadableByteCount(
		// getLocalMemorySize(), false)));
		// sb.append(String.format("extensions=%s", getExtensions()));
		return sb.toString();
	}

	public Object toVerboseString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("id=0x%x, name=%s, type=%s, available=%s\n",
				id, getName(), getDeviceType().toString(), isAvailable()));
		sb.append(String.format("freq=%s, max compute units=%d\n",
				RuntimeUtilities.humanReadableFreq(getMaxClockFrequency()),
				getMaxComputeUnits()));
		sb.append(String.format("global mem. size=%s, local mem. size=%s\n",
				RuntimeUtilities.humanReadableByteCount(getGlobalMemorySize(),
						false), RuntimeUtilities.humanReadableByteCount(
						getLocalMemorySize(), false)));
		 sb.append(String.format("extensions:\n"));
		 for(String extension : getExtensions().split(" "))
			 sb.append("\t" + extension + "\n");
		 sb.append(String.format("unified memory   : %s\n", hasUnifiedMemory()));
		 sb.append(String.format("device vendor    : %s\n", getVendor()));
		 sb.append(String.format("device version   : %s\n", getDeviceVersion()));
		 sb.append(String.format("driver version   : %s\n", getDriverVersion()));
		 sb.append(String.format("OpenCL C version : %s\n", getOpenCLCVersion()));
		 sb.append(String.format("Endianess        : %s\n", isLittleEndian()? "little" : "big"));
		return sb.toString();
	}

	public String getVersion() {
		Arrays.fill(buffer.array(), (byte) 0);
		buffer.clear();
		
		clGetDeviceInfo(id, OCLDeviceInfo.CL_DEVICE_VERSION.getValue(),
				buffer.array());
		String name;
		try {
			name = new String(buffer.array(), "ASCII");
		} catch (UnsupportedEncodingException e) {
			name = "OpenCL 0.0";
		}
		return name.trim();
	}
}
