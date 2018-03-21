package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;


public class ServerReader {
	private final SocketChannel sc;
	private final ByteBuffer buff;

	public ServerReader(SocketChannel sc, ByteBuffer buff) {
		this.sc = sc;
		this.buff = buff;
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
	 * @return The ServerHeader object corresponding to the header read
	 * @throws IOException
	 *             GFPException if the connection is closed before a header could
	 *             be read if the header is ill-formed
	 */
	public static ServerHeader readHeader(String data) throws IOException {
		// TODO
		int size = data.length();
		Map<String, String> map = new HashMap<String, String>();
		String []lines = data.split("\r\n");
		for (String line : lines) {
			if (line.equals("") || line.isEmpty()) {
				continue;
			}
			String[] keyvalue = line.split(": ");
			map.merge(keyvalue[0].toLowerCase(), keyvalue[1], (x, y) -> String.join("; ", x, y));
		}
		
		return ServerHeader.create(size, map);
	}
	

}
