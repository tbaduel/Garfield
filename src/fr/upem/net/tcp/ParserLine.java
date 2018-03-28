package fr.upem.net.tcp;

public class ParserLine {

	public final Opcode opcode;
	public final String line;
	
	private ParserLine(Opcode opcode, String line) {
		this.opcode = opcode;
		this.line = line;
	}

	public static ParserLine parse(String rawline) {
		String trimline = rawline.trim();
		if (trimline.length() < 3) {
			return new ParserLine(Opcode.MESSAGE, "data: " + rawline);
		}
		String line = trimline.substring(0, 2);
		Opcode opcode = Opcode.MESSAGE;
		switch (line) {
		case "/r ":
			opcode = Opcode.REQUEST;
			return new ParserLine(opcode, "username: " + trimline.substring(2) +"\r\n");
		case "/f ":
			opcode = Opcode.FILE;
			String[] words = rawline.split("\\s+");
			if (words.length < 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + words[0] + "\r\n" + "file: " + words[1] + "\r\n");
		default:
 			return new ParserLine(opcode, "data: " + rawline);
		}
	}
}
