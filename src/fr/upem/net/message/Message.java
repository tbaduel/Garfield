package fr.upem.net.message;

import java.nio.charset.Charset;

public abstract class Message {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	//private String body;
	//private BodyParser bp;
	//private int headerSize;
	private int op;
	public byte endFlag;
	//private final static int SIZE = 2 * Integer.BYTES + Byte.BYTES;
	//private ByteBuffer bodyBuffer;
	
	/*public Message(String body, int headerSize, int op, byte endFlag) throws IOException {
		this.body = body;
		this.bp = BodyParser.readBody(body);
		this.headerSize = headerSize;
		this.op = op;
		this.endFlag = endFlag;
		this.bodyBuffer = UTF8.encode(body);
	}*/
	
	public Message(int op, byte endFlag) {
		this.op = op;
		this.endFlag = endFlag;
	}
	
	
	/*
	public BodyParser getBp() {
		return bp;
	}
	
	public String getBody() {
		return body;
	}

	public int getHeaderSize() {
		return headerSize;
	}
	*/
	public int getOp() {
		return op;
	}
	
	/*
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
	
	public String toString() {
		return "[" + op + ", "  + body + "]";
		
	}
	
	*/
	
}
