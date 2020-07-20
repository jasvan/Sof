package klfr.sof.lang;

import klfr.sof.CompilerException;

@StackableName("String")
public class StringPrimitive extends Primitive {
   private static final long serialVersionUID = 1L;

   private final String s;
   public final long length;

   private StringPrimitive(String s) {
      this.s = s;
      length = s.length();
   }

   @Override
   public Object v() {
      return s;
   }

   public String value() {
      return s;
   }

   public static StringPrimitive createStringPrimitive(String s) {
      return new StringPrimitive(s);
   }

   @Override
   public String toDebugString(DebugStringExtensiveness e) {
      switch (e) {
         case Full:
            return String.format("s\"%s\"(%2d)", this.s, this.length);
         case Compact:
            return '"' + s + '"';
         default:
            return super.toDebugString(e);
      }
   }

   @Override
   public String print() {
      return s;
   }

   @Override
   public int compareTo(Stackable o) {
      if (o instanceof StringPrimitive) {
         return this.s.compareTo(((StringPrimitive) o).s);
      }
      throw new CompilerException.Incomplete("type", "type.compare", this.typename(), o.typename());
   }

   @Override
   public boolean equals(Stackable other) {
      if (other instanceof StringPrimitive)
         return this.s.equals(((StringPrimitive) other).s);
      return super.equals(other);
   }

}
