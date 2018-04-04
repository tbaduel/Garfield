package fr.upem.net.parser;

import fr.upem.net.client.ClientMatou;
import fr.upem.net.other.Opcode;

public class ParserLine {

	public final Opcode opcode;
	public final String line;
	
	private ParserLine(Opcode opcode, String line) {
		this.opcode = opcode;
		this.line = line;
	}

	public static ParserLine parse(String rawline, ClientMatou client) {
		String trimline = rawline.trim();
		if (trimline.length() < 3) {
			return new ParserLine(Opcode.MESSAGE, "data: " + rawline + "\r\n");
		}
		String line = trimline.substring(0, 2);
		Opcode opcode = Opcode.MESSAGE;
		String[] words;
		
		//change with hashmap like hubclient
		//System.out.println("line:["+ line + "]");
		if (line.equals("/r")) {
			opcode = Opcode.REQUEST;
			return new ParserLine(opcode, "userReq: " + trimline.substring(2).trim() +"\r\n");
		}
		else if (line.equals("/f")) {
			opcode = Opcode.FILE;
			words = rawline.split("\\s+");
			if (words.length < 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + words[0] + "\r\n" + "file: " + words[1] + "\r\n");
		}
		else if (line.equals("/y")) {
			opcode = Opcode.WHISP_OK;
			words = rawline.split("\\s+");
			if (words.length < 2) {
				return new ParserLine(Opcode.ERROR, "");
			}
			return new ParserLine(opcode, "username: " + words[0] + "\r\n" + "ip: " + client.getAddress() + "\r\n" + "port: " + client.getPort() + "token: " + client.getToken());
		}
		else
 			return new ParserLine(opcode, "data: " + rawline + "\r\n");
	}
}
