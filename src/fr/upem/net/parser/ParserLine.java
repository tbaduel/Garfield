package fr.upem.net.parser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import fr.upem.net.client.ClientMatou;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.apache.EscapeCharacter;

public class ParserLine {

	public final Opcode opcode;
	public final String line;
	public String additionalInfo = null;
	public String additionalInfo2 = null;
	public String file = null;
	
	private ParserLine(Opcode opcode, String line) {
		this.opcode = opcode;
		this.line = line;
	}

	private ParserLine(Opcode opcode, String line, String additionalInfo) {
		this(opcode, line);
		this.additionalInfo = additionalInfo;
	}
	
	private ParserLine(Opcode opcode, String line, String additionalInfo, String additionalInfo2) {
		this(opcode, line, additionalInfo);
		this.additionalInfo2 = additionalInfo2;
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
					"data: " + trimline.substring(words[1].length() + 3).trim() + "\r\n" + "username: " + client.username,
					words[1]);
		} else if (line.equals("/f")) {
			opcode = Opcode.FILE_REQUEST;
			words = rawline.split("\\s+");
			if (words.length != 3) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + client.username + "\r\n" + "file: " + words[2] + "\r\n", words[1]);
		}
		else if (line.equals("/o")) {
			opcode = Opcode.FILE_OK;
			words = rawline.split("\\s+");
			if (words.length != 3) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + client.username + "\r\n" + "file: " + words[2], words[1]);
		} else if (line.equals("/y")) {
			opcode = Opcode.WHISP_OK;
			words = rawline.split("\\s+");
			if (words.length != 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			Optional<Integer> token = client.getPendingConnectionToken(words[1]);
			if (!token.isPresent())
				return new ParserLine(Opcode.ERROR, "");
			try {
				return new ParserLine(opcode,
						"username: " + words[1] + "\r\n" + "userReq: " + client.username + "\r\n" + "ip: "
								+ InetAddress.getLocalHost().getHostAddress() + "\r\n" + "port: " + client.getPort() + "\r\n" + "token: "
								+ token.get() + "\r\n");
			} catch (UnknownHostException e) {
				return new ParserLine(Opcode.ERROR, "");
			}
		} else
			return new ParserLine(opcode, "data: " + rawline + "\r\n");
	}
}
