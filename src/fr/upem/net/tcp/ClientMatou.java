package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientMatou {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger log = Logger.getLogger(ClientMatou.class.getName());
	public final SocketChannel sc;

	public ClientMatou(SocketChannel sc) {
		this.sc = sc;
	}

	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		while (sc.read(bb) != -1) {
			if (!bb.hasRemaining()) {
				return true;
			}
		}
		return false;
	}

	public BodyParser receiveServer() throws IOException {
		// int + int + (header) + (body)
		// readfully ~ one by one
		// status badly formed, need to restructure code (opcode added in bodyparser as status)
		ByteBuffer receive = ByteBuffer.allocate(Integer.BYTES * 2);
		if (readFully(sc, receive)) {
			int id = receive.getInt();
			if (id > 99) {
				//if id > 99 then its ack
				return BodyParser.createAck(id);
			}
			int headerSize = receive.getInt();
			// check if > 0 ?

			ByteBuffer header = ByteBuffer.allocate(headerSize);
			if (readFully(sc, header)) {
				byte endFlag = header.get();
				int bodySize = header.getInt();
				ByteBuffer body = ByteBuffer.allocate(bodySize);
				if (endFlag == (byte) 1) {
					if (readFully(sc, body)) {
						String bodyString = UTF8.decode(body).toString();
						// bodyparser useless?
						// its meant to use body json efficiently
						BodyParser bp = ServerReader.readBody(bodyString);
						bp.addField("status", String.valueOf(id));
						return bp;
					}
				} // else receive chunks?
			}
		}
		return null;
	}

	public boolean requestServer(int id, String dataBody) throws IOException {
		ByteBuffer req = ByteBuffer.allocate(BUFFER_SIZE);
		ByteBuffer body = UTF8.encode(dataBody);
		byte endMsg = (byte) 1;
		// check if body is too large, if it is, then put chunk mode
		if (!body.hasRemaining()) {
			// TODO
			// while endMsg == 0, send chunks until endMsg == 1
		}
		ByteBuffer header = ByteBuffer.allocate(64);
		req.clear();
		header.clear();

		header.put(endMsg);
		header.putInt(body.remaining());
		header.flip();
		// Add id of packet
		req.putInt(id);
		// Add size of header
		req.putInt(header.remaining());
		// Add header to req
		req.put(header);
		// Add body to req
		req.put(body);
		req.flip();
		System.out.println("Sending request: " + id);
		sc.write(req);
		if (id == Opcode.REQUEST.op) {
			// add other opcodes for ACK etc
			BodyParser bp = receiveServer();
			if (bp.getField("status") == Opcode.WHISP_OK.toString()) {
				System.out.println("WHISP MODE!");
			} else if (bp.getField("status") == Opcode.LOGIN_OK.toString()) {
				System.out.println("LOGIN SUCCESS!");
			}

		}
		return true;
	}

	public boolean login(String login, String password, boolean newUser) throws IOException {
		String data = "username: " + login + "\r\npassword: " + password + "\r\n";
		if (newUser == true && requestServer(Opcode.SIGNUP.op, data)) {
			// inscription
			return true;
		}
		// login
		return requestServer(Opcode.LOGIN.op, data);
	}

	/*
	 * [18:27] tikko to localhost: salut les amis [18:27] tikko to localhost: /r
	 * thomas [18:27] localhost to thomas: DEMANDE DE TIKKO DE MESSAGE PRIVE (O/N)
	 * [18:27] thomas to localhost: O /w thomas salut mon frere [18:27] tikko to
	 * thomas: salut mon frere /f [nom] file /r [nom] whisper
	 */

	public void beginChat(Scanner scan) throws IOException {
		while (true) {
			String line = scan.nextLine();
			ParserLine parser = ParserLine.parse(line);
			executeAction(parser.opcode, parser.line);

		}
	}

	public void executeAction(Opcode op, String line) throws IOException {
		switch (op) {
		case MESSAGE:
			requestServer(Opcode.MESSAGE.op, line);
			break;
		case REQUEST:
			requestServer(Opcode.REQUEST.op, line);
			break;
		default:
			break;
		}
	}

	public static void main(String args[]) {

		try (Scanner sc = new Scanner(System.in); SocketChannel sock = SocketChannel.open();) {
			sock.connect(new InetSocketAddress("localhost", 8083));
			ClientMatou cm = new ClientMatou(sock);
			boolean auth = false;
			boolean newUser = false;
			String login = null;
			String password = null;
			while (!auth) {
				System.out.println("Are you a new user? (O/N)");
				newUser = sc.nextLine().toLowerCase().equals("o");
				// if newUser is true, then we'll register, otherwise we just login
				System.out.println("Please authenticate\nLogin:");
				login = sc.nextLine();
				System.out.println("Password:");
				password = sc.nextLine();
				auth = cm.login(login, password, newUser);
			}
			cm.beginChat(sc);

		} catch (IOException e) {
			log.severe("IOException, terminating client: " + e.getMessage());
		}
	}
}
