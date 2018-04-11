package fr.upem.net.message;

public class MessageTwoString extends Message {
	public String str1;
	public String str2;
	
	public MessageTwoString(int op, byte endFlag, String str1, String str2) {
		super(op, endFlag);
		this.str1 = str1;
		this.str2 = str2;
	}
	
}
