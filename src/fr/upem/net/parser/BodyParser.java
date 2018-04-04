package fr.upem.net.parser;

import java.io.IOException;
//import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class BodyParser {

    private final Map<String, String> fields;
    private final int size;
    
    private BodyParser(int size, Map<String, String> fields) {
        //this.fields = Collections.unmodifiableMap(fields);
    	this.fields = fields;
        this.size = size;
    }
    
    public static BodyParser create(int size, Map<String,String> fields) {
        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s,fields.get(s).trim());
        return new BodyParser(size, fieldsCopied);
    }
    
    public static BodyParser createAck(int ack)  {
    	Map<String, String> fields = new HashMap<>();
    	fields.put("status", String.valueOf(ack));
    	return new BodyParser(0, fields);
    }
    
    public void addField(String field, String value) {
    	fields.put(field.toLowerCase(), value);
    }
    
    public String getField(String field) {
    	return fields.get(field.toLowerCase());
    }
    
    public int getSize() {
    	return size;
    }
    
    /**
	 * @return The BodyParser object corresponding to the body read as fields
	 * @throws IOException
	 *             if the connection is closed before a body could
	 *             be read if the body is ill-formed
	 */
	public static BodyParser readBody(String data) throws IOException {
		// TODO
		int size = data.length();
		Map<String, String> map = new HashMap<String, String>();
		String []lines = data.split("\r\n");
		for (String line : lines) {
			if (line.equals("") || line.isEmpty()) {
				continue;
			}
			String[] keyvalue = line.split(": ");
			
			if (keyvalue.length > 1 && keyvalue[0].length() > 0 && keyvalue[1].length() > 0) {
				map.merge(keyvalue[0].toLowerCase(), keyvalue[1], (x, y) -> String.join("; ", x, y));
			} else {
				map.merge(keyvalue[0].toLowerCase(), "", (x, y) -> String.join("; ", x, y));
			}
		}
		
		return BodyParser.create(size, map);
	}
}
