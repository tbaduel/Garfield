package fr.upem.net.message;

import java.nio.ByteBuffer;

public class MessageFile extends Message{
	public ByteBuffer buffer;
	
	public MessageFile(int op, byte endFlag, ByteBuffer buffer) {
		super(op, endFlag);
		this.buffer = buffer;
	}

}
