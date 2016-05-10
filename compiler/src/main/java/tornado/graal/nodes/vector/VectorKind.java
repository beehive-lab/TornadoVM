package tornado.graal.nodes.vector;

import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.ResolvedJavaType;

public enum VectorKind implements PlatformKind {

	// @formatter:off
	FLOAT2('f', "float4", tornado.collections.types.Float2.TYPE, Kind.Float, 2), FLOAT3('f',
			"float3", tornado.collections.types.Float3.TYPE, Kind.Float, 3), FLOAT4('f', "float4",
			tornado.collections.types.Float4.TYPE, Kind.Float, 4), FLOAT8('f', "float8",
			tornado.collections.types.Float8.TYPE, Kind.Float, 8),
	// FLOAT16('f',"float16",tornado.collections.types.Float16.TYPE,Kind.Float,16),

	INT2('i', "int2", tornado.collections.types.Int2.TYPE, Kind.Int, 2), INT3('i', "int3",
			tornado.collections.types.Int3.TYPE, Kind.Int, 3),
	// INT4('i',"int4",tornado.collections.types.Int4.TYPE,Kind.Int,4),
	// INT8('i',"int8",tornado.collections.types.Int8.TYPE,Kind.Int,8),
	// INT16('i',"int16",tornado.collections.types.Int16.TYPE,Kind.Int,16),

	SHORT2('s', "short2", tornado.collections.types.Short2.TYPE, Kind.Short, 2),
	// SHORT3('s',"short3",tornado.collections.types.Short3.TYPE,Kind.Short,3),
	// SHORT4('s',"short4",tornado.collections.types.Short4.TYPE,Kind.Short,4),
	// SHORT8('s',"short8,tornado.collections.types.Short8.TYPE,Kind.Short,8),
	// SHORT16('s',"short16",tornado.collections.types.Short16.TYPE,Kind.Short,16),

	// BYTE2('b', "byte2", tornado.collections.types.Byte2.TYPE, Kind.Short, 2),
	BYTE3('b', "char3", tornado.collections.types.Byte3.TYPE, Kind.Byte, 3), BYTE4('b', "char4",
			tornado.collections.types.Byte4.TYPE, Kind.Byte, 4),
	// BYTE8('s',"short8,tornado.collections.types.Short8.TYPE,Kind.Short,8),
	// BYTE16('s',"short16",tornado.collections.types.Short16.TYPE,Kind.Short,16),

	/** The non-type. */
	Illegal('-', "illegal", null, Kind.Illegal, 0);

	public static final VectorKind[][]	typeTable	= new VectorKind[][] {
			{ SHORT2, Illegal, Illegal, Illegal, Illegal },
			{ INT2, INT3, Illegal, Illegal, Illegal }, { FLOAT2, FLOAT3, FLOAT4, FLOAT8, Illegal },
			{ Illegal, BYTE3, BYTE4, Illegal, Illegal } };

	// @formatter:on

	public static final VectorKind fromElementKind(Kind kind, int length) {
		int colIndex = lookupLengthIndex(length);
		int rowIndex = lookupTypeIndex(kind);

		return (colIndex >= 0 && rowIndex >= 0) ? typeTable[rowIndex][colIndex] : Illegal;
	}

	public static final VectorKind fromClass(Class<?> type) {
		for (VectorKind vectorKind : VectorKind.values()) {
			if (vectorKind.javaClass == type) return vectorKind;
		}
		return Illegal;
	}

	public static final VectorKind fromResolvedJavaType(ResolvedJavaType type) {
		if (!type.isArray()) {
			for (VectorKind vectorKind : VectorKind.values()) {
				if (vectorKind.javaClass != null) {
//					System.out.printf("vectorkind: %s != %s\n",
//							vectorKind.javaClass.getSimpleName(), type.getUnqualifiedName());
					if (vectorKind.javaClass.getSimpleName().equals(type.getUnqualifiedName())) return vectorKind;
				}
			}
		}
		return Illegal;
	}

	public final static int lookupTypeIndex(Kind kind) {
		switch (kind) {
			case Short:
				return 0;
			case Int:
				return 1;
			case Float:
				return 2;
			case Byte:
				return 3;
			default:
				return -1;
		}
	}

	public Class<?> getJavaClass() {
		return javaClass;
	}

	public final int lookupLengthIndex() {
		return lookupLengthIndex(getVectorLength());
	}

	public final int lookupTypeIndex() {
		return lookupTypeIndex(getElementKind());
	}

	public final static int lookupLengthIndex(int length) {
		switch (length) {
			case 2:
				return 0;
			case 3:
				return 1;
			case 4:
				return 2;
			case 8:
				return 3;
			case 16:
				return 4;
			default:
				return -1;
		}
	}

	private final char		typeChar;
	private final String	javaName;
	private final Class<?>	javaClass;
	private final Kind		elementKind;
	private final EnumKey	key	= new EnumKey(this);
	private final int		vectorLength;

	private VectorKind(
			char typeChar,
			String javaName,
			Class<?> javaClass,
			Kind elementKind,
			int vectorLength) {
		this.typeChar = typeChar;
		this.javaName = javaName;
		this.javaClass = javaClass;
		this.elementKind = elementKind;
		this.vectorLength = vectorLength;
	}

	/**
	 * Returns the name of the kind as a single character.
	 */
	public char getTypeChar() {
		return typeChar;
	}

	/**
	 * Returns the name of this kind which will also be it Java programming language name if it is
	 * {@linkplain #isPrimitive() primitive} or {@code void}.
	 */
	public String getJavaName() {
		return javaName;
	}

	public Key getKey() {
		return key;
	}

	/**
	 * Checks whether this type is a Java primitive type.
	 *
	 * @return {@code true} if this is {@link #Boolean}, {@link #Byte}, {@link #Char},
	 *         {@link #Short}, {@link #Int}, {@link #Long}, {@link #Float}, {@link #Double}, or
	 *         {@link #Void}.
	 */
	public boolean isPrimitive() {
		return false;
	}

	/**
	 * Checks whether this type is a Java primitive type representing an integer number.
	 *
	 * @return {@code true} if the stack kind is {@link #Int} or {@link #Long}.
	 */
	public boolean isNumericInteger() {
		return elementKind.isNumericInteger();
	}

	/**
	 * Checks whether this type is a Java primitive type representing an unsigned number.
	 *
	 * @return {@code true} if the kind is {@link #Boolean} or {@link #Char}.
	 */
	public boolean isUnsigned() {
		return elementKind.isUnsigned();
	}

	/**
	 * Checks whether this type is a Java primitive type representing a floating point number.
	 *
	 * @return {@code true} if this is {@link #Float} or {@link #Double}.
	 */
	public boolean isNumericFloat() {
		return elementKind.isNumericFloat();
	}

	/**
	 * Checks whether this represent an Object of some sort.
	 *
	 * @return {@code true} if this is {@link #Object}.
	 */
	public boolean isObject() {
		return false;
	}

	/**
	 * Returns the Java class representing this kind.
	 *
	 * @return the Java class
	 */
	public Class<?> toJavaClass() {
		return javaClass;
	}

	/**
	 * Converts this value type to a string.
	 */
	@Override
	public String toString() {
		return javaName;
	}

	/**
	 * Marker interface for types that should be {@linkplain Kind#format(Object) formatted} with
	 * their {@link Object#toString()} value. Calling {@link Object#toString()} on other objects
	 * poses a security risk because it can potentially call user code.
	 */
	public interface FormatWithToString {
	}

	/**
	 * Number of bytes that are necessary to represent a value of this kind.
	 *
	 * @return the number of bytes
	 */
	public int getByteCount() {
		return vectorLength * elementKind.getByteCount();
	}

	/**
	 * Number of bits that are necessary to represent a value of this kind.
	 *
	 * @return the number of bits
	 */
	public int getBitCount() {
		return vectorLength * elementKind.getBitCount();
	}

	public JavaConstant getDefaultValue() {
		return elementKind.getDefaultValue();
	}

	public int getVectorLength() {
		return vectorLength;
	}

	public Kind getElementKind() {
		return elementKind;
	}
}
