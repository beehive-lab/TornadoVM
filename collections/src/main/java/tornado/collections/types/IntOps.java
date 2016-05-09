package tornado.collections.types;

public class IntOps {
	public static final float EPSILON = 1e-7f;
	public static final String fmt = "%d";
	public static final String fmt2 = "{%d,%d}";
	public static final String fmt3 = "{%d,%d,%d}";
	public static String	fmt6= "{%d,%d,%d,%d,%d,%d}";
	
	public static final boolean compare(float a, float b){
		return (a == b);
	}
}
