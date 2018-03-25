package fr.upem.net.tcp;

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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerMatou {

	static private class Context {

		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
		final private Queue<ByteBuffer> queue = new LinkedList<>();
		final private ServerMatou server;
		private boolean closed = false;

		/* Try to read smthng */
		private boolean opCodeReaded = false;
		private boolean headerSizeReaded = false;
		private boolean headerReaded = false;
		private boolean messageReaded = false;

		private boolean endFlag = false;

		private Opcode opCode;
		private int headerSize = 0;
		private int messageSize = 0;
		private ByteBuffer header;
		private ByteBuffer message;

		// final private MessageReader messageReader = new MessageReader(bbin);

		private Context(ServerMatou server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
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

			// get Opcode
			if (!opCodeReaded && bbin.remaining() >= Integer.BYTES) {
				opCode = Opcode.valueOfId(bbin.getInt());
				opCodeReaded = true;
			}

			// get header size
			if (opCodeReaded && !headerSizeReaded && bbin.remaining() >= Integer.BYTES) {
				// server.broadcast(bbin.getInt());
				headerSize = bbin.getInt();
				headerSizeReaded = true;
			}

			// get header
			if (headerSizeReaded && !headerReaded && bbin.remaining() >= headerSize) {
				header = readBytes(headerSize);
				//header.flip();
				endFlag = (header.get() == 1 ? true : false);
				messageSize = header.getInt();
				headerReaded = true;
			}

			// get body
			if (headerReaded && !messageReaded && bbin.remaining() >= messageSize) {
				message = readBytes(messageSize);
				opCodeReaded = false;
				headerReaded = false;
				headerSizeReaded = false;
				messageReaded = false;
				server.broadcast(messageProcessing());
			}

			bbin.compact();
		}

		public ByteBuffer messageProcessing() throws IOException {
			ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
			String bodyString = UTF8.decode(message).toString();
			BodyParser bp = ServerReader.readBody(bodyString);
			String name;
			switch (opCode) {
			case SIGNUP:
				System.out.println("Signin...");
				name = bp.getField("username");
				if (server.map.containsValue(name)) { 						// Username already used
					bb.putInt(Opcode.SIGNUP_ERR.op);
				} else { 													// Username not used
					bb.putInt(Opcode.SIGNUP_OK.op);
					System.out.println("ADDED: " + Opcode.SIGNUP_OK);
					server.map.put(sc.getRemoteAddress(), name);
					server.userMap.put(name, bp.getField("password"));
				}
				break;

			case LOGIN:
				System.out.println("Login...");
				name = bp.getField("username");
				String password = bp.getField("password");
				if (server.map.containsValue(name)) { 						// Username exists
					if (server.userMap.get(name) == password) {
						bb.putInt(Opcode.LOGIN_OK.op);
					}
					else {
						bb.putInt(Opcode.LOGIN_ERR.op);	
					}
				} else { 													
					bb.putInt(Opcode.LOGIN_ERR.op);
				}

				break;

			case MESSAGE:
				name = server.map.get(sc.getRemoteAddress());
				name = "username: " + name + "\r\n";
				ByteBuffer headerToSend = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
				bb.putInt(Opcode.MESSAGEBROADCAST.op);
				ByteBuffer bodyToSend = ByteBuffer.allocate(BUFFER_SIZE);

				// add content to body's buffer
				bodyToSend.put(UTF8.encode(name));
				bodyToSend.put(UTF8.encode(bp.getField("data")));
				bodyToSend.flip();

				// Add content to header's buffer
				headerToSend.put(endFlag == true ? (byte) 1 : (byte) 0);
				headerToSend.putInt(bodyToSend.remaining());
				headerToSend.flip();

				// Add header and body to ByteBuffer's response
				bb.put(headerToSend);
				bb.put(bodyToSend);
				break;

			default:
				break;

			}
			return bb;
		}

		/**
		 * Add a message to the message queue, tries to fill bbOut and updateInterestOps
		 *
		 * @param msg
		 */
		private void queueMessage(ByteBuffer msg) {
			// TODO
			queue.add(msg);
			processOut();
			updateInterestOps();
		}

		/**
		 * Try to fill bbout from the message queue
		 *
		 */
		private void processOut() {
			// TODO
			while (bbout.remaining() >= Integer.BYTES && queue.size() > 0) {
				System.out.println(bbout.remaining());
				ByteBuffer a = queue.poll();
				System.out.println("add : " + a);
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
			// TODO
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

		private void silentlyClose() {
			try {
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
			// TODO
			if (sc.read(bbin) == -1)
				closed = true;
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
			// TODO
			bbout.flip();
			sc.write(bbout);
			bbout.compact();
			updateInterestOps();
		}

		/**
		 * @param size
		 * @return a ByteBuffer in write-mode containing size bytes read on the socket
		 * @throws IOException
		 *             IOException is the connection is closed before all bytes could be
		 *             read
		 */
		public ByteBuffer readBytes(int size) throws IOException {
			ByteBuffer bb = ByteBuffer.allocate(size);
			System.out.println("Allocate size = " + size);
			bbin.flip();
			if (bbin.hasRemaining()) {
				if (bbin.remaining() <= bb.remaining()) {
					System.out.println("plus petit");
					bb.put(bbin);
					bbin.clear();
				}
			}
			return bb;
		}

	}

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	private void broadcast(ByteBuffer msg) {
		// TODO
		System.out.println("\n\t SIZE = " + selector.keys().size());
		for (SelectionKey key : selector.keys()) {
			System.out.println("une key");
			if (key.isValid() && !key.isAcceptable()) {
				System.out.println("Ajout");
				((Context) key.attachment()).queueMessage(msg);
			}
		}

	}

	static private int BUFFER_SIZE = 1_024;
	static private Logger logger = Logger.getLogger(ServerMatou.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<SocketAddress, String> map; // Address => Username
	private final HashMap<String, String> userMap; // Username => password

	private final static Charset UTF8 = Charset.forName("utf-8");

	public ServerMatou(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		map = new HashMap<>();
		userMap = new HashMap<>();
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
			printSelectedKey();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			try {
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
		// TODO
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
		new ServerMatou(Integer.parseInt(args[0])).launch();
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
}
