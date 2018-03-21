package fr.upem.net.tcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class BodyParser {

    private final Map<String, String> fields;
    private final int size;
    
    private BodyParser(int size, Map<String, String> fields) throws GFPException {
        this.fields = Collections.unmodifiableMap(fields);
        this.size = size;
    }
    
    public static BodyParser create(int size, Map<String,String> fields) throws GFPException {
        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s,fields.get(s).trim());
        return new BodyParser(size, fieldsCopied);
    }
    
    public String getField(String field) {
    	return fields.get(field);
    }
    
    public int getSize() {
    	return size;
    }
}