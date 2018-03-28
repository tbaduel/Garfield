package fr.upem.net.tcp;

public class Message {
	private BodyParser bp;
	private int headerSize;
	private int op;
	private byte endFlag;
	
	public Message(BodyParser bp, int headerSize, int op, byte endFlag) {
		this.bp = bp;
		this.headerSize = headerSize;
		this.op = op;
		this.endFlag = endFlag;
	}

	public BodyParser getBp() {
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
	
	
}
