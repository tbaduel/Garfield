package fr.upem.net.message;

public class MessageIp extends Message {

	public String ip;
	public int port;
	public String username;
	public String userReq;
	public int token;
	
	public MessageIp(int op, byte endFlag, String ip, int port, String username, String userReq, int token) {
		super(op, endFlag);
		this.ip = ip;
		this.port = port;
		this.username = username;
		this.userReq = userReq;
		this.token = token;
	}

}
