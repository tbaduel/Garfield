package fr.upem.net.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

import fr.upem.net.other.ColorText;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.BodyParser;
import fr.upem.net.client.ClientMatou.ContextClient;

public class HubClient {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = Integer.BYTES * 2;
	public static final int HEADER_SIZE = Integer.BYTES + Byte.BYTES;

	public final HashMap<Opcode, ClientFunction> clientMap = new HashMap<>();

	public HubClient() {
		clientMap.put(Opcode.MESSAGEBROADCAST, this::messageBroadcast);
		clientMap.put(Opcode.WHISP, this::messageBroadcast);
		clientMap.put(Opcode.WHISP_REQUEST, this::authorizeIpAddress);
		clientMap.put(Opcode.IPRESPONSE, this::receiveIpAddress);
		clientMap.put(Opcode.CHECK_PRIVATE, this::checkPrivate);
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
		//req.flip();
		System.out.println(req);
		return req;
	}

	public void messageBroadcast(BodyParser bp, ClientMatou client) {
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		ColorText.start(ColorText.BLUE);
		System.out.print("[" + calendar.get(Calendar.HOUR_OF_DAY) + ":"
				+ calendar.get(Calendar.MINUTE) + "] ");
		System.out.println(ColorText.colorize(ColorText.GREEN, bp.getField("username"))
				+ ": " + bp.getField("data"));
	}

	public void receiveIpAddress(BodyParser bp, ClientMatou client) {
		System.out.println("Received IP to connect to: " + bp.getField("ip") + ":" + bp.getField("port"));
		//if (!client.removeAwaitingUsers(bp.getField("userReq"))) {
			//System.out.println("No user found to connect to. User : " + bp.getField("userReq"));
			//return;
		//}
		try {
			SocketChannel sc = SocketChannel.open();
			sc.connect(new InetSocketAddress(bp.getField("ip"), Integer.parseInt(bp.getField("port"))));
			sc.configureBlocking(false);
			SelectionKey ClientKey = sc.register(client.selector, SelectionKey.OP_CONNECT);
			ContextClient ct = new ContextClient(client, ClientKey);
			ct.setUserName(bp.getField("userReq"));
			ClientKey.attach(ct);
			client.addConnectedUsers(ClientKey);
			client.doConnect(ClientKey);
			ct.queueMessage(formatBuffer(sc,"username: "+ client.username + "\r\ntoken: " + bp.getField("token") ,Opcode.CHECK_PRIVATE.op));
			/*client.ssc.register(client.selector, SelectionKey.OP_ACCEPT);
			SocketChannel sc = client.ssc.accept();
			if (sc == null)
				return; 
			sc.configureBlocking(false);
			SelectionKey ClientKey = sc.register(client.selector, SelectionKey.OP_READ);
			Context ct = new Context(client, ClientKey);
			ct.setUserName(bp.getField("username"));
			ClientKey.attach(ct);*/
		} catch (IOException e) {
			client.log.severe("IOException!!");
		}
		// and connect here.
	}
	
	public void checkPrivate(BodyParser bp, ClientMatou client) {
		
	}

	public void authorizeIpAddress(BodyParser bp, ClientMatou client) {
		System.out.println(ColorText.colorize(ColorText.GREEN, bp.getField("username"))
				+ " wants to communicate with you, to authorize requests, type /y " + bp.getField("username"));
		client.addAwaitingUsers(bp.getField("username"));
		System.out.println("add username : " + bp.getField("username"));
	}

	public void executeClient(Opcode op, BodyParser bp, ClientMatou client) {
		ClientFunction function = clientMap.get(op);
		if (function != null) {
			function.apply(bp, client);
		}
	}

}
