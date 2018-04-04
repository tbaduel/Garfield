package fr.upem.net.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.client.ClientMatou;
import fr.upem.net.other.Opcode;
import fr.upem.net.reader.MessageReader;
import fr.upem.net.reader.Reader.ProcessStatus;

public class ServerMatou {

	public static class Context {

		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
		final private Queue<ByteBuffer> queue = new LinkedList<>();
		final private ServerMatou server;
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

		private Context(ServerMatou server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
			this.client = null;
		}
		
		public Context(ClientMatou client, SelectionKey key) {
			//added for whisper
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = null;
			this.client = client;
		}
		
		
		public void setUserName(String username) {
			//added for whisper
			this.username = username;
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
			System.out.println("BBIN :  " + bbin);
			ProcessStatus ps = messageReader.process();
			System.out.println(ps);
			if (ps == ProcessStatus.DONE) {
				Message msg = messageReader.get();
				// ByteBuffer toSend = msg.toByteBuffer();
				ByteBuffer toSend = messageProcessing(msg);
				System.out.println(msg);
				System.out.println(toSend);
				System.out.println("BBIN :  " + bbin);
				if (toSend != null) {
					// toSend.flip();
					System.out.println(toSend);
					if (msg.getOp() == Opcode.MESSAGE.op) { // Broadcast case
						server.broadcast(toSend);
						
					}else if (msg.getOp() == Opcode.REQUEST.op) {
						//TODO
						System.out.println("Request : opcode = " + opcodeAction);
						//toSend.position(0);
						if (opcodeAction == Opcode.WHISP_OK){
							/*
							int port = Integer.parseInt(msg.getBp().getField("port"));
							String ip = msg.getBp().getField("ip");
							*/
							Context contextDest = server.getContextFromIP(server.getKeyFromMap(msg.getBp().getField("username")));
							contextDest.queueMessage(toSend);
						}
						else if (opcodeAction == Opcode.WHISP_REQUEST) {
							Context contextDest = server.getContextFromIP(server.getKeyFromMap(msg.getBp().getField("userReq")));
							System.out.println(contextDest.sc.getRemoteAddress().toString());
							contextDest.queueMessage(toSend);
						}
						else if (opcodeAction == Opcode.WHISP_ERR) {
							queueMessage(toSend);
						}
						opcodeAction = null;
						
						
					}
					else {				
						System.out.println("Sending to client ...");
						queueMessage(toSend);
					}
					messageReader.reset();
					bbin.compact();
				}
			} else {
				System.out.println("not done");
			}

			System.out.println("Endremaining = " + bbin.remaining());
		}

		/**
		 * Process the message
		 * 
		 * @param msg
		 * @return The buffer of the processed message
		 * @throws IOException
		 */
		public ByteBuffer messageProcessing(Message msg) throws IOException {

			HubServ hub = new HubServ();
			ByteBuffer tmp = hub.ServerExecute(msg, server, sc);
			opcodeAction = hub.getOpcodeAction();
			return tmp;

		}

		/**
		 * Add a message to the message queue, tries to fill bbOut and updateInterestOps
		 *	The message is a ByteBuffer in write mode
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
			while (bbout.remaining() >= Integer.BYTES && queue.size() > 0) {
				System.out.println("remaining bbout " + bbout.remaining());
				ByteBuffer a = queue.poll();
				System.out.println("add : " + a);
				a.flip();
				bbout.put(a);
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
		 * Close the socketChannel to the current Context.
		 * Note that it remove the connection from the server map connection
		 */
		private void silentlyClose() {
			try {
				server.map.remove(sc.getRemoteAddress());
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
				logger.info("closing");
				closed = true;
			}
			System.out.println("--------------\n jai lu " + read + "bytes");
			processIn();
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
			System.out.println("ID to send = " + bbout.getInt());
			bbout.position(0);
			System.out.println("Avant envoie : " + bbout);
			System.out.println("WRITING " + sc.write(bbout));
			bbout.compact();
			System.out.println("Il reste a envoyer " + bbout);
			updateInterestOps();
		}

	}
	
	/**
	 * Get the Context corresponding to the ip
	 * @param ip
	 * @return The context
	 */
	public Context getContextFromIP(SocketAddress ip) {
		for (SelectionKey key : selector.keys()) {
			SelectableChannel channel = key.channel();
			if(!(channel instanceof ServerSocketChannel)) {
				SocketChannel sc = (SocketChannel) channel;
				try {
					if (sc.getRemoteAddress().equals(ip)) {
						return ((Context) key.attachment());
					}
				} catch (IOException e) {
					return null;
				}
				
			}
		}
		return null;
	}
	
	

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	private void broadcast(ByteBuffer msg) {
		System.out.println("BROADCASTING ...");
		if (msg == null)
			return;
		System.out.println("\n\t SIZE = " + selector.keys().size());
		for (SelectionKey key : selector.keys()) {
			System.out.println("une key");
			if (key.isValid() && !key.isAcceptable()) {
				System.out.println("Ajout");
				((Context) key.attachment()).queueMessage(msg.duplicate());
			}
		}

	}

	static private int BUFFER_SIZE = 1_024;
	static private Logger logger = Logger.getLogger(ServerMatou.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	final HashMap<SocketAddress, String> map; // Address => Username
	final HashMap<String, String> userMap; // Username => password
	final BlockingQueue<String> consoleQueue;
	private Thread console;


	//private final static Charset UTF8 = Charset.forName("utf-8");

	public ServerMatou(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		map = new HashMap<>();
		userMap = new HashMap<>();
		consoleQueue = new LinkedBlockingQueue<>();
	}
	
	private SocketAddress getKeyFromMap( String username) {
		Collection<SocketAddress> keys = map.keySet();
		for (SocketAddress address : keys) {
			if (map.get(address).equals(username)) {
				return address;
			}
		}
		return null;
	}
	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			printKeys();
			System.out.println("Starting select");
			selector.select();
			System.out.println("Select finished");
			executeCommand();
			printSelectedKey();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void executeCommand() {
		while (consoleQueue.size() > 0) {
			switch (consoleQueue.poll()) {
			case "INFO":
				System.out.println("Connected : " + infoActive() + " client(s)");
				break;
			case "SHUTDOWN":
				System.out.println("Shutdown...");
				shutdown();
				//console.interrupt();
				//Thread.currentThread().interrupt();
				break;
			case "SHUTDOWNNOW":
				System.out.println("ShutDown NOW !");
				shutdownNow();
				console.interrupt();
				Thread.currentThread().interrupt();
				break;
			default:
				System.out.println("unkown command");
			}
		}
	}

	public int infoActive() {
		int cpt = 0;
		for (SelectionKey key : selector.keys()) {
			SelectableChannel channel = key.channel();
			if (!(channel instanceof ServerSocketChannel)) {
				cpt++;
			}
		}
		return cpt;
	}

	public void shutdownNow() {
		//TODO
		boolean done = false;
		while (!done) {
			done = true;
			for (SelectionKey key : selector.keys()) {
				SelectableChannel channel = key.channel();
				if (!(channel instanceof ServerSocketChannel) && (((Context) key.attachment()).sc != null)) {
					if (((Context) key.attachment()).sc.isOpen()) {
						done = false;
						logger.info("Disconnect client");
						((Context) key.attachment()).silentlyClose();
						System.out.println(((Context) key.attachment()).sc.isConnected());
					}
				}
			}
		}
	}

	public void shutdown() {
		// TODO
		logger.info("Shutdown requested ...");
		for (SelectionKey key : selector.keys()) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				silentlyClose(key);
				return;
			}
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			try {
				if (!key.isValid() && key.attachment() != null) {
					System.out.println("Remove" + ((Context) key.attachment()).sc.getRemoteAddress() );
					map.remove(((Context) key.attachment()).sc.getRemoteAddress());
				}
				if (key.isValid() && key.isWritable()) {
					((Context) key.attachment()).doWrite();
				}
				if (key.isValid() && key.isReadable()) {
					((Context) key.attachment()).doRead();
				}
			} catch (IOException e) {
				logger.log(Level.INFO, "Connection closed with client due to IOException", e);
				silentlyClose(key);
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return; // the selector gave a bad hint
		sc.configureBlocking(false);
		SelectionKey ClientKey = sc.register(selector, SelectionKey.OP_READ);
		Context ct = new Context(this, ClientKey);
		ClientKey.attach(ct);

	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		ServerMatou matou = new ServerMatou(Integer.parseInt(args[0]));
		matou.console = new Thread(() -> {
			consoleRunner(matou);
		});
		matou.console.start();
		matou.launch();

	}

	private static void usage() {
		System.out.println("Usage : ServerMatou port");
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}

		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	private void printSelectedKey() {
		if (selectedKeys.isEmpty()) {
			System.out.println("There were not selected keys.");
			return;
		}
		System.out.println("The selected keys are :");
		for (SelectionKey key : selectedKeys) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println(
						"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
			}

		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}

	private static void consoleRunner(ServerMatou server) {
		String command = "";
		try (Scanner scan = new Scanner(System.in)) {
			while (!Thread.interrupted() && !command.equals("SHUTDOWNNOW")) {
				if (scan.hasNextLine()) {
					command = scan.nextLine();
					System.out.println("Command = " + command);
					server.consoleQueue.put(command);
					server.selector.wakeup();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

	}

}
