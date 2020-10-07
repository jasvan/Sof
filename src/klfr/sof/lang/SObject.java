package klfr.sof.lang;

public class SObject extends Nametable {

	private static final long serialVersionUID = 1L;
	private Identifier name;

	public String getName() {
		return name.getValue();
	}

	public SObject(Identifier name) {
		this.name = name;
	}

	@Override
	public String toDebugString(DebugStringExtensiveness e) {
		return switch (e) {
			case Full -> "Object " + getName() + ":" + System.lineSeparator() + super.toDebugString(e);
			case Compact -> "Obj(" + super.toDebugString(e) + ")";
			default -> super.toDebugString(e);
		};
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
