package klfr.sof.lang;

import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import klfr.sof.CompilerException;
import klfr.sof.Interpreter;

/**
 * Primitive values in SOF are represented through this class.<br>
 * Because there is no way of limiting the possible types to the ones that
 * should be allowed, one shall only use the following types as type arguments:
 * {@code Number, Boolean, Character, String}.
 * 
 * @author klfr
 * @param <T> Type that the primitive refers down to.
 */
public class Primitive<T> implements Callable {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(Primitive.class.getCanonicalName());

	// all chars that can make up integers base 16 and lower, in ascending value
	public static final Map<Character, Integer> numberChars = new Hashtable<>();
	static {
		numberChars.put('0', 0);
		numberChars.put('1', 1);
		numberChars.put('2', 2);
		numberChars.put('3', 3);
		numberChars.put('4', 4);
		numberChars.put('5', 5);
		numberChars.put('6', 6);
		numberChars.put('7', 7);
		numberChars.put('8', 8);
		numberChars.put('9', 9);
		numberChars.put('a', 10);
		numberChars.put('b', 11);
		numberChars.put('c', 12);
		numberChars.put('d', 13);
		numberChars.put('e', 14);
		numberChars.put('f', 15);
	}

	private T value;

	public Primitive(T value) {
		this.value = value;
	}

	public T getValue() {
		return value;
	}

	@Override
	public String getDebugDisplay() {
		if (value instanceof String)
			return '"' + value.toString() + '"';
		return value.toString();
	}

	@Override
	public CallProvider getCallProvider() {
		final Primitive<T> self = this;
		return (interpreter) -> self.clone();
	}

	@Override
	public Stackable clone() {
		return new Primitive<>(this.value);
	}

	public boolean equals(Object other) {
		return other instanceof Primitive ? ((Primitive) other).equals(this) : false;
	}

	public boolean equals(Primitive other) {
		return other.value.equals(this.value);
	}

	public String tostring() {
		return this.getDebugDisplay();
	}

	@Override
	public String toOutputString() {
		return value.toString();
	}

	/**
	 * Helper method to create an integer primitive from a string that is a valid
	 * integer literal according to SOF specs.
	 * 
	 * @param integerString The string that only contains the integer in text
	 *                      format, to be converted.
	 * @return a new Primitive with integer type and the parsed integer value.
	 */
	public static Primitive<Long> createInteger(String integerString) throws CompilerException {
		integerString = integerString.strip();
		int radix = 10;
		long sign = 1;
		// check zero
		if (integerString.matches("[\\+\\-]?0+")) {
			return new Primitive<Long>(0l);
		}
		// check sign
		if (integerString.charAt(0) == '+') {
			integerString = integerString.substring(1);
		} else if (integerString.charAt(0) == '-') {
			integerString = integerString.substring(1);
			sign = -1;
		}
		// check radix
		if (integerString.charAt(0) == '0') {
			char base = integerString.charAt(1);
			switch (base) {
			case 'b':
				radix = 2;
				break;
			case 'o':
				radix = 8;
				break;
			case 'd':
				radix = 10;
				break;
			case 'h':
			case 'x':
				radix = 16;
				break;
			default:
				throw CompilerException.fromIncompleteInfo("Syntax",
						String.format("Invalid Integer literal \"%s\".", integerString));
			}
			integerString = integerString.substring(2);
		}

		String reverseInt = new StringBuilder(integerString).reverse().toString();
		long value = 0;
		for (int place = 0; place < reverseInt.length(); ++place) {
			char magnitude = reverseInt.charAt(place);
			if (!numberChars.containsKey(magnitude) || numberChars.get(magnitude) >= radix) {
				throw CompilerException.fromIncompleteInfo("Syntax",
						String.format("Character \"%c\" not allowed in base %d integer literal.", magnitude, radix));
			}
			value += numberChars.get(magnitude) * (long) (Math.pow(radix, place));
		}
		return new Primitive<Long>(value * sign);
	}

	public static Primitive<Boolean> createBoolean(String booleanString) throws CompilerException {
		if (booleanString.toLowerCase().equals("true"))
			return new Primitive<>(true);
		if (booleanString.toLowerCase().equals("false"))
			return new Primitive<>(false);
		throw CompilerException.fromIncompleteInfo("Syntax",
				String.format("No boolean literal found in \"%s\"", booleanString));
	}

	public static Primitive<Double> createDouble(String doubleString) throws CompilerException {
		final var m = Interpreter.doublePattern.matcher(doubleString);
		if (m.matches()) {
			log.finest(() -> String.format("%s %s %s %s", m.group(1), m.group(2), m.group(3), m.group(4)));
			// split the parts of the double up
			final byte sign = (byte) ((m.group(1) == null ? "+" : m.group(1)).contentEquals("-") ? -1 : 1);
			final long integerPart = createInteger(m.group(2)).value;
			String decimalPartStr = m.group(3), exponentStr = m.group(4) == null ? "" : m.group(4);
			double decimalPart = 0d; long exponent = 0L;

			exponentStr = exponentStr.length() > 0 ? exponentStr.substring(1) : "";
			if (exponentStr.length() > 0) {
				exponent = createInteger(exponentStr).value;
			}

			for (var i = 0; i < decimalPartStr.length(); ++i) {
				decimalPart += numberChars.get(decimalPartStr.charAt(i)) * Math.pow(10, -(i+1));
			}
			log.finest(String.format("%d * ( %d + %f ) * 10 ^ %d", sign, integerPart, decimalPart, exponent));
			return new Primitive<Double>(sign * Math.pow(10, exponent) * (integerPart + decimalPart));
		} else {
			throw CompilerException.fromIncompleteInfo("Syntax",
					String.format("No float literal found in\"%s\".", doubleString));
		}
	}

}
