package fr.upem.net.parser;

import java.io.IOException;
//import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    
    /**
     * Add a key/value
     * @param field the key
     * @param value the value
     */
    public void addField(String field, String value) {
    	fields.put(field.toLowerCase(), value);
    }
    
    /**
     * Get the field corresponding to the string key
     * @param field the key
     * @return the String value of the key
     */
    public String getField(String field) {
    	return fields.get(field.toLowerCase());
    }
    
    /**
     * Get the size
     * @return the size
     */
    public int getSize() {
    	return size;
    }
    
    /*
     * data: jean: "je suis là"
     * 
     */
    
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
			Pattern pattern = Pattern.compile(": *");
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
			    String key = line.substring(0, matcher.start());
			    String value = line.substring(matcher.end());
			    if (value != null && key.length() > 0 && value.length() > 0) {
					map.merge(key.toLowerCase(), value, (x, y) -> String.join("; ", x, y));
				} else {
					map.merge(key.toLowerCase(), "", (x, y) -> String.join("; ", x, y));
				}
			}
		}
		
		return BodyParser.create(size, map);
	}
	
	/*
	public static List<String> splitter(String data) {
		List<String> ret = new ArrayList<>();
		
		return null;
	}
	*/
}
