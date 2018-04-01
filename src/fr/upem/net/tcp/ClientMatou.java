package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.tcp.Reader.ProcessStatus;

public class ClientMatou {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger log = Logger.getLogger(ClientMatou.class.getName());
	public final SocketChannel sc;
	public String username;
	public static final Object monitor = new Object();
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private SelectionKey uniqueKey;
	final private ByteBuffer bbin = ByteBuffer.allocateDirect(BUFFER_SIZE);
	final private ByteBuffer bbout = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private boolean closed = false;
	final private MessageReader messageReader = new MessageReader(bbin);
	private final HubClient hubClient = new HubClient();
	final private Queue<ByteBuffer> queue = new LinkedList<>();
	private Scanner scan;

	public ClientMatou(SocketChannel sc, Scanner scan) throws IOException {
		this.sc = sc;
		this.scan = scan;
		sc.connect(new InetSocketAddress("localhost", 8083));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		while (sc.read(bb) != -1) {
			if (!bb.hasRemaining()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * @return id (opcode)
	 * @throws IOException
	 */
	public int receiveServer() throws IOException {
		ByteBuffer receive = ByteBuffer.allocate(Integer.BYTES);
		if (readFully(sc, receive)) {
			receive.flip();
			int id = receive.getInt();
			if (id > 99) {
				// if id > 99 then its ack
				return id;
			}
		}
		return -1;
	}

	public void requestServer(int id, String dataBody) throws IOException {
		ByteBuffer req = HubClient.formatBuffer(sc, dataBody, id);
		sendServer(req);
	}

	/**
	 * Write the buffer to the server
	 * 
	 * @param send
	 * @throws IOException
	 */
	public void sendServer(ByteBuffer send) throws IOException {
		sc.write(send);
	}

	public boolean login(String login, String password, boolean newUser) throws IOException {
		String data = "username: " + login + "\r\npassword: " + password + "\r\n";
		ByteBuffer send = null;
		if (newUser == true) {
			send = HubClient.formatBuffer(sc, data, Opcode.SIGNUP.op);
		} else {
			send = HubClient.formatBuffer(sc, data, Opcode.LOGIN.op);
		}
		sendServer(send);
		int status = receiveServer();
		return status == Opcode.SIGNUP_OK.op || status == Opcode.LOGIN_OK.op;

	}

	public void receiveBroadcast() {
		HubClient hub = new HubClient();
		ByteBuffer receive = ByteBuffer.allocate(Integer.BYTES);
		try {
			while (!Thread.interrupted()) {
				receive.clear();
				if (readFully(sc, receive)) {
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
						if (headerSize <= 0)
							continue;
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
									BodyParser bp = ServerReader.readBody(bodyString);
									hub.executeClient(Opcode.valueOfId(id), bp);
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

	public void beginChat() {
		try {
			while (!Thread.interrupted()) {
				String line = scan.nextLine();
				ParserLine parser = ParserLine.parse(line);
				ByteBuffer req = HubClient.formatBuffer(sc, parser.line, parser.opcode.op);
				queue.add(req);
				processOut();
				updateInterestOps();
				selector.wakeup();
				/*
				 * if (line.equals("/exit")) { notEnded = false; } else { ParserLine parser =
				 * ParserLine.parse(line); executeAction(parser.opcode, parser.line); }
				 */

			}
		} catch (IOException e) {
			log.severe("IOException !!");
		}

	}

	public void executeAction(Opcode op, String line) throws IOException {
		requestServer(op.op, line);
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			//System.out.println(key.isValid() ? "VALID KEY !" : "NOT VALID KEY !");
			if (key.isValid() && key.isConnectable()) {
				doConnect();
			}
			if (key.isValid() && key.isWritable()) {
				doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				doRead();
			}
			
		}
	}

	private void updateInterestOps() {
		// TODO
		int newInterestOps = 0;
		if (!closed && bbin.hasRemaining()) {
			System.out.println("READ MODE");
			newInterestOps |= SelectionKey.OP_READ;
		}
		if (bbout.position() != 0) {
			System.out.println("WRITE MODE");
			newInterestOps |= SelectionKey.OP_WRITE;
		}
		if (newInterestOps == 0) {
			silentlyClose();
		} else
			uniqueKey.interestOps(newInterestOps);

	}


	/**
	 * Try to fill bbout from the message queue
	 *
	 */
	private void processOut() {
		// TODO
		while (bbout.remaining() >= Integer.BYTES && queue.size() > 0) {
			ByteBuffer a = queue.poll();
			// a.flip();
			bbout.put(a);
		}
	}

	private void processIn() throws IOException {
		bbin.flip();
		ProcessStatus ps = messageReader.process();
		if (ps == ProcessStatus.DONE) {
			Message msg = messageReader.get();
			hubClient.executeClient(Opcode.valueOfId(msg.getOp()), msg.getBp());
			messageReader.reset();
			bbin.compact();
		} else {
			System.out.println("not done");
		}
	}
	
	private void doRead() throws IOException {
		if (sc.read(bbin) == -1) {
			log.info("Closing connection.");
			closed = true;
		}
		processIn();
		updateInterestOps();
	}

	private void doWrite() throws IOException {
		// dowrite ne s'executera pas tant que l'utilisateur n'aura pas ecrit sur
		// l'entrÃ©e standard
		// (en gros tant que bbout ne sera pas rempli par l'autre thread.)
		bbout.flip();
		bbout.position(0);
		sc.write(bbout);
		bbout.compact();
		updateInterestOps();

	}

	private void doConnect() throws IOException {
		if (!sc.finishConnect()) {
			return;
		}
		System.out.println("Change mode");
		updateInterestOps();
		System.out.println("Changed");
	}
	
	

	public void launch() throws IOException, InterruptedException {
		sc.configureBlocking(false);
	    uniqueKey=sc.register(selector, SelectionKey.OP_CONNECT);
	    Set<SelectionKey> selectedKeys = selector.selectedKeys();
		Thread reader = new Thread(this::beginChat);
		reader.start();
		//updateInterestOps(); sinon on reçoit rien au début
		doConnect();
		while (!Thread.interrupted()) {
			selector.select();
			processSelectedKeys();
			selectedKeys.clear();
		}
		reader.join();
	}

	private void silentlyClose() {
		try {
			System.out.println("Closing...");
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String args[]) throws InterruptedException {

		try (Scanner sc = new Scanner(System.in); SocketChannel sock = SocketChannel.open();) {
			ClientMatou cm = new ClientMatou(sock, sc);
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
				if (!auth) {
					System.out.println("Invalid credentials or already registered login.");
				}
			}
			System.out.println("Welcome, " + login + " !");
			cm.launch();
			// cm.beginChat(sc);

		} catch (IOException e) {
			log.severe("IOException, terminating client: " + e.getMessage());
		}
	}
}
