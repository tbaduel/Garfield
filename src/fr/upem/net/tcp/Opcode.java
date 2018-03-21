package fr.upem.net.tcp;

public enum Opcode {
	ERROR(-1), LOGIN(0), SIGNUP(1), MESSAGE(2), REQUEST(3), FILE(4), WHISP_OK(103), LOGIN_OK(104);

	public final int op;

	Opcode(int op) {
		this.op = op;
	}
	
	@Override
	public String toString() {
		return Integer.toString(op);
	}
}
