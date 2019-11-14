package klfr.sof;

import java.io.Serializable;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.*;
import static klfr.sof.Interpreter.cleanCode;

/**
 * The tokenizer class wraps the regular expression matching functionality to
 * provide an interpreter or other SOF source code analysis tool with easy
 * methods to examine SOF source code, change interpretation locations
 * on-the-fly and modify the source code during execution.<br>
 * <br>
 * Of course, this class makes heavy use of regular expressions for string
 * analysis.
 * 
 * @author klfr
 */
public class Tokenizer implements Iterator<String> {

	/**
	 * Represents internal state of a tokenizer for transfering such states between
	 * objects and storing state as a file (hence the Serializable
	 * implementation).<br>
	 * <br>
	 * <strong>All of this classes properties are public to allow the SOF language
	 * internal utilities to modify them. It is strongly disregarded to make any
	 * modifications to a stored state object, as any modification might break the
	 * tokenizer; i.e. a tokenizer created from such a state will experience
	 * undocumented and indeterminate behavior.</strong>
	 * 
	 * @author klfr
	 */
	public static class TokenizerState implements Serializable {
		private static final long serialVersionUID = 1L;

		public int start;
		public int end;
		public int regionStart;
		public int regionEnd;
		public String code;

		public TokenizerState(int start, int end, int regionStart, int regionEnd, String code) {
			super();
			this.start = start;
			this.end = end;
			this.regionStart = regionStart;
			this.regionEnd = regionEnd;
			this.code = code;
		}

		@Override
		public TokenizerState clone() {
			return new TokenizerState(this.start, this.end, this.regionStart, this.regionEnd, this.code);
		}

		public boolean equals(Object other) {
			return other instanceof TokenizerState ? equals((TokenizerState) other) : false;
		}

		public boolean equals(TokenizerState other) {
			return other.start == this.start && other.end == this.end && other.regionStart == this.regionStart
					&& other.regionEnd == this.regionEnd && other.code.equals(this.code);
		}

		@Override
		public String toString() {
			return String.format("TokenizerState(%d->%d, r%d->%d [%s])", this.start, this.end, this.regionStart, this.regionEnd,
				this.code.substring(0, Math.min(10, this.code.length())));
		}
	}

	private String code;
	private Matcher m;
	/**
	 * stores where the last token match ended; all scanning methods continue from
	 * here.
	 */
	private int lastMatchEnd = 0;

	/**
	 * Returns the matcher that is used to match tokens and limit the token search
	 * space. The user is free to modify the matcher however they want, though any
	 * modification of the matcher might break this class's proper behavior.
	 * 
	 * @return The internal matcher, uncloned.
	 */
	public Matcher getMatcher() {
		return m;
	}

	/** nested code block information */
	private Deque<TokenizerState> stateStack;

	/**
	 * Constructor for factory methods, do not use externally.
	 */
	private Tokenizer(String code, Matcher m) {
		this.m = m;
		this.code = code;
		this.stateStack = new LinkedBlockingDeque<>();
	}

	public String getCode() {
		return this.code;
	}

	/**
	 * Creates a new tokenizer from a source code string. The tokenizer's state is
	 * set up to start scanning the code from the beginning.
	 * 
	 * @param code the SOF source code to be used with this Tokenizer
	 * @return a new tokenizer
	 * @throws CompilerException if the code contains malformed strings, comments
	 *                          etc.
	 */
	public static Tokenizer fromSourceCode(String code) throws CompilerException {
		String clean = cleanCode(code);
		Matcher matcher = Interpreter.tokenPattern.matcher(clean);
		Tokenizer t = new Tokenizer(clean, matcher);
		matcher.reset();
		return t;
	}

	/**
	 * Creates a tokenizer from the given saved tokenizer state. This method does
	 * not guarantee to return a functioning tokenizer as the tokenizer state might
	 * be corrupted.
	 * 
	 * @param state the state to create a tokenizer from
	 * @return a new tokenizer without a state stack
	 */
	public static Tokenizer fromState(TokenizerState state) {
		Matcher matcher = Interpreter.tokenPattern.matcher(state.code);
		matcher.region(state.regionStart, state.regionEnd);
		Tokenizer t = new Tokenizer(state.code, matcher);
		t.lastMatchEnd = state.end;
		return t;
	}

	/**
	 * Create a state object that represents the current full internal state of the
	 * Tokenizer excluding its saved state stack. The new state object is fully
	 * independed from this tokenizer.<br>
	 * <br>
	 * This method is to be used together with {@code Tokenizer.fromState()} to
	 * recreate the tokenizer later on or clone it.
	 * 
	 * @return a new, independed TokenizerState
	 */
	@SuppressWarnings("finally")
	public TokenizerState getState() {
		int start = 0, end = 0;
		try {
			start = this.m.start();
			end = this.m.end();
		} catch (IllegalStateException e) {
		} finally {
			return new TokenizerState(start, end, this.m.regionStart(), this.m.regionEnd(), code);
		}
	}

	/**
	 * Sets the state's parameters on this tokenizer.
	 */
	private void setState(TokenizerState state) {
		this.code = state.code;
		this.m = Interpreter.tokenPattern.matcher(code);
		this.m.region(state.regionStart, state.regionEnd);
		this.lastMatchEnd = state.end;
	}

	/**
	 * Returns a new tokenizer that is the same as this one, except that the given
	 * code is appended to the new tokenizer. This tokenizer is not modified.
	 * 
	 * @param code the code to be appended, without leading newlines. A newline is
	 *             inserted between the current code and the new code.
	 * @return a new, independed Tokenizer with this tokenizer's state and the given
	 *         code appended.
	 */
	public Tokenizer withCodeAppended(String code) throws CompilerException {
		Tokenizer nt = this.clone();
		nt.appendCode(code);
		return nt;
	}

	/**
	 * Appends the given code to this tokenizer, thereby modifying this tokenizer.
	 * 
	 * @param code the code to be appended, without leading newlines. A newline is
	 *             inserted between the current code and the new code.
	 * @return this tokenizer
	 */
	public Tokenizer appendCode(String code) throws CompilerException {
		TokenizerState state = this.getState();
		System.out.println(state);
		this.code += this.code.endsWith(System.lineSeparator()) ? cleanCode(code)
				: (System.lineSeparator() + cleanCode(code));
		this.m = Interpreter.tokenPattern.matcher(this.code);
		this.m.region(state.regionStart, state.regionEnd);
		this.lastMatchEnd = state.end;
		return this;
	}
	
	/**
	 * Returns the current line the tokenizer points to.
	 * @return the current line the tokenizer points to.
	 */
	public int getCurrentLine() {
		Matcher linefinder = Interpreter.nlPat.matcher(getCode());
		int realIndex = m.start();
		int lastLineStart = 0, linenum = 0;
		while (linefinder.find() && realIndex > lastLineStart) {
			// System.out.println("Advancing to index " + linefinder.start() + " line " + (linenum+1));
			lastLineStart = linefinder.start();
			++linenum;
		}
		return linenum;
	}
	
	/**
	 * Returns the index of the current position inside (respective to) its line in the code.
	 * @return the index of the current position inside (respective to) its line in the code.
	 */
	public int getIndexInsideLine() {
		Matcher linefinder = Interpreter.nlPat.matcher(getCode());
		int realIndex = m.start();
		int lastLineStart = 0;
		while (linefinder.find() && realIndex > lastLineStart) {
			lastLineStart = linefinder.start();
		}
		//last line now contains the index where the line starts that begins before the matcher's index
		//i.e. the line of the matcher
		return realIndex - lastLineStart;
	}

	@Override
	public Tokenizer clone() {
		Tokenizer nt = Tokenizer.fromState(this.getState());
		Iterator<TokenizerState> it = this.stateStack.stream().map(x -> x.clone()).iterator();
		while (it.hasNext()) {
			nt.stateStack.add(it.next());
		}
		return nt;
	}

	/**
	 * Returns whether the tokenizer can provide more tokens.
	 * 
	 * @return whether the tokenizer can provide more tokens.
	 */
	public boolean hasNext() {
		return !this.m.hitEnd();
	}

	/**
	 * Finds and returns the next token, or an empty string if there is no next
	 * token.
	 * 
	 * @return the next token, or an empty string if there is no next token.
	 */
	public String next() {
		boolean success = this.m.find(lastMatchEnd);
		if (!success)
			return "";
		this.lastMatchEnd = this.m.end();
		return this.m.group();
	}

	/**
	 * Pushes a state onto the internal state stack.
	 */
	public void pushState() {
		this.stateStack.push(this.getState());
	}

	/**
	 * Pops and restores a state from the internal stack. This will re-initialize
	 * the matcher, be careful.
	 */
	public void popState() throws NoSuchElementException {
		this.setState(this.stateStack.pop());
	}
}
