package fr.upem.net.parser.apache;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public abstract class CharSequenceTranslator {
	
	static final char[] HEX_DIGITS = new char[] {'0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'A', 'B',
            'C', 'D', 'E', 'F'};
	public abstract int translate(CharSequence input, int index, Writer out) throws IOException;

	public final String translate(final CharSequence input) {
		if (input == null) {
			return null;
		}
		try {
			final StringWriter writer = new StringWriter(input.length() * 2);
			translate(input, writer);
			return writer.toString();
		} catch (final IOException ioe) {
			// this should never ever happen while writing to a StringWriter
			throw new RuntimeException(ioe);
		}
	}
	
	public final void translate(final CharSequence input, final Writer out) throws IOException {
        if (input == null) {
            return;
        }
        int pos = 0;
        final int len = input.length();
        while (pos < len) {
            final int consumed = translate(input, pos, out);
            if (consumed == 0) {
                // inlined implementation of Character.toChars(Character.codePointAt(input, pos))
                // avoids allocating temp char arrays and duplicate checks
                final char c1 = input.charAt(pos);
                out.write(c1);
                pos++;
                if (Character.isHighSurrogate(c1) && pos < len) {
                    final char c2 = input.charAt(pos);
                    if (Character.isLowSurrogate(c2)) {
                      out.write(c2);
                      pos++;
                    }
                }
                continue;
            }
            // contract with translators is that they have to understand codepoints
            // and they just took care of a surrogate pair
            for (int pt = 0; pt < consumed; pt++) {
                pos += Character.charCount(Character.codePointAt(input, pos));
            }
        }
    }
	
}
