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

import fr.upem.net.client.ClientMatou.ContextClient;
import fr.upem.net.message.Message;
import fr.upem.net.message.MessageFile;
import fr.upem.net.message.MessageIp;
import fr.upem.net.message.MessageOneString;
import fr.upem.net.message.MessageStringToken;
import fr.upem.net.message.MessageTwoString;
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
	 * @return
	 * @throws IOException
	 */
	public static ByteBuffer formatBufferFile(ByteBuffer bodyfile, int id, boolean endFlag) throws IOException {
		byte endMsg = (byte) (endFlag == true ? 1 : 0);
		ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
		header.put(endMsg);
		header.putInt(bodyfile.remaining());
		header.flip();
		ByteBuffer req = ByteBuffer.allocate(header.remaining() + bodyfile.remaining() + BUFFER_SIZE);
		req.putInt(id);
		req.putInt(header.remaining());
		req.put(header);
		req.put(bodyfile);
		return req;
	}

	public void messageBroadcast(Message msg, ClientMatou client, ContextClient ctc) {
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

	public void receiveIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
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
			SelectionKey ClientKey = newsc.register(client.selector, SelectionKey.OP_CONNECT);
			ContextClient ct = new ContextClient(client, ClientKey);
			ct.setUserName(message.userReq);
			client.addUsernameContext(message.userReq, ClientKey);
			ClientKey.attach(ct);
			client.doConnect(ClientKey);
			client.addConnectedUsers(ClientKey);
			System.out.println("SETTING USERNAME: " + message.userReq);
			System.out.println("QUEUING MESSAGE FOR PRIVATE");
			ct.queueMessage(formatBuffer(newsc,
					"username: " + client.username + "\r\ntoken: "
							+ client.getPendingConnectionToken(message.userReq).orElse(-1).toString(),
					Opcode.CHECK_PRIVATE.op));
		} catch (IOException e) {
			System.err.println("IOException!!");
		}
	}

	public void checkPrivate(Message msg, ClientMatou client, ContextClient ctc) {
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
		client.addConnectedUsers(ctc.getSelectionKey());
		client.addUsernameContext(newUsername, ctc.getSelectionKey());
		client.removePendingConnectionToken(message.username);

	}

	public void authorizeIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
		MessageOneString message = (MessageOneString) msg;
		System.out.println(ColorText.colorize(ColorText.GREEN, message.str)
				+ " wants to communicate with you, to authorize requests, type /y " + message.str);
		client.addAwaitingUsers(message.str);
		// System.out.println("add username : " + bp.getField("username"));

	}

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

	public void errorIpAddress(Message msg, ClientMatou client, ContextClient ctc) {
		MessageOneString message = (MessageOneString) msg;
		System.err.println(message.str + " failed to communicate with you.");
	}

	public void sendFileRequest(Message msg, ClientMatou client, ContextClient ctc) {
		MessageTwoString message = (MessageTwoString) msg;
		System.out.println(ColorText.colorize(ColorText.GREEN, message.str1) + " wants to send you a file named: "
				+ message.str2 + ", to authorize the file, type /o " + message.str1 + " " + message.str2);
		client.addAwaitingFileUser(message.str2, message.str1);
	}

	public void acceptedFileRequest(Message msg, ClientMatou client, ContextClient ctc) {
		MessageTwoString message = (MessageTwoString) msg;
		System.out.println("Sending file : " + message.str2 + " to " + message.str1);
		// System.out.println("Use /f " + message.str + "path/to/your/file to send a
		// file to " + message.str);
		client.addAuthorizedToSendFile(ctc.getSelectionKey());
		Path path = Paths.get(message.str2);
		System.out.println(path.toString());
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
			int size = (int) fc.size();
			int readed;
			ByteBuffer buffer = ByteBuffer.allocate(499);
			// while (fc.read(buffer) > 0 && buffer.hasRemaining()) {
			while (fc.position() < size) {
				System.out.println("pos = " + fc.position() + ", size = " + size);
				if (buffer.remaining() < size - fc.position()) {
					readed = fc.read(buffer);
					if (readed != -1) {
						buffer.flip();
						ctc.queueMessage(formatBufferFile(buffer.duplicate(),Opcode.FILE_SEND.op , false));
					}
					buffer.clear();

				} else {
					readed = fc.read(buffer);
					if (readed != -1) {
						buffer.flip();
						ctc.queueMessage(formatBufferFile(buffer.duplicate(), Opcode.FILE_SEND.op, true));
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
	
	public void receiveFile(Message msg, ClientMatou client, ContextClient ctc) {
		System.out.println("Receiving file ...");
		try(FileChannel fc = FileChannel.open(Paths.get("./file"), 
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.APPEND)){
			MessageFile message = (MessageFile)msg;
			fc.write(message.buffer);
			if (message.endFlag == (byte)1) {
				System.out.println("File received !");
			}
			
				
			} catch (IOException e) {
				System.out.println("Erreur pendant la reception");
			}
			
		}
		
	
}
