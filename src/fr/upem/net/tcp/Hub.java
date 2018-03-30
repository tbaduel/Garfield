package fr.upem.net.tcp;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class Hub {
	private final HashMap<Opcode, K > map  = new HashMap<>();
	static private int BUFFER_SIZE = 1_024;
	
	public static void execute(Message msg, ServerMatou server ) {
	
		Opcode opcode = Opcode.valueOfId(msg.getOp());
	}
	
	private ByteBuffer signup(Message msg, ServerMatou server) {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		System.out.println("Signin...");
		String name = msg.getBp().getField("username");
		if (server.map.containsValue(name)) { 					// Username already used
			bb.putInt(Opcode.SIGNUP_ERR.op);
		} else { 												// Username not used
			bb.putInt(Opcode.SIGNUP_OK.op);
			System.out.println("ADDED: " + Opcode.SIGNUP_OK);
			server.map.put(sc.getRemoteAddress(), name);
			server.userMap.put(name, bp.getField("password"));
			queueMessage(bb);
			bb = null;
		}
		break;
	
	}
	
}
