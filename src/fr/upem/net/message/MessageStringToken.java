package fr.upem.net.message;

public class MessageStringToken extends Message {
	
	public String username;
	public int token;
	
	public MessageStringToken(int op,byte endFlag, String username, int token) {
		super(op, endFlag);
		this.username = username;
		this.token = token;
	}
	
}
