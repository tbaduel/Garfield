package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

public class HubClient {
	
	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = Integer.BYTES * 2;
	public static final int HEADER_SIZE = Integer.BYTES + Byte.BYTES;
	
	public final HashMap<Opcode, ClientFunction> clientMap = new HashMap<>();
	
	public HubClient() {
		clientMap.put(Opcode.MESSAGEBROADCAST, this::messageBroadcast);
		
	}

	/**
	 * No chunks sent, this sends the bytebuffer corresponding to the format of our packets
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
		req.flip();
		return req;
	}
	
	public void messageBroadcast(BodyParser bp) {
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		System.out.println("\033[1;34m[" + calendar.get(Calendar.HOUR_OF_DAY) + ":" + calendar.get(Calendar.MINUTE)
						+ "]\033[1;32m" + bp.getField("username") + "\033[0;m: " + bp.getField("data"));
	}

	
	public void executeClient(Opcode op, BodyParser bp) {
		ClientFunction function = clientMap.get(op);
		if (function != null) {
			function.apply(bp);
		}
	}

}
