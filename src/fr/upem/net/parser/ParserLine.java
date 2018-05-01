package fr.upem.net.parser;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
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
	
	/**
	 * Takes a formatted ip as a parameter and returns the final ip used to connect.
	 * @param ip
	 * @return the formatted ip
	 */
	public static String formatIpBack(String ip) {
		// pas besoin de faire quoi que ce soit en java les ipv4 et ipv6 sont directement support�es.
		// dans un autre langage il aurait fallu effectuer un traitement.
		ip = ip.replace("6-", "");
		ip = ip.replace("4-", "");
		return ip;
	}
	
	/**
	 * This method is used to go further than just localhost, that way you're able to get the real ip address
	 * of your client and send it to a server. It means that the client and the server would work even if you're
	 * not trying it locally.
	 * It also adds whether the IP is an IPv6 or IPv4 so that the client is able to use the IP.
	 * @param ip
	 * @return formatted ip.
	 */
	private static String formatIp(InetAddress ip) {
		String host = ip.getHostAddress();
		String ret = "";
		try {
			Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
			while(e.hasMoreElements())
			{
			    NetworkInterface n = (NetworkInterface) e.nextElement();
			    Enumeration<InetAddress> ee = n.getInetAddresses();
			    while (ee.hasMoreElements())
			    {
			        InetAddress i = ee.nextElement();
			        if (!i.isAnyLocalAddress() && !i.isLinkLocalAddress() && !i.isLoopbackAddress() && !i.isMulticastAddress()) {
			        	if(i instanceof Inet6Address){
			                ret = "6-";
			            } else if (i instanceof Inet4Address) {
			                ret = "4-";
			            }
			        	ret += i.getHostAddress();
			        	return ret;
			        }
			    }
			}
		} catch (SocketException e) {
			System.err.println("Socket Exception!");
		}
		return host;
	}
	
	/**
	 * Takes the line that the client wrote and parse it to determine what action he wanted to do.
	 * @param rawline
	 * @param client
	 * @return a ParserLine object containing additional infos, opcodes and everything needed.
	 */
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
			if (!checkFileExist(words[2])) {
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
			System.out.println(words[2]);
			Optional<Integer> fileId = client.getIdFromFileName(words[2]);
			if (fileId.isPresent()) {
				return new ParserLine(opcode, "username: " + client.username + "\r\n" + "file: " + words[2] + "\r\n" + "fileId: " + fileId.get() + "\r\n", words[1]);
			}
			else {
				System.out.println("File not accepted");
				return new ParserLine(Opcode.ERROR, "");
			}
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
								+ formatIp(InetAddress.getLocalHost()) + "\r\n" + "port: " + client.getPort() + "\r\n" + "token: "
								+ token.get() + "\r\n");
			} catch (UnknownHostException e) {
				return new ParserLine(Opcode.ERROR, "");
			}
		} else
			return new ParserLine(opcode, "data: " + rawline + "\r\n");
	}
	
	
	private static boolean checkFileExist(String stringPath) {
		int maxFileSize = 50_000_000;
		try(FileChannel fc = FileChannel.open(Paths.get(stringPath), StandardOpenOption.READ)){
			long size = fc.size();
			if (size > maxFileSize) {
				System.out.println("Max file size = " + maxFileSize);
				return false;
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
