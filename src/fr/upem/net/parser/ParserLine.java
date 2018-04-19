package fr.upem.net.parser;

import fr.upem.net.client.ClientMatou;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.apache.EscapeCharacter;

public class ParserLine {

	public final Opcode opcode;
	public final String line;
	public String userWhispered = null;

	private ParserLine(Opcode opcode, String line) {
		this.opcode = opcode;
		this.line = line;
	}

	private ParserLine(Opcode opcode, String line, String username) {
		this(opcode, line);
		this.userWhispered = username;
	}

	public static ParserLine parse(String rawline, ClientMatou client) {
		rawline = EscapeCharacter.escapeJava(rawline); // METHODE APACHE
		String trimline = rawline.trim();
		if (trimline.length() < 3) {
			return new ParserLine(Opcode.MESSAGE, "data: " + rawline + "\r\n");
		}
		String line = trimline.substring(0, 2);
		Opcode opcode = Opcode.MESSAGE;
		String[] words;

		// change with hashmap like hubclient
		// System.out.println("line:["+ line + "]");
		if (line.equals("/r")) {
			opcode = Opcode.REQUEST;
			return new ParserLine(opcode, "userReq: " + trimline.substring(2).trim() + "\r\n");
		} else if (line.equals("/w")) {
			opcode = Opcode.WHISP;
			words = rawline.split("\\s+");
			if (words.length < 3) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode,
					"data: " + trimline.substring(words[1].length() + 3).trim() + "\r\n" + "username: " + words[1],
					words[1]);
		} else if (line.equals("/f")) {
			opcode = Opcode.FILE;
			words = rawline.split("\\s+");
			if (words.length < 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + words[0] + "\r\n" + "file: " + words[1] + "\r\n");
		} else if (line.equals("/y")) {
			opcode = Opcode.WHISP_OK;
			words = rawline.split("\\s+");
			if (words.length < 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode,
					"username: " + words[1] + "\r\n" + "userReq: " + client.username + "\r\n" + "ip: "
							+ client.getAddress() + "\r\n" + "port: " + client.getPort() + "\r\n" + "token: "
							+ client.getToken() + "\r\n");
		} else
			return new ParserLine(opcode, "data: " + rawline + "\r\n");
	}
}
