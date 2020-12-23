package klfr.sof.exceptions;

/**
 * Supertype of SOF-internal exceptions. Useful for catch block hierarchy. 
 * 
 * @author klfr
 */
public abstract class SOFException extends Exception {
	private static final long serialVersionUID = 1L;

	public SOFException(String message) {
		super(message);
	}

	public SOFException(String message, Throwable cause) {
		super(message, cause);
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
