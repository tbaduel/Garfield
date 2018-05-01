package fr.upem.net.other;

public enum Opcode {
	ERROR(-1), 
	LOGIN(0), 
	SIGNUP(1), 
	MESSAGE(2), 
	REQUEST(3), 
	WHISP(7), 
	FILE_REQUEST(4), 
	FILE_SEND(6), 
	FILE_OK(5), 
	IPRESPONSE(98), 
	MESSAGEBROADCAST(99), 
	LOGIN_OK(103), 
	LOGIN_ERR(104), 
	SIGNUP_OK(105), 
	SIGNUP_ERR(106), 
	WHISP_OK(107), 
	WHISP_ERR(108), 
	WHISP_REFUSED(109), 
	WHISP_REQUEST(110), 
	BAD_REQUEST(115),
	CHECK_PRIVATE(111), 
	CHECK_FILE(112)		

	;

	public final int op;

	Opcode(int op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return Integer.toString(op);
	}
	
	/**
	 * Get the Opcode corresponding to a id
	 * @param id
	 * @return Opcode corresponding to the id
	 */
	public static Opcode valueOfId(int id) {
	    for (Opcode opcode : values()) {
	         int valueId = opcode.op;
	         if (id == valueId) {
	              return opcode;
	         }
	    }
		return Opcode.ERROR;
	}
}
