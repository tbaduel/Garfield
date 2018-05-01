package fr.upem.net.message;

public abstract class Message {
	private int op;
	public byte endFlag;
	
	public Message(int op, byte endFlag) {
		this.op = op;
		this.endFlag = endFlag;
	}
	
	/**
	 * Get the op (integer) of the Message
	 * @return
	 */
	public int getOp() {
		return op;
	}
	

	
}
