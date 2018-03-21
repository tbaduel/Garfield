package fr.upem.net.tcp;

public enum Opcode {
	LOGIN(0), SIGNUP(1), MESSAGE(2), REQUEST(3), FILE(4);

	public final int op;

	Opcode(int op) {
		this.op = op;
	}
}
