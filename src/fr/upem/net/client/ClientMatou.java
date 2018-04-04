package fr.upem.net.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import fr.upem.net.other.Opcode;
import fr.upem.net.parser.ParserLine;
import fr.upem.net.reader.MessageReader;
import fr.upem.net.reader.Reader.ProcessStatus;
import fr.upem.net.server.Message;

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
	final private MessageReader messageReader = new MessageReader(bbin);
	private final HubClient hubClient = new HubClient();
	final private Queue<ByteBuffer> queue = new LinkedList<>();
	private Scanner scan;
	private List<String> connectedUsers = new ArrayList<>();
	private final int port;
	private final String address;
	private final int token;
	private boolean closed = false;
	
	public ClientMatou(SocketChannel sc, Scanner scan, int port, String address) throws IOException {
		Random rand = new Random();
		this.sc = sc;
		this.scan = scan;
		this.port = port;
		this.address = address;
		sc.connect(new InetSocketAddress(address, port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		token = rand.nextInt(10000000);
	}
	
	public int getToken() {
		return token;
	}

	public String getAddress() {
		return address;
	}
	
	public int getPort() {
		return port;
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

	
	/*
	 * [18:27] tikko to localhost: salut les amis
	 * [18:27] tikko to localhost: /r thomas
	 * [18:27] localhost to thomas: DEMANDE DE TIKKO DE MESSAGE PRIVE (O/N)
	 * [18:27] thomas to localhost: O /w thomas salut mon frere
	 * [18:27] tikko to thomas: salut mon frere /f [nom] file /r [nom] whisper
	 */

	public void beginChat() {
		try {
			while (!Thread.interrupted()) {
				String line = scan.nextLine();
				if (closed) {
					return ;
				}
				ParserLine parser = ParserLine.parse(line, this);
				ByteBuffer req = HubClient.formatBuffer(sc, parser.line, parser.opcode.op);
				queue.add(req);
				processOut();
				if (!updateInterestOps()) {
					return;
				}
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


	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
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

	private boolean updateInterestOps() {
		int newInterestOps = 0;
		if (bbin.hasRemaining()) {
			newInterestOps |= SelectionKey.OP_READ;
		}
		if (bbout.position() != 0) {
			newInterestOps |= SelectionKey.OP_WRITE;
		}
		if (newInterestOps == 0) {
			silentlyClose();
		} else {
			if (uniqueKey.isValid()) {
				uniqueKey.interestOps(newInterestOps);
			}
			else {
				System.out.println("Closing...");
				return false;
			}
		}
		return true;
	}


	/**
	 * Try to fill bbout from the message queue
	 *
	 */
	private void processOut() {
		while (bbout.remaining() >= Integer.BYTES && queue.size() > 0) {
			ByteBuffer a = queue.poll();
			bbout.put(a);
		}
	}

	private void processIn() throws IOException {
		bbin.flip();
		ProcessStatus ps = messageReader.process();
		if (ps == ProcessStatus.DONE) {
			Message msg = messageReader.get();
			hubClient.executeClient(Opcode.valueOfId(msg.getOp()), msg.getBp(), this);
			messageReader.reset();
			bbin.compact();
		} else {
			System.out.println("not done");
		}
	}
	
	private void doRead() throws IOException {
		System.out.println("this doread closes: " + closed);
		if (sc.read(bbin) == -1) {
			log.info("Closing connection.");
			silentlyClose();
			return ;
		}
		processIn();
		updateInterestOps();
	}

	public void addConnectedUser(String user) {
		connectedUsers.add(user);
	}
	
	public boolean removeConnectedUser(String user) {
		int i = connectedUsers.indexOf(user);
		if (i == -1) {
			return false;
		}
		connectedUsers.remove(i);
		return true;
	}
	
	private void doWrite() throws IOException {
		// dowrite ne s'executera pas tant que l'utilisateur n'aura pas ecrit sur
		// l'entrée standard
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
		updateInterestOps();
	}
	
	

	public void launch() throws IOException, InterruptedException {
		sc.configureBlocking(false);
	    uniqueKey=sc.register(selector, SelectionKey.OP_CONNECT);
	    Set<SelectionKey> selectedKeys = selector.selectedKeys();
		Thread reader = new Thread(this::beginChat);
		reader.start();
		doConnect();
		while (!Thread.interrupted()) {
			if (closed == true) {
				reader.join();
				return ;
			}
			System.out.println("Selecting keys");
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
			closed = true;
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void usage() {
		System.out.println("usage: java fr.upem.net.tcp.ClientMatou address port");
	}
	
	public static void main(String args[]) throws InterruptedException {
		
		try (Scanner sc = new Scanner(System.in); SocketChannel sock = SocketChannel.open();) {
			if (args.length != 2) {
				usage();
				return ;
			}
			int port = Integer.parseInt(args[1]);
			ClientMatou cm = new ClientMatou(sock, sc, port, args[0]);
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

		} catch (IOException e) {
			log.severe("IOException, terminating client: " + e.getMessage());
		}
	}
}
