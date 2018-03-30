package fr.upem.net.tcp;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface ServerFunction {
		
	public ByteBuffer apply(Message msg, ServerMatou server, SocketChannel sc);
}
