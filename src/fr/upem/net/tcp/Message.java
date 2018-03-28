package fr.upem.net.tcp;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Message {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private String bp;
	private int headerSize;
	private int op;
	private byte endFlag;
	private final static int SIZE = 2 * Integer.BYTES + Byte.BYTES;
	private ByteBuffer bodyBuffer;
	
	public Message(String bp, int headerSize, int op, byte endFlag) {
		this.bp = bp;
		this.headerSize = headerSize;
		this.op = op;
		this.endFlag = endFlag;
		this.bodyBuffer = UTF8.encode(bp);
	}

	public String getBp() {
		return bp;
	}

	public int getHeaderSize() {
		return headerSize;
	}

	public int getOp() {
		return op;
	}

	public byte getEndFlag() {
		return endFlag;
	}
	
	public ByteBuffer getBodyBuffer() {
		return bodyBuffer;
	}
	
	public ByteBuffer toByteBuffer() {
		ByteBuffer ret = ByteBuffer.allocate(bodyBuffer.remaining() + SIZE);
		ret.putInt(op);
		ret.putInt(headerSize);
		ret.put(endFlag);
		ret.putInt(bodyBuffer.remaining());
		ret.put(bodyBuffer);
		return ret;
	}
	
	
}
