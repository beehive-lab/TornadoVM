package tornado.collections.types;

public class ByteOps {
	public static final float EPSILON = 1e-7f;
	public static final String fmt = "%3d";
	public static final String fmt2 = "{%3d,%3d}";
	public static final String fmt3 = "{%3d,%3d,%3d}";
	public static final String fmt4 = "{%3d,%3d,%3d,%3d}";
	
	public static final boolean compare(byte a, byte b){
		return Byte.compare(a, b) == 0;
	}
}
