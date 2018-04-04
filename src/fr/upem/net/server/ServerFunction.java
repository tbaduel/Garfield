package fr.upem.net.server;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface ServerFunction {
		
	public ByteBuffer apply(Message msg, ServerMatou server, SocketChannel sc);
}
