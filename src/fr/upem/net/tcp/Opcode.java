package fr.upem.net.tcp;

public enum Opcode {
	ERROR(-1), LOGIN(0), SIGNUP(1), MESSAGE(2), REQUEST(3), FILE(4), IPRESPONSE(98), MESSAGEBROADCAST(99), WHISP_OK(
			103), LOGIN_OK(103), LOGIN_ERR(104), SIGNUP_OK(105), SIGNUP_ERR(106)

	;

	public final int op;

	Opcode(int op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return Integer.toString(op);
	}
}
