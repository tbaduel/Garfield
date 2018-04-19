package fr.upem.net.parser.apache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EscapeCharacter {


	public static final CharSequenceTranslator ESCAPE_JAVA;
	static {
		final Map<CharSequence, CharSequence> escapeJavaMap = new HashMap<>();
		escapeJavaMap.put("\"", "\\\"");
		escapeJavaMap.put("\\", "\\\\");
		ESCAPE_JAVA = new AggregateTranslator(new LookupTranslator(Collections.unmodifiableMap(escapeJavaMap)),
				new LookupTranslator(EntityArrays.JAVA_CTRL_CHARS_ESCAPE), JavaUnicodeEscaper.outsideOf(32, 0x7f));
	}

	public static final String escapeJava(final String input) {
		return ESCAPE_JAVA.translate(input);
	}
	
	
}
