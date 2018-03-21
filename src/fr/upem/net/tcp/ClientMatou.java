package fr.upem.net.tcp;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientMatou {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger logger = Logger.getLogger(ClientMatou.class.getName());
	
	public static boolean requestServer(int id, String data) {
		/*
		 * username: tikko\r\n
		 * password: dede\r\n
		 */
		ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
		ByteBuffer dataToAdd = UTF8.encode(data);
		//ServerHeader header = ServerReader.readHeader(data);
		dataToAdd.flip();
		buff.clear();
		//Add ID of packet
		buff.putInt(id);
		//Add size of the header
		buff.putInt(0);
		//Add length of the body
		buff.putInt(dataToAdd.remaining());
		//username
		//password
		buff.put(dataToAdd);
		return true;
	}
	
	public static boolean login(String login, String password, boolean newUser) {
		String data = "username: " + login + "\r\npassword: " + password + "\r\n";
		if (newUser == true && requestServer(0, data)) {
			// inscription
			return true;
		}
		// login
		return requestServer(1, data);
	}
	
	public static void beginChat() {
		
	}
	
	
	public static void main(String args[]) {
		try (Scanner sc = new Scanner(System.in);) {
			boolean auth = false;
			boolean newUser = false;
			String login = null;
			String password = null;
			while (!auth) {
				System.out.println("Are you a new user? (O/N)");
				newUser = sc.nextLine().equals("O");
				System.out.println("Please authenticate\nLogin:");
				login = sc.nextLine();
				System.out.println("Password:");
				password = sc.nextLine();
				auth = login(login, password, newUser);
			}
			beginChat();

		}
	}
}
