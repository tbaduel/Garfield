package fr.upem.net.message;


public class MessageOneString extends Message {
	public String str;
	
	public MessageOneString(int op, byte endFlag, String str) {
		super(op, endFlag);
		this.str = str;
	}

}
