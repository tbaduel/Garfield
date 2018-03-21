package fr.upem.net.tcp;

public class ParserLine {

	public final Opcode opcode;
	public final String line;
	
	private ParserLine(Opcode opcode, String line) {
		this.opcode = opcode;
		this.line = line;
	}

	public static ParserLine parse(String rawline) {
		String line = rawline.trim().substring(0, 2);
		Opcode opcode = Opcode.MESSAGE;
		switch (line) {
		case "/r ":
			opcode = Opcode.REQUEST;
			break;
		case "/f ":
			opcode = Opcode.FILE;
			break;
		}
		if (opcode != Opcode.MESSAGE) {
			line = rawline.substring(2);
		}
		return new ParserLine(opcode, line);
	}
}
