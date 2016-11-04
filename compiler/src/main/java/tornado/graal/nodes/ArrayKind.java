package tornado.graal.nodes;

import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.PlatformKind;

@Deprecated
public enum ArrayKind implements PlatformKind {

	// @formatter:off
	FLOAT(Kind.Float),
	DOUBLE(Kind.Double),
	BYTE(Kind.Byte),
	SHORT(Kind.Short),
	INT(Kind.Int),
	LONG(Kind.Long),
	

	/** The non-type. */
	Illegal(Kind.Illegal);

	public static final ArrayKind[]	typeTable	= new ArrayKind[] {FLOAT, DOUBLE, LONG, INT, SHORT, BYTE};

	// @formatter:on

	public static final ArrayKind fromKind(Kind kind) {	
		int index = lookupTypeIndex(kind);
		return (index >= 0) ? typeTable[index] : Illegal;
	}

	public final static int lookupTypeIndex(Kind kind) {
		
		switch (kind) {
			case Byte:
				return 5;
			case Double:
				return 1;
			case Float:
				return 0;
			case Int:
				return 3;
			case Long:
				return 2;
			case Short:
				return 4;
			case Char:
			case Boolean:
			case Illegal:
			case Void:
			case Object:
			default:
				
				break;
		}
		return -1;
	}

	public final int lookupTypeIndex() {
		return lookupTypeIndex(getElementKind());
	}

	private final Kind		elementKind;
	private final EnumKey	key	= new EnumKey(this);

	private ArrayKind(
			Kind elementKind) {
		this.elementKind = elementKind;
	}

	/**
	 * Returns the name of the kind as a single character.
	 */
	public char getTypeChar() {
		return 'a';
	}

	/**
	 * Returns the name of this kind which will also be it Java programming language name if it is
	 * {@linkplain #isPrimitive() primitive} or {@code void}.
	 */
	public String getJavaName() {
		return elementKind.getJavaName() + "[]";
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
		return Object[].class;
	}

	/**
	 * Converts this value type to a string.
	 */
	@Override
	public String toString() {
		return getJavaName();
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
		return elementKind.getByteCount();
	}

	/**
	 * Number of bits that are necessary to represent a value of this kind.
	 *
	 * @return the number of bits
	 */
	public int getBitCount() {
		return elementKind.getBitCount();
	}

	public JavaConstant getDefaultValue() {
		return elementKind.getDefaultValue();
	}

	public Kind getElementKind() {
		return elementKind;
	}
	
}
