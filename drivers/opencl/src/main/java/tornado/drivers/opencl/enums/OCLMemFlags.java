package tornado.drivers.opencl.enums;

public class OCLMemFlags {
	public static final long	CL_MEM_READ_WRITE		= (1 << 0);
	public static final long	CL_MEM_WRITE_ONLY		= (1 << 1);
	public static final long	CL_MEM_READ_ONLY		= (1 << 2);
	public static final long	CL_MEM_USE_HOST_PTR		= (1 << 3);
	public static final long	CL_MEM_ALLOC_HOST_PTR	= (1 << 4);
	public static final long	CL_MEM_COPY_HOST_PTR	= (1 << 5);
	// reserved (1 << 6)
	public static final long	CL_MEM_HOST_WRITE_ONLY	= (1 << 7);
	public static final long	CL_MEM_HOST_READ_ONLY	= (1 << 8);
	public static final long	CL_MEM_HOST_NO_ACCESS	= (1 << 9);
}
