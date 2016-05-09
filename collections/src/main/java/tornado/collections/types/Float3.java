package tornado.collections.types;

import java.nio.FloatBuffer;

import tornado.collections.api.Vector;
import tornado.collections.api.Payload;
import tornado.collections.math.TornadoMath;
/**
 * Class that represents a vector of 3x floats
 * e.g. <float,float,float>
 * 
 * @author jamesclarkson
 */
@Vector
public class Float3 implements PrimitiveStorage<FloatBuffer> {

	public static final Class<Float3>	TYPE		= Float3.class;

	/**
	 * backing array
	 */
	@Payload final protected float[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 3;

	public Float3(float[] storage) {
		this.storage = storage;
	}

	public Float3() {
		this(new float[numElements]);
	}

	public Float3(float x, float y, float z) {
		this();
		setX(x);
		setY(y);
		setZ(z);
	}

	public float get(int index) {
		return storage[index];
	}

	public void set(int index, float value) {
		storage[index] = value;
	}

	public void set(Float3 value) {
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
	}

	public float getX() {
		return get(0);
	}

	public float getY() {
		return get(1);
	}

	public float getZ() {
		return get(2);
	}
	
	public float getS0() {
		return get(0);
	}

	public float getS1() {
		return get(1);
	}

	public float getS2() {
		return get(2);
	}

	public void setX(float value) {
		set(0, value);
	}

	public void setY(float value) {
		set(1, value);
	}

	public void setZ(float value) {
		set(2, value);
	}
	
	public void setS0(float value) {
		set(0, value);
	}

	public void setS1(float value) {
		set(1, value);
	}

	public void setS2(float value) {
		set(2, value);
	}

	/**
	 * Duplicates this vector
	 * 
	 * @return
	 */
	public Float3 duplicate() {
		final Float3 vector = new Float3();
		vector.set(this);
		return vector;
	}
	
	

	public String toString(String fmt) {
		return String.format(fmt, getX(), getY(), getZ());
	}

	public String toString() {
		return toString(FloatOps.fmt3);
	}

	/**
	 * Cast vector into a Float2
	 * 
	 * @return
	 */
	public Float2 asFloat2() {
		return new Float2(getX(),getY());
	}
	
	protected static final Float3 loadFromArray(final float[] array, int index){
		final Float3 result = new Float3();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		return result;
	}
	
	protected final void storeToArray(final float[] array, int index){
		array[index] = getX();
		array[index+1] = getY();
		array[index+2] = getZ();
	}

	@Override
	public void loadFromBuffer(FloatBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public FloatBuffer asBuffer() {
		return FloatBuffer.wrap(storage);
	}

	public int size() {
		return numElements;
	}
	
	/***
	 * Operations on Float3 vectors
	 */
	
	
	/*
	 * vector = op( vector, vector )
	 */
	public static Float3 add(Float3 a, Float3 b){
		return new Float3(a.getX() + b.getX(),a.getY() + b.getY(),a.getZ() + b.getZ());
	}
	
	public static Float3 sub(Float3 a, Float3 b){
		return new Float3(a.getX() - b.getX(),a.getY() - b.getY(),a.getZ() - b.getZ());
	}
	
	public static Float3 div(Float3 a, Float3 b){
		return new Float3(a.getX() / b.getX(),a.getY() / b.getY(),a.getZ() / b.getZ());
	}
	
	public static Float3 mult(Float3 a, Float3 b){
		return new Float3(a.getX() * b.getX(),a.getY() * b.getY(),a.getZ() * b.getZ());
	}
	
	public static Float3 min(Float3 a, Float3 b){
		return new Float3(Math.min(a.getX() , b.getX()),Math.min(a.getY() , b.getY()),Math.min(a.getZ() , b.getZ()));
	}
	
	public static Float3 max(Float3 a, Float3 b){
		return new Float3(Math.max(a.getX() , b.getX()),Math.max(a.getY() , b.getY()),Math.max(a.getZ() , b.getZ()));
	}
	
	public static Float3 cross(Float3 a, Float3 b) {
		return new Float3(
		a.getY() * b.getZ() - a.getZ() * b.getY(),
		a.getZ() * b.getX() - a.getX() * b.getZ(),
		a.getX() * b.getY() - a.getY() * b.getX());
	}
	
	/*
	 * vector = op (vector, scalar)
	 */
	
	public static Float3 add(Float3 a, float b){
		return new Float3(a.getX() + b,a.getY() + b,a.getZ() + b);
	}
	
	public static Float3 sub(Float3 a, float b){
		return new Float3(a.getX() - b,a.getY() - b,a.getZ() - b);
	}
	
	public static Float3 mult(Float3 a, float b){
		return new Float3(a.getX() * b,a.getY() * b,a.getZ() * b);
	}
	
	public static Float3 div(Float3 a, float b){
		return new Float3(a.getX() / b,a.getY() / b,a.getZ() / b);
	}
	
	/*
	 * vector = op (vector, vector, vector)
	 */
	
	public static void add(Float3 a, Float3 b, Float3 c){
		c.setX(a.getX() + b.getX());
		c.setY(a.getY() + b.getY());
		c.setZ(a.getZ() + b.getZ());
	}
	
	public static void sub(Float3 a, Float3 b, Float3 c){
		c.setX(a.getX() - b.getX());
		c.setY(a.getY() - b.getY());
		c.setZ(a.getZ() - b.getZ());
	}
	
	public static void mult(Float3 a, Float3 b, Float3 c){
		c.setX(a.getX() * b.getX());
		c.setY(a.getY() * b.getY());
		c.setZ(a.getZ() * b.getZ());
	}
	
	public static void div(Float3 a, Float3 b, Float3 c){
		c.setX(a.getX() / b.getX());
		c.setY(a.getY() / b.getY());
		c.setZ(a.getZ() / b.getZ());
	}
	
	public static void min(Float3 a, Float3 b, Float3 c){
		c.setX(Math.min(a.getX() , b.getX()));
		c.setY(Math.min(a.getY() , b.getY()));
		c.setZ(Math.min(a.getZ() , b.getZ()));
	}
	
	public static void max(Float3 a, Float3 b, Float3 c){
		c.setX(Math.max(a.getX() , b.getX()));
		c.setY(Math.max(a.getY() , b.getY()));
		c.setZ(Math.max(a.getZ() , b.getZ()));
	}
	
	public static void cross(Float3 a, Float3 b, Float3 c) {
		c.setX(a.getY() * b.getZ() - a.getZ() * b.getY());
		c.setY(a.getZ() * b.getX() - a.getX() * b.getZ());
		c.setZ(a.getX() * b.getY() - a.getY() * b.getX());
	}
	
	/*
	 *  inplace src = op (src, scalar)
	 */
	
	public static void inc(Float3 a, float value){
		a.setX(a.getX() + value);
		a.setY(a.getY() + value);
		a.setZ(a.getZ() + value);
	}
	
	
	public static void dec(Float3 a, float value){
		a.setX(a.getX() - value);
		a.setY(a.getY() - value);
		a.setZ(a.getZ() - value);
	}
	
	public static void scaleByInverse(Float3 a, float value){
		scale(a, 1f / value);
	}
	
	
	public static void scale(Float3 a, float value){
		a.setX(a.getX() * value);
		a.setY(a.getY() * value);
		a.setZ(a.getZ() * value);
	}
	
	/*
	 * vector = op(vector)
	 */
	public static Float3 sqrt(Float3 a){
		return new Float3(TornadoMath.sqrt(a.getX()),TornadoMath.sqrt(a.getY()),TornadoMath.sqrt(a.getZ()));
	}
	
	public static Float3 floor(Float3 a){
		return new Float3(TornadoMath.floor(a.getX()),TornadoMath.floor(a.getY()),TornadoMath.floor(a.getZ()));
	}
	
	public static Float3 fract(Float3 a){
		return new Float3(TornadoMath.fract(a.getX()),TornadoMath.fract(a.getY()),TornadoMath.fract(a.getZ()));
	}
	
	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Float3 x, float min, float max){
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
		x.setZ(TornadoMath.clamp(x.getZ(), min, max));
	}
	
//	public static void normalise(Float3 value){
//		final float len = length(value);
//		scaleByInverse(value, len);
//	}
	
	public static Float3 normalise(Float3 value){
		final float len = 1f / length(value);
		return mult(value, len);
	}
	
	/*
	 * vector wide operations
	 */
	

	public static float min(Float3 value){
		return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
	}
	
	public static float max(Float3 value){
		return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
	}
	
	public static float dot(Float3 a, Float3 b){
		final Float3 m = mult(a,b);
		return m.getX() + m.getY() + m.getZ();
	}
		
	/**
	 * Returns the vector length
	 * e.g. the sqrt of all elements squared
	 * @return
	 */
	public static float length(Float3 value){
		return TornadoMath.sqrt(dot(value, value));
	}
	
	public static boolean isEqual(Float3 a, Float3 b){
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}
	
	public static boolean isEqualULP(Float3 a, Float3 b, float numULP){
		return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
	}
	
	public static float findULPDistance(Float3 a, Float3 b){
		return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
	}
}
