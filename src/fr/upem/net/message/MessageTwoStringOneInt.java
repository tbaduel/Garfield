package fr.upem.net.message;

public class MessageTwoStringOneInt extends Message{
	public String str1;
	public String str2;
	public int integer;
	public MessageTwoStringOneInt(int op, byte endFlag, String str1, String str2, int integer) {
		super(op, endFlag);
		this.str1 = str1;
		this.str2 = str2;
		this.integer = integer;
	}
	
}
