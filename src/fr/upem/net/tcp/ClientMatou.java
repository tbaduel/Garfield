package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientMatou {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger log = Logger.getLogger(ClientMatou.class.getName());
	public final SocketChannel sc;
	public String username;
	public static final Object monitor = new Object();

	public ClientMatou(SocketChannel sc) throws IOException {
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
		// status badly formed, need to restructure code (opcode added in bodyparser as
		// status)
		ByteBuffer receive = ByteBuffer.allocate(Integer.BYTES);
		if (readFully(sc, receive)) {
			receive.flip();
			int id = receive.getInt();
			if (id > 99) {
				// if id > 99 then its ack
				return BodyParser.createAck(id);
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
		// make header before adding id of packet
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
		sc.write(req);
		// add other opcodes for ACK etc
		if (id == Opcode.LOGIN.op || id == Opcode.SIGNUP.op) {
			BodyParser bp = receiveServer();
			boolean ret = false;
			Opcode status = Opcode.valueOfId(Integer.parseInt(bp.getField("status")));
			switch (status) {
			case LOGIN_OK:
				ret = true;
				System.out.println("You're now online.");
				break;
			case LOGIN_ERR:
				ret = false;
				System.out.println("Incorrect credentials.");
				break;
			case SIGNUP_OK:
				ret = true;
				System.out.println("Registration complete.");
				break;
			default:
				break;
			}
			return ret;
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

	public void receiveBroadcast() {
		// int + int + (header) + (body)
		// readfully ~ one by one
		// status badly formed, need to restructure code (opcode added in bodyparser as
		// status)
		ByteBuffer receive = ByteBuffer.allocate(Integer.BYTES);
		try {
			while (!Thread.interrupted()) {
				receive.clear();
				boolean test = readFully(sc, receive);
				if (test) {
					receive.flip();
					int id = receive.getInt();
					if (id > 99) {
						// if id > 99 then its ack
						continue;
					}
					ByteBuffer headerSizeBuff = ByteBuffer.allocate(Integer.BYTES);
					if (readFully(sc, headerSizeBuff)) {
						headerSizeBuff.flip();
						int headerSize = headerSizeBuff.getInt();
						// check if > 0 ?

						ByteBuffer header = ByteBuffer.allocate(headerSize);
						if (readFully(sc, header)) {
							header.flip();
							byte endFlag = header.get();
							int bodySize = header.getInt();
							ByteBuffer body = ByteBuffer.allocate(bodySize);
							if (endFlag == (byte) 1) {
								if (readFully(sc, body)) {
									body.flip();
									String bodyString = UTF8.decode(body).toString();
									// meant to use body json efficiently
									BodyParser bp = ServerReader.readBody(bodyString);
									if (id == Opcode.MESSAGEBROADCAST.op) {
										Calendar date = Calendar.getInstance();
										System.out
												.println("[" + date.get(Calendar.HOUR) + ":" + date.get(Calendar.MINUTE)
														+ "]" + bp.getField("username") + ": " + bp.getField("data"));
									}

								}
							} // else receigve chunks?
						}
					}
				}
			}
		} catch (AsynchronousCloseException e) {
			// normal behaviour.
			log.log(Level.INFO, "Sender stopped");
		} catch (IOException e) {
			log.log(Level.SEVERE, "Sender thread killed by IOException", e);
		}
	}
	/*
	 * [18:27] tikko to localhost: salut les amis [18:27] tikko to localhost: /r
	 * thomas [18:27] localhost to thomas: DEMANDE DE TIKKO DE MESSAGE PRIVE (O/N)
	 * [18:27] thomas to localhost: O /w thomas salut mon frere [18:27] tikko to
	 * thomas: salut mon frere /f [nom] file /r [nom] whisper
	 */

	public void beginChat(Scanner sc) throws IOException, InterruptedException {
		boolean notEnded = true;
		Thread reader = new Thread(this::receiveBroadcast);
		reader.start();
		while (notEnded) {
			String line = sc.nextLine();

			if (line.equals("/exit")) {
				notEnded = false;
			} else {
				ParserLine parser = ParserLine.parse(line);
				executeAction(parser.opcode, parser.line);
			}

		}
		reader.join();
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

	public static void main(String args[]) throws InterruptedException {

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
				System.out.print("Please authenticate\nLogin: ");
				login = sc.nextLine();
				System.out.print("Password: ");
				password = sc.nextLine();
				auth = cm.login(login, password, newUser);
			}
			cm.beginChat(sc);

		} catch (IOException e) {
			log.severe("IOException, terminating client: " + e.getMessage());
		}
	}
}
