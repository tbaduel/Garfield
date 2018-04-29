package fr.upem.net.message;

import java.nio.ByteBuffer;

public class MessageFile extends Message{
	public ByteBuffer buffer;
	public int fileId;
	
	public MessageFile(int op, byte endFlag, int fileId, ByteBuffer buffer) {
		super(op, endFlag);
		this.buffer = buffer;
		this.fileId = fileId;
	}

}
