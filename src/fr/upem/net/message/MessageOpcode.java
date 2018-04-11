package fr.upem.net.message;

public class MessageOpcode extends Message {
	
	public MessageOpcode(int op, byte endFlag) {
		super(op, endFlag);
	}
}
