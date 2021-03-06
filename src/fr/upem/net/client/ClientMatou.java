package fr.upem.net.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import fr.upem.net.message.Message;
import fr.upem.net.other.FileInfo;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.ParserLine;
import fr.upem.net.reader.MessageReader;
import fr.upem.net.reader.Reader.ProcessStatus;
import fr.upem.net.server.HubServ;

public class ClientMatou {

	public static class ContextClient {

		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
		final private Queue<ByteBuffer> queue = new LinkedList<>();
		final private ClientMatou client;
		private boolean closed = false;
		private Opcode opcodeAction;
		public String username; // added for whisper

		/* Try to read smthng */
		/*
		 * private boolean opCodeReaded = false; private boolean headerSizeReaded =
		 * false; private boolean headerReaded = false; private boolean messageReaded =
		 * false;
		 * 
		 * private boolean endFlag = false;
		 * 
		 * private Opcode opCode; private int headerSize = 0; private int messageSize =
		 * 0; private ByteBuffer header; private ByteBuffer message;
		 */

		final private MessageReader messageReader = new MessageReader(bbin);

		public ContextClient(ClientMatou client, SelectionKey key) {
			// added for whisper
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.client = client;
		}

		public void setUserName(String username) {
			// added for whisper
			this.username = username;
		}
		
		public SelectionKey getSelectionKey() {
			return key;
		}

		/**
		 * Process the content of bbin
		 *
		 * The convention is that bbin is in write-mode before the call to process and
		 * after the call
		 * 
		 * @throws IOException
		 *
		 */
		private void processIn() throws IOException {
			bbin.flip();
			ProcessStatus ps = messageReader.process();
			while (ps == ProcessStatus.DONE ) {
				Message msg = messageReader.get();
				System.out.println("MESSAGE RECUP: " + msg.getOp());
				if (Opcode.valueOfId(msg.getOp()) != Opcode.CHECK_PRIVATE && !client.connectedClients.contains(key)) {
					silentlyClose();
					return ;
				}
				client.hubClient.executeClient(Opcode.valueOfId(msg.getOp()), msg, client, this);
				messageReader.reset();
				
				ps = ProcessStatus.REFILL;
				ps = messageReader.process();
				System.out.println("new ps = " + ps);
			}
			bbin.compact();
			/*else {
				 System.out.println("not done");
			}
			*/
		}
		
		
		
		/*
		private void processIn() throws IOException {
			bbin.flip();
			ProcessStatus ps = messageReader.process();
			if (ps == ProcessStatus.DONE) {
				Message msg = messageReader.get();
				System.out.println("MESSAGE RECUP: " + msg.getOp());
				if (Opcode.valueOfId(msg.getOp()) != Opcode.CHECK_PRIVATE && !client.connectedClients.contains(key)) {
					silentlyClose();
					return ;
				}
				client.hubClient.executeClient(Opcode.valueOfId(msg.getOp()), msg, client, this);
				messageReader.reset();
				bbin.compact();
				
			} else {
				 System.out.println("not done");
			}
		}
		*/

		/**
		 * Process the message
		 * 
		 * @param msg
		 * @return The buffer of the processed message
		 * @throws IOException
		 */
		public ByteBuffer messageProcessing(Message msg) throws IOException {
			// TODO
			// add server here
			HubServ hub = new HubServ();
			ByteBuffer tmp = hub.ServerExecute(msg, null, sc);
			opcodeAction = hub.getOpcodeAction();
			return tmp;

		}

		/**
		 * Add a message to the message queue, tries to fill bbOut and updateInterestOps
		 * The message is a ByteBuffer in write mode
		 *
		 * @param msg
		 */
		public void queueMessage(ByteBuffer msg) {
			queue.add(msg);
			processOut();
			updateInterestOps();
		}

		/**
		 * Try to fill bbout from the message queue
		 *
		 */
		private void processOut() {
			
			while (bbout.remaining() > 0 && queue.size() > 0) {
				// System.out.println("remaining bbout " + bbout.remaining());
				ByteBuffer a = queue.peek();
				// System.out.println("add : " + a);
				a.flip();
				System.out.println("To send = " + a.remaining());
				System.out.println("bbout = " + bbout.remaining());
				if (bbout.remaining() >= a.remaining()) {
					System.out.println("Ya la place");
					bbout.put(a);
					queue.poll();
					
				}
				else {
					System.out.println("Je remets");
					a.position(a.limit());
					return;
				}
			}
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and of both ByteBuffers.
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * updateInterestOps and after the call. Also it is assumed that process has
		 * been be called just before updateInterestOps.
		 */

		private void updateInterestOps() {
			int newInterestOps = 0;
			if (!closed && bbin.hasRemaining()) {
				newInterestOps |= SelectionKey.OP_READ;
			}
			if (bbout.position() != 0) {
				newInterestOps |= SelectionKey.OP_WRITE;
			}
			if (newInterestOps == 0) {
				silentlyClose();
			} else
				key.interestOps(newInterestOps);

		}

		/**
		 * Close the socketChannel to the current Context. Note that it remove the
		 * connection from the server map connection
		 */
		public void silentlyClose() {
			try {
				client.removeConnectedClient(key);
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doRead and after the call
		 *
		 * @throws IOException
		 */

		private void doRead() throws IOException {
			int read;
			if ((read = sc.read(bbin)) == -1) {
				client.log.info("closing");
				closed = true;
			}
			// System.out.println("--------------\n jai lu " + read + "bytes");
			processIn();
			processOut();
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doWrite and after the call
		 *
		 * @throws IOException
		 */

		private void doWrite() throws IOException {
			bbout.flip();
			// System.out.println("ID to send = " + bbout.getInt());
			// bbout.position(0);
			// System.out.println("Avant envoie : " + bbout);

			/* Two option */
			// System.out.println("WRITING " + sc.write(bbout));
			System.out.println("WRITING TO "  + sc.getRemoteAddress());
			sc.write(bbout);
			System.out.println("WROTE TO "  + sc.getRemoteAddress());
			bbout.compact();
			System.out.println("Il reste a envoyer " + bbout);
			processOut();
			updateInterestOps();
		}

	}

	/**
	 * Get the Context corresponding to the ip
	 * 
	 * @param ip
	 * @return The context
	 */
	public ContextClient getContextFromIP(SocketAddress ip) {
		for (SelectionKey key : selector.keys()) {
			SelectableChannel channel = key.channel();
			if (!(channel instanceof ServerSocketChannel)) {
				SocketChannel sc = (SocketChannel) channel;
				try {
					if (sc.getRemoteAddress().equals(ip)) {
						return ((ContextClient) key.attachment());
					}
				} catch (IOException e) {
					return null;
				}

			}
		}
		return null;
	}

	public ContextClient getContextFromUsername(String username) {
		for (SelectionKey key : selector.keys()) {
			SelectableChannel channel = key.channel();
			if (!(channel instanceof ServerSocketChannel)) {
				ContextClient ct = ((ContextClient) key.attachment());
				if (ct.username.equals(username)) {
					return ct;
				}
			}
		}
		return null;
	}

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger log = Logger.getLogger(ClientMatou.class.getName());
	public final SocketChannel sc;
	public final ServerSocketChannel ssc;
	public static final Object monitor = new Object();
	public final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private SelectionKey uniqueKey;
	final private ByteBuffer bbin = ByteBuffer.allocateDirect(BUFFER_SIZE);
	final private MessageReader messageReader = new MessageReader(bbin);
	private final HubClient hubClient = new HubClient();
	final private Queue<ByteBuffer> queue = new LinkedBlockingQueue<>();
	final private Queue<ByteBuffer> queueFile = new LinkedBlockingQueue<>();
	// final private Queue<ByteBuffer> serverQueue = new LinkedList<>();
	final private Set<SelectionKey> connectedClients = new HashSet<>();
	final private Set<SelectionKey> authorizedToSendFile = new HashSet<>();
	private Scanner scan;
	private final int port;
	private final String address;
	private final int token;
	private String userWhispered = null; // for whispers
	private boolean closed = false;
	private ContextClient serverContext;
	private final ConcurrentHashMap<String, ByteBuffer> mapWhisperMessage = new ConcurrentHashMap<>();
	public String username;
	public String usernameWhisper;
	private Random rand = new Random();
	private Map<String, Integer> pendingConnectionToken = new HashMap<>();
	private Map<String, Set<FileInfo>> pendingConnectionFile = new HashMap<>();
	private Map<String, SelectionKey> usernameContext = new HashMap<>();
	private Map<Integer, String> idFileMap = new HashMap<>();
	private Map<String, ContextClient> fileContextMap = new HashMap<>();
	
	public ClientMatou(SocketChannel sc, Scanner scan, int port, String address) throws IOException {
		
		ssc = ServerSocketChannel.open();
		ssc.bind(null);
		ssc.configureBlocking(false);
		this.sc = sc;
		this.scan = scan;
		this.port = ((InetSocketAddress) ssc.getLocalAddress()).getPort();
		this.address = address;
		sc.connect(new InetSocketAddress(address, port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("SERVER IS ON " + ssc.getLocalAddress());
		token = generateToken();
		// System.out.println("mon port est : " + this.port);
	}

	/**
	 * Generate a random token and returns it
	 * @return random token
	 */
	public int generateToken() {
		return rand.nextInt(10000000);
	}
	
	public void addAuthorizedToSendFile(SelectionKey key) {
		authorizedToSendFile.add(key);
	}
	
	
	public void removeConnectedClient(SelectionKey key) {
		connectedClients.remove(key);
	}
	
	public void addAwaitingFileUser(String user, String file, int fileId) {
		Set<FileInfo> files = pendingConnectionFile.get(user);
		if (files == null) {
			files = new HashSet<FileInfo>();
		}
		FileInfo fi = new FileInfo(file, fileId);
		files.add(fi);
		pendingConnectionFile.put(user, files);
		System.out.println("Add :" + fileId + " = " + file);
		idFileMap.put(fileId, file);
	}
	
	public void addUsernameContext(String user, SelectionKey key) {
		usernameContext.put(user, key);
	}
	
	public void addAuthorizedToSendFile(Optional<SelectionKey> key) {		
		if (!key.isPresent()) {
			return ;
		}
		authorizedToSendFile.add(key.get());
	}
	
	/**
	 * Get the String name of a file from id
	 * @param fileId
	 * @return
	 */
	public String getFileFromId(int fileId) {
		return idFileMap.get(fileId);
	}
	
	public Optional<Integer> getIdFromFileName(String filename) {
		Collection<Integer> icollection = idFileMap.keySet();
		for (Integer i : icollection) {
			if (idFileMap.get(i).equals(filename)) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}
	
	public void setUsername(String username) {
		this.username = username;
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
	 * First received from server
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

	/**
	 * First sending to the server
	 * 
	 * @param id
	 * @param dataBody
	 * @throws IOException
	 */
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
		send.flip();
		sendServer(send);
		int status = receiveServer();
		return status == Opcode.SIGNUP_OK.op || status == Opcode.LOGIN_OK.op;

	}

	public Optional<SelectionKey> getNameInKeys(String user) {
		SelectionKey key = usernameContext.get(user);
		return key != null ? Optional.of(key) : Optional.empty();
		/*System.out.println("There are " + connectedClients.size() + " connected clients");
		connectedClients.stream().map(x -> {
			if (((ContextClient) x.attachment()) != null) {
				if (((ContextClient) x.attachment()).username.equals(user)) {
					return Optional.of(x);
				}
			}
			return Optional.empty();
		});
		return Optional.empty();*/
	}

	public boolean isUserAuthorizedToSendFile(Optional<SelectionKey> key) {
		if (!key.isPresent()) {
			return false;
		}
		return authorizedToSendFile.contains(key.get());
	}

	public void sendFile(ByteBuffer req, String file) {
		System.out.println("Sending file !");
	}
	
	public void beginChat() {
		try {
			while (!Thread.interrupted() && scan.hasNextLine()) {
				String line = scan.nextLine();
				if (closed) {
					return;
				}
				ParserLine parser = ParserLine.parse(line, this);
				ByteBuffer req = HubClient.formatBuffer(sc, parser.line, parser.opcode.op);
				//if (parser.opcode == Opcode.FILE_OK) {
				//	addAuthorizedToSendFile(getNameInKeys(parser.additionalInfo));
				//}
				if (parser.opcode == Opcode.ERROR) {
					System.out.println("Erreur de saisie");
				} else if (parser.opcode == Opcode.WHISP || parser.opcode == Opcode.FILE_REQUEST || parser.opcode == Opcode.FILE_OK || parser.opcode == Opcode.FILE_SEND) {
					System.out.println("user whispered = " + parser.additionalInfo);
					if (getNameInKeys(parser.additionalInfo).isPresent()) {
						System.out.println(parser.additionalInfo + " is present !");
						userWhispered = parser.additionalInfo; // for whispers
						mapWhisperMessage.put(userWhispered, req);
					}
				}
				else {
					queue.add(req);
				}
				selector.wakeup();
			}
		} catch (IOException e) {
			log.severe("IOException !!");
		} catch (NoSuchElementException e) {
			log.severe("Scanner closed.");
		}

	}

	/**
	 * Try to fill bbout of the differents contexts
	 *
	 */
	private void fillBuffers() {
		if (!queue.isEmpty()) {
			while (queue.size() > 0) {
				// System.out.println("Somehting to send to the server");
				serverContext.queueMessage(queue.poll());
			}
		} else {
			if (!mapWhisperMessage.isEmpty()) {
				for (String name : mapWhisperMessage.keySet()) {
					ContextClient ct = (ContextClient) getNameInKeys(name).get().attachment();
					// System.out.println(ct.username);
					// System.out.println("Somehting to send to a Client !");
					ct.queueMessage(mapWhisperMessage.remove(name));
				}

			}
		}
	}

	public void addAwaitingUsers(String user) {
		pendingConnectionToken.put(user, generateToken());
	}
	
	public void addAwaitingUsers(String user, int token) {
		pendingConnectionToken.put(user, token);
	}
	
	public Optional<Integer> getPendingConnectionToken(String user) {
		Integer ret = pendingConnectionToken.get(user);
		if (ret == null) {
			return Optional.empty();
		}
		return Optional.of(ret);
	}

	public int removePendingConnectionToken(String user) {
		return pendingConnectionToken.remove(user);
	}

	public void addConnectedUsers(SelectionKey client) {
		connectedClients.add(client);
	}

	/*
	 * private void doWrite() throws IOException { // dowrite ne s'executera pas
	 * tant que l'utilisateur n'aura pas ecrit sur // l'entrÃ©e standard // (en gros
	 * tant que bbout ne sera pas rempli par l'autre thread.) bbout.flip();
	 * bbout.position(0); sc.write(bbout); bbout.compact(); updateInterestOps();
	 * 
	 * }
	 */

	public void doConnect(SelectionKey key) throws IOException {
		if (!sc.finishConnect()) {
			// System.out.println("Not connected now !");
			return;
		}
		((ContextClient) key.attachment()).updateInterestOps();
	}

	/*
	 * private void doRead() throws IOException {
	 * System.out.println("this doread closes: " + closed); if (sc.read(bbin) == -1)
	 * { log.info("Closing connection."); silentlyClose(); return ; } processIn();
	 * updateInterestOps(); }
	 */

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = ssc.accept();
		if (sc == null)
			return; // the selector gave a bad hint
		System.out.println("Quelqu'un s'est connectï¿½!");
		sc.configureBlocking(false);
		SelectionKey ClientKey = sc.register(selector, SelectionKey.OP_READ);
		ContextClient ct = new ContextClient(this, ClientKey);
		ClientKey.attach(ct);
		// System.out.println("===============+++>ADDING:");
		// System.out.println(ct);
		// TODO
		//addConnectedUsers(ClientKey);

	}

	private void processSelectedKeys() throws IOException {

		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			if (key.isValid() && key.isConnectable()) {
				doConnect(key);
			}
			if (key.isValid() && key.isWritable()) {
				((ContextClient) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((ContextClient) key.attachment()).doRead();
			}

		}
	}

	public void launch() throws IOException, InterruptedException {
		sc.configureBlocking(false);
		uniqueKey = sc.register(selector, SelectionKey.OP_CONNECT);
		serverContext = new ContextClient(this, uniqueKey);
		uniqueKey.attach(serverContext);
		addConnectedUsers(uniqueKey); // pour que le serveur soit considï¿½rï¿½ comme connectï¿½.
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		Thread reader = new Thread(this::beginChat);
		reader.start();
		doConnect(uniqueKey);
		while (!Thread.interrupted()) {
			// System.out.println("Closed?: " + closed);
			if (closed == true) {
				reader.join();
				return;
			}
			fillBuffers();
			// Debug.printKeys(selector);
			System.out.println("Selecting keys");
			selector.select();
			// Debug.printSelectedKey(selectedKeys);
			processSelectedKeys();
			selectedKeys.clear();
		}
		reader.join();
	}

	public static void usage() {
		System.out.println("usage: java fr.upem.net.tcp.ClientMatou address port");
	}

	public static void main(String args[]) throws InterruptedException {

		try (Scanner sc = new Scanner(System.in); SocketChannel sock = SocketChannel.open();) {
			if (args.length != 2) {
				usage();
				return;
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
			cm.setUsername(login);
			cm.launch();

		} catch (IOException e) {
			log.severe("IOException, terminating client: " + e.getMessage());
		}
	}
}
