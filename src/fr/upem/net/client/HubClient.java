package fr.upem.net.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Optional;

import fr.upem.net.client.ClientMatou.ContextClient;
import fr.upem.net.message.Message;
import fr.upem.net.message.MessageFile;
import fr.upem.net.message.MessageIp;
import fr.upem.net.message.MessageOneString;
import fr.upem.net.message.MessageStringToken;
import fr.upem.net.message.MessageTwoString;
import fr.upem.net.message.MessageTwoStringOneInt;
import fr.upem.net.other.ColorText;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.ParserLine;

public class HubClient {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = Integer.BYTES * 2;
	public static final int HEADER_SIZE = Integer.BYTES + Byte.BYTES;
	private String colorText;
	private String colorUsername;
	private String colorHour;
	public final HashMap<Opcode, ClientFunction> clientMap = new HashMap<>();

	public HubClient() {
		clientMap.put(Opcode.MESSAGEBROADCAST, this::messageBroadcast);
		clientMap.put(Opcode.WHISP, this::messageBroadcast);
		clientMap.put(Opcode.WHISP_REQUEST, this::authorizeIpAddress);
		clientMap.put(Opcode.IPRESPONSE, this::receiveIpAddress);
		clientMap.put(Opcode.CHECK_PRIVATE, this::checkPrivate);
		clientMap.put(Opcode.WHISP_ERR, this::errorIpAddress);
		clientMap.put(Opcode.FILE_REQUEST, this::sendFileRequest);
		clientMap.put(Opcode.FILE_OK, this::acceptedFileRequest);
		clientMap.put(Opcode.FILE_SEND, this::receiveFile);
		clientMap.put(Opcode.CHECK_FILE, this::checkfile);
		clientMap.put(Opcode.FILE_REFUSED, this::fileRefused);
	}

	/**
	 * No chunks sent, this sends the bytebuffer corresponding to the format of our
	 * packets
	 * 
	 * @throws IOException
	 */
	public static ByteBuffer formatBuffer(SocketChannel sc, String bodyString, int id) throws IOException {

		ByteBuffer body = UTF8.encode(bodyString);
		byte endMsg = (byte) 1; // change
		ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
		header.put(endMsg);
		header.putInt(body.remaining());
		header.flip();
		ByteBuffer req = ByteBuffer.allocate(header.remaining() + body.remaining() + BUFFER_SIZE);
		req.putInt(id);
		req.putInt(header.remaining());
		req.put(header);
		req.put(body);
		// req.flip();
		// System.out.println(req);
		return req;
	}

	/**
	 * Create a ByteBuffer for a File transfers
	 * 
	 * @param bodyString
	 * @param id
	 * @param endFlag
	 * @return a ByteBuffer of a message to send
	 * @throws IOException
	 */
	public static ByteBuffer formatBufferFile(ByteBuffer bodyfile, int id, int fileId, boolean endFlag) throws IOException {
		byte endMsg = (byte) (endFlag == true ? 1 : 0);
		ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE + Integer.BYTES);		// One Integer more
		header.put(endMsg);
		header.putInt(fileId);
		header.putInt(bodyfile.remaining());
		header.flip();
		ByteBuffer req = ByteBuffer.allocate(header.remaining() + bodyfile.remaining() + BUFFER_SIZE);
		req.putInt(id);
		req.putInt(header.remaining());
		req.put(header);
		req.put(bodyfile);
		return req;
	}
	
	/**
	 * Display a broadcasted message
	 * @param msg
	 * @param client
	 * @param ctc
	 */
	private void messageBroadcast(Message msg, ClientMatou client, ContextClient ctc) {
		System.out.println("My token is: " + client.getToken());
		MessageTwoString message = (MessageTwoString) msg;
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		ColorText.start(colorHour);
		System.out.print("[" + calendar.get(Calendar.HOUR_OF_DAY) + ":" + calendar.get(Calendar.MINUTE) + "] ");
		System.out.print(ColorText.colorize(colorUsername, message.str1) + ": ");
		System.out.println(ColorText.colorize(colorText, message.str2));
	}
	
	/**
	 * Received ip address Message type
	 * @param msg
	 * @param client
	 * @param ctc
	 */
	private void receiveIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
		try {
			MessageIp message = (MessageIp) msg;
			System.out.println("message token from username: " + message.username + " - " + message.token
					+ " to client token: " + client.getToken());
			client.addAwaitingUsers(message.userReq, message.token);
			SocketChannel newsc = SocketChannel.open();
			String ip = ParserLine.formatIpBack(message.ip);
			System.out.println("IP TO CONNECT TO :" + ip);
			newsc.connect(new InetSocketAddress(ip, message.port));
			newsc.configureBlocking(false);
			
			// First socket channel : msgSocketChannel
			SelectionKey clientKey = newsc.register(client.selector, SelectionKey.OP_CONNECT);
			ContextClient ct = new ContextClient(client, clientKey);
			ct.setUserName(message.userReq);
			client.addUsernameContext(message.userReq, clientKey);
			clientKey.attach(ct);
			client.doConnect(clientKey);
			client.addConnectedUsers(clientKey);
			System.out.println("SETTING USERNAME: " + message.userReq);
			System.out.println("QUEUING MESSAGE FOR PRIVATE");
			ct.queueMessage(formatBuffer(newsc,
					"username: " + client.username + "\r\ntoken: "
							+ client.getPendingConnectionToken(message.userReq).orElse(-1).toString(),
					Opcode.CHECK_PRIVATE.op));
			
			//TODO Faire la deuxieme socketChannel
			
			SocketChannel scFile = SocketChannel.open();
			scFile.connect(new InetSocketAddress(ip,message.port));
			scFile.configureBlocking(false);
			SelectionKey clientFileKey = scFile.register(client.selector, SelectionKey.OP_CONNECT);
			ContextClient ctFile = new ContextClient(client, clientFileKey);
			clientFileKey.attach(ctFile);
			client.doConnect(clientFileKey);
			client.addConnectedUsers(clientFileKey);
			ctFile.queueMessage(formatBuffer(newsc,
					"username: " + client.username + "\r\ntoken: "
							+ client.getPendingConnectionToken(message.userReq).orElse(-1).toString(),
					Opcode.CHECK_FILE.op));
			System.out.println("Adding Filecontext : " + message.userReq);
			client.addFileContext(message.userReq, ctFile);
			
			//TODO add to Filemap
			
			
			
			
			
		} catch (IOException e) {
			System.err.println("IOException!!");
		}
	}
	
	/**
	 * Check connection for message socket, and add to client's connected users
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	private void checkPrivate(Message msg, ClientMatou client, ContextClient ctc) {
		MessageStringToken message = (MessageStringToken) msg;
		int token = message.token;
		System.out.println("checking private, token: " + token);
		if (client.getPendingConnectionToken(message.username).orElse(-1) != token) {
			ctc.silentlyClose();
		}

		String newUsername = message.username;
		if (newUsername != null) {
			ctc.setUserName(newUsername);
		}
		System.out.println("Message.username = " + message.username);
		client.addConnectedUsers(ctc.getSelectionKey());
		client.addUsernameContext(newUsername, ctc.getSelectionKey());
		//client.removePendingConnectionToken(message.username);

	}
	
	/**
	 * Check connection for file socket transfer, and add to client's connected users
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	private void checkfile(Message msg, ClientMatou client, ContextClient ctc) {
		MessageStringToken message = (MessageStringToken)msg;
		int token = message.token;
		if (client.getPendingConnectionToken(message.username).orElse(-1) != token) {
			System.out.println("Wrong connection, closing ...");
			ctc.silentlyClose();
		}

		String newUsername = message.username;
		if (newUsername != null) {
			ctc.setUserName(newUsername + "_file");
		}
		client.addConnectedUsers(ctc.getSelectionKey());
		client.addFileContext(newUsername, ctc);
		//client.removePendingConnectionToken(message.username);
	}
	
	/**
	 * Process Ip message authorization
	 * @param msg
	 * @param client
	 * @param ctc
	 */
	private void authorizeIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
		MessageOneString message = (MessageOneString) msg;
		System.out.println(ColorText.colorize(ColorText.GREEN, message.str)
				+ " wants to communicate with you, to authorize requests, type /y " + message.str);
		client.addAwaitingUsers(message.str);
		// System.out.println("add username : " + bp.getField("username"));

	}
	
	/**
	 * Execute action corresponding to opcode
	 * @param op
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	public void executeClient(Opcode op, Message msg, ClientMatou client, ContextClient ctc) {
		ClientFunction function = clientMap.get(op);
		if (op.op == Opcode.WHISP.op) {
			colorText = ColorText.PURPLE;
			colorUsername = ColorText.PURPLE;
			colorHour = ColorText.PURPLE;
		} else {
			colorText = ColorText.WHITE;
			colorUsername = ColorText.GREEN;
			colorHour = ColorText.BLUE;
		}
		if (function != null) {
			function.apply(msg, client, ctc);
		}
	}
	
	/**
	 * Process error Ip Address Message
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	private void errorIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
		MessageOneString message = (MessageOneString) msg;
		System.err.println(message.str + " failed to communicate with you.");
	}
	
	/**
	 * Process a File request message
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	private void sendFileRequest(Message msg, ClientMatou client, ContextClient ctc) {
		MessageTwoString message = (MessageTwoString) msg;
		System.out.println(ColorText.colorize(ColorText.GREEN, message.str1) + " wants to send you a file named: "
				+ message.str2 + ", to authorize the file, type /o " + message.str1 + " " + message.str2);

		// Get a random number for file ID 
		int fileId = client.generateToken();
		client.addAwaitingFileUser(message.str1, message.str2, fileId);
	}
	
	
	/**
	 * Send file to another client.
	 * At this moment, fill the file queue of this other Contextclient
	 * @param msg the Message
	 * @param client
	 * @param msgContext
	 */
	private void acceptedFileRequest(Message msg, ClientMatou client, ContextClient msgContext) {
		MessageTwoStringOneInt message = (MessageTwoStringOneInt) msg;
		//System.out.println("Sending file : " + message.str2 + " to " + message.str1);
		// System.out.println("Use /f " + message.str + "path/to/your/file to send a
		// file to " + message.str);
		ContextClient ctc = client.getContextFileFromUsername(message.str1);
		if (ctc == null) {
			return;
		}
		client.addAuthorizedToSendFile(ctc.getSelectionKey());
		Path path = Paths.get(message.str2);
		
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
			int size = (int) fc.size();
			int fileId = message.integer;
			int readed;
			ByteBuffer buffer = ByteBuffer.allocate(494);
			// while (fc.read(buffer) > 0 && buffer.hasRemaining()) {
			while (fc.position() < size) {
				if (buffer.remaining() < size - fc.position()) {
					readed = fc.read(buffer);
					if (readed != -1) {
						buffer.flip();
						ctc.queueMessage(formatBufferFile(buffer.duplicate(),Opcode.FILE_SEND.op, fileId, false));
					}
					buffer.clear();

				} else {
					
					readed = fc.read(buffer);
					if (readed != -1) {
						buffer.flip();
						ctc.queueMessage(formatBufferFile(buffer.duplicate(), Opcode.FILE_SEND.op, fileId, true));
					}
					buffer.clear();

				}
			}
			//buffer.flip();
			//System.out.println(UTF8.decode(buffer).toString());
		} catch (IOException e) {
			System.out.println(e.toString());
			System.err.println("Erreur dans la lecture, envoi annulé.");
		}

	}
	
	/**
	 * Receive a file
	 * @param msg the Message
	 * @param client
	 * @param ctc
	 */
	private void receiveFile(Message msg, ClientMatou client, ContextClient ctc) {
		
		MessageFile message = (MessageFile)msg;
		Optional<String> OptionalstringPath = client.getFileFromId(message.fileId);
		if (!OptionalstringPath.isPresent()) {
			return;
		}
		String stringPath = OptionalstringPath.get();
		
		try(FileChannel fc = FileChannel.open(Paths.get(stringPath), 
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.APPEND)){
			
			fc.write(message.buffer);
			if (message.endFlag == (byte)1) {
				System.out.println("File received !");
				//TODO remove and check file size
				//client.removeFile(message.fileId);
			}
			
				
			} catch (IOException e) {
				System.out.println("Erreur pendant la reception");
			}
			
		}
	
	private void fileRefused(Message msg, ClientMatou client, ContextClient ctc) {
		System.out.println("File tranfert refused");
	}
		
	
}
