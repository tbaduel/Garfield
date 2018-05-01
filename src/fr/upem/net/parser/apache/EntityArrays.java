package fr.upem.net.parser.apache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EntityArrays {

	   /**
	     * A Map&lt;CharSequence, CharSequence&gt; to to escape
	     * <a href="https://secure.wikimedia.org/wikipedia/en/wiki/ISO/IEC_8859-1">ISO-8859-1</a>
	     * characters to their named HTML 3.x equivalents.
	     */
	    public static final Map<CharSequence, CharSequence> ISO8859_1_ESCAPE;
	    static {
	        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
	        ISO8859_1_ESCAPE = Collections.unmodifiableMap(initialMap);
	    }

	    /**
	     * Reverse of {@link #ISO8859_1_ESCAPE} for unescaping purposes.
	     */
	    public static final Map<CharSequence, CharSequence> ISO8859_1_UNESCAPE;
	    static {
	        ISO8859_1_UNESCAPE = Collections.unmodifiableMap(invert(ISO8859_1_ESCAPE));
	    }

	    /**
	     * A Map&lt;CharSequence, CharSequence&gt; to escape additional
	     * <a href="http://www.w3.org/TR/REC-html40/sgml/entities.html">character entity
	     * references</a>. Note that this must be used with {@link #ISO8859_1_ESCAPE} to get the full list of
	     * HTML 4.0 character entities.
	     */
	    public static final Map<CharSequence, CharSequence> HTML40_EXTENDED_ESCAPE;
	    static {
	        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
	        HTML40_EXTENDED_ESCAPE = Collections.unmodifiableMap(initialMap);
	    }

	    /**
	     * Reverse of {@link #HTML40_EXTENDED_ESCAPE} for unescaping purposes.
	     */
	    public static final Map<CharSequence, CharSequence> HTML40_EXTENDED_UNESCAPE;
	    static {
	        HTML40_EXTENDED_UNESCAPE = Collections.unmodifiableMap(invert(HTML40_EXTENDED_ESCAPE));
	    }

	    /**
	     * A Map&lt;CharSequence, CharSequence&gt; to escape the basic XML and HTML
	     * character entities.
	     *
	     * Namely: {@code " & < >}
	     */
	    public static final Map<CharSequence, CharSequence> BASIC_ESCAPE;
	    static {
	        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
	        BASIC_ESCAPE = Collections.unmodifiableMap(initialMap);
	    }

	    /**
	     * Reverse of {@link #BASIC_ESCAPE} for unescaping purposes.
	     */
	    public static final Map<CharSequence, CharSequence> BASIC_UNESCAPE;
	    static {
	        BASIC_UNESCAPE = Collections.unmodifiableMap(invert(BASIC_ESCAPE));
	    }

	    /**
	     * A Map&lt;CharSequence, CharSequence&gt; to escape the apostrophe character to
	     * its XML character entity.
	     */
	    public static final Map<CharSequence, CharSequence> APOS_ESCAPE;
	    static {
	        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
	        APOS_ESCAPE = Collections.unmodifiableMap(initialMap);
	    }

	    /**
	     * Reverse of {@link #APOS_ESCAPE} for unescaping purposes.
	     */
	    public static final Map<CharSequence, CharSequence> APOS_UNESCAPE;
	    static {
	        APOS_UNESCAPE = Collections.unmodifiableMap(invert(APOS_ESCAPE));
	    }

	    /**
	     * A Map&lt;CharSequence, CharSequence&gt; to escape the Java
	     * control characters.
	     *
	     * Namely: {@code \b \n \t \f \r}
	     */
	    public static final Map<CharSequence, CharSequence> JAVA_CTRL_CHARS_ESCAPE;
	    static {
	        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
	        initialMap.put("\b", "\\b");
	        initialMap.put("\n", "\\n");
	        initialMap.put("\t", "\\t");
	        initialMap.put("\f", "\\f");
	        initialMap.put("\r", "\\r");
	        JAVA_CTRL_CHARS_ESCAPE = Collections.unmodifiableMap(initialMap);
	    }

	    /**
	     * Reverse of {@link #JAVA_CTRL_CHARS_ESCAPE} for unescaping purposes.
	     */
	    public static final Map<CharSequence, CharSequence> JAVA_CTRL_CHARS_UNESCAPE;
	    static {
	        JAVA_CTRL_CHARS_UNESCAPE = Collections.unmodifiableMap(invert(JAVA_CTRL_CHARS_ESCAPE));
	    }

	    /**
	     * Used to invert an escape Map into an unescape Map.
	     * @param map Map&lt;String, String&gt; to be inverted
	     * @return Map&lt;String, String&gt; inverted array
	     */
	    public static Map<CharSequence, CharSequence> invert(final Map<CharSequence, CharSequence> map) {
	        final Map<CharSequence, CharSequence> newMap = new HashMap<>();
	        for (final Map.Entry<CharSequence, CharSequence> pair : map.entrySet()) {
	            newMap.put(pair.getValue(), pair.getKey());
	        }
	        return newMap;
	    }

	}