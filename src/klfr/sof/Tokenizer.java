package klfr.sof;

import java.io.Serializable;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;
import java.util.regex.*;

import klfr.Tuple;

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

	private Logger log = Logger.getLogger(this.getClass().getCanonicalName());

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
			return String.format("TokenizerState(%d->%d, r%d->%d [%s])", this.start, this.end, this.regionStart,
					this.regionEnd, this.code.replace("\n", "\\n").replace("\r", "\\r").replace("\b", "\\b")
							.replace("\t", "\\t").replace("\f", "\\f"));
		}
	}

	private Matcher m;
	/**
	 * stores the current state of the tokenizer
	 */
	private TokenizerState currentState;
	/** Stores the last token that was found by the match methods */
	private String lastMatchedToken;

	/**
	 * Returns the index of the last matched token.
	 */
	public int start() {
		return this.currentState.start;
	}

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
	 * Constructor for factory methods, do not use externally. Code needs to be
	 * cleaned beforehand
	 */
	private Tokenizer(String code) {
		this.m = Patterns.tokenPattern.matcher(code);
		this.stateStack = new LinkedBlockingDeque<>();
		this.currentState = new TokenizerState(0, 0, 0, code.length(), code);
	}

	public String getCode() {
		return this.currentState.code;
	}

	/**
	 * Creates a new tokenizer from a source code string. The tokenizer's state is
	 * set up to start scanning the code from the beginning.
	 * 
	 * @param code the SOF source code to be used with this Tokenizer
	 * @return a new tokenizer
	 */
	public static Tokenizer fromSourceCode(String code) {
		return new Tokenizer(code);
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
		Tokenizer t = new Tokenizer(state.code);
		t.currentState = state;
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
	public TokenizerState getState() {
		return this.currentState.clone();
	}

	/**
	 * Sets the state's parameters on this tokenizer.
	 * 
	 * @deprecated This method will easily break the tokenizer's proper behavior.
	 */
	@Deprecated
	public void setState(TokenizerState state) {
		this.currentState = state;
		this.m = Patterns.tokenPattern.matcher(this.currentState.code);
		this.m.region(state.regionStart, state.regionEnd);
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
	 * The old region settings are discarded.
	 * 
	 * @param code the code to be appended, without leading newlines. A newline is
	 *             inserted between the current code and the new code.
	 * @return this tokenizer
	 */
	public Tokenizer appendCode(String code) {
		TokenizerState state = this.currentState;
		this.log.finer("State before appending: " + state);
		var needsNewline = !this.currentState.code.isEmpty() && !this.currentState.code.endsWith("\n");
		this.currentState.code += (needsNewline ? "\n" : "") + code;
		this.m = Patterns.tokenPattern.matcher(this.currentState.code);
		this.currentState.end = state.end;
		this.currentState.regionStart = 0;
		this.currentState.regionEnd = this.currentState.code.length();
		this.log.finer("State after appending: " + this.getState());
		return this;
	}

	// /**
	// * Returns the current line the tokenizer points to. This is in human-readable
	// * form, i.e. line number 1 is actually the first line of code (and not the
	// * second).
	// *
	// * @return the current line the tokenizer points to.
	// */
	// public int getCurrentLine() {
	// Matcher linefinder = Interpreter.nlPat.matcher(getCode());
	// int realIndex = this.start(), linenum = 0;
	// // increment line number while the text index is still after the searched
	// line
	// // beginning
	// while (linefinder.find() &&
	// realIndex > linefinder.start() - 1)
	// ++linenum;
	// // return line number - 1 because we advanced past the line itself
	// return linenum - 1;
	// }

	// /**
	// * Returns the index of the current position inside (respective to) its line
	// in
	// * the code.
	// *
	// * @return the index of the current position inside (respective to) its line
	// in
	// * the code.
	// */
	// public int getIndexInsideLine() {
	// Matcher linefinder = Interpreter.nlPat.matcher(getCode());
	// int realIndex = this.start(), linestart = 0;
	// while (linefinder.find() && realIndex > linefinder.start()) {
	// linestart = linefinder.start() + 1;
	// }
	// // last line now contains the index where the line starts that begins before
	// the
	// // matcher's index
	// // i.e. the line of the matcher
	// return realIndex - linestart;
	// }

	/**
	 * Returns the current execution position of the Tokenizer, as a <Line, Index>
	 * number tuple. While the line number is one-based (as in text editors), the
	 * index (inside the line) is zero-based (as in strings).
	 * 
	 * @return A tuple with two integers that represent the line position and index
	 *         inside the line; see above notes.
	 */
	public Tuple<Integer, Integer> getCurrentPosition() {
		Matcher linefinder = Patterns.nlPat.matcher(getCode());
		int realIndex = this.start(), linenum = 0, lineStart = 0;
		// increment line number while the text index is still after the searched line
		// beginning
		while (linefinder.find() && realIndex > linefinder.start() - 1) {
			++linenum;
			lineStart = linefinder.start() + 1;
		}
		// linenum -1 because we advanced past the actual line
		log.fine(String.format("tuple current index %d computed to line %d starting at %d line-inside-index %d",
				realIndex, linenum, lineStart, realIndex - (lineStart - 1)));
		return new Tuple<>(linenum, realIndex - (lineStart - 1));
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
	 * Returns whether the tokenizer has exceeded its searching region.
	 * 
	 * @return whether the tokenizer has exceeded its searching region.
	 */
	public boolean regionExceeded() {
		return this.currentState.regionEnd < Math.max(this.currentState.start, this.currentState.end)
				|| this.currentState.regionStart > Math.min(this.currentState.start, this.currentState.end);
	}

	/**
	 * Returns whether the tokenizer can provide more tokens.
	 * 
	 * @return whether the tokenizer can provide more tokens.
	 */
	public boolean hasNext() {
		return findNextToken(false);
	}

	/**
	 * Performs region- and state-safe find on the matcher from the given index.
	 * 
	 * @param advance Whether to actually store the new findings. hasNext(), for
	 *                example, will set this to false to not change the state on
	 *                repeated invocations.
	 */
	private boolean findNextToken(boolean advance) {
		if (this.regionExceeded())
			return false;
		// whether there are more tokens to be found: perform one additional match
		var hasMore = this.m.find(this.currentState.end);
		final int prevEnd = this.currentState.end, prevStart = this.currentState.start;
		if (hasMore) {
			// in this case, use the matcher's finding bounds
			this.currentState.end = this.m.end();
			this.currentState.start = this.m.start();
			// check if any of the new finds are outside of the region
			if (this.regionExceeded()) {
				if (!advance) {
					this.currentState.end = prevEnd;
					this.currentState.start = prevStart;
				}
				return false;
			}
			// store the matched token for the other methods to use
			if (advance)
				this.lastMatchedToken = this.m.group();
		}
		// otherwise, we hit the end, position the last match at the end of the code
		else
			this.currentState.end = this.currentState.code.length();

		if (!advance) {
			this.currentState.end = prevEnd;
			this.currentState.start = prevStart;
		}
		return hasMore;
	}

	/**
	 * Finds and returns the next token, or an empty string if there is no next
	 * token.
	 * 
	 * @return the next token, or an empty string if there is no next token.
	 */
	@Override
	public String next() {
		if (!this.findNextToken(true))
			throw new NoSuchElementException("No more tokens");
		return this.lastMatchedToken;
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

/*  
The SOF programming language interpreter.
Copyright (C) 2019-2020  kleinesfilmröllchen

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
