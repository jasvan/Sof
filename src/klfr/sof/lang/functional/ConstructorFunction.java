package klfr.sof.lang.functional;

import klfr.sof.ast.TokenListNode;
import klfr.sof.lang.*;

/**
 * A special function that is a constructor, i.e. a function responsible for creating ("constructing") a new object. The
 * internal structure is not particularly special, it simply acts as a marker to the interpreter system that some
 * special logic needs to act before and after the object creation.
 * 
 * @author klfr
 */
@StackableName("Constructor")
public final class ConstructorFunction extends Function {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new constructor function with the given code and arguments.
	 * 
	 * @param code            The AST list of nodes that is used as this constructor's behavior.
	 * @param arguments       The number of arguments to this constructor.
	 * @param globalNametable global nametable of the function's global scope.
	 */
	public ConstructorFunction(TokenListNode code, long arguments, Nametable globalNametable) {
		super(code, arguments, globalNametable);
	}

}
