package fr.upem.net.server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import fr.upem.net.message.Message;

@FunctionalInterface
public interface ServerFunction {
		
	public ByteBuffer apply(Message msg, ServerMatou server, SocketChannel sc);
}
