package fr.upem.net.reader;

import java.io.IOException;
import java.nio.ByteBuffer;

import fr.upem.net.message.Message;
import fr.upem.net.message.MessageIp;
import fr.upem.net.message.MessageOneString;
import fr.upem.net.message.MessageOpcode;
import fr.upem.net.message.MessageStringToken;
import fr.upem.net.message.MessageTwoString;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.BodyParser;



public class MessageReader implements Reader {
	
	private enum State {
		DONE, WAITING_OP, WAITING_HEADER_SIZE, WAITING_END_FLAG ,WAITING_BODY, ERROR
	};
	
	private final ByteBuffer bb;
	private State state = State.WAITING_OP;
	private int op;
	private int headerSize;
	private byte endFlag;
	private String body;
	

	
	public MessageReader(ByteBuffer bbin) {
		// TODO Auto-generated constructor stub
		bb = bbin;
	}

	@Override
	public ProcessStatus process() {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		ProcessStatus ps;
		IntReader iRead = new IntReader(bb);
		ByteReader bRead = new ByteReader(bb);
		StringReader strRead = new StringReader(bb);
		if (state == State.WAITING_OP) {
			ps = iRead.process();
			if (ps == ProcessStatus.DONE) {
				op = (Integer) iRead.get();
				iRead.reset();
				state = State.WAITING_HEADER_SIZE;
				//System.out.println("OP READ");
			}
		}
		//System.out.println("Remaining : "+ bb.remaining());
		if (state == State.WAITING_HEADER_SIZE) {
			ps = iRead.process();
			if (ps == ProcessStatus.DONE) {
				headerSize = (Integer) iRead.get();
				iRead.reset();
				state = State.WAITING_END_FLAG;
				//System.out.println("HEADER_SIZE READ");
			}
		}
		//System.out.println("Remaining : "+ bb.remaining());
		if (state == State.WAITING_END_FLAG) {
			ps = bRead.process();
			if (ps == ProcessStatus.DONE) {
				endFlag = (byte) bRead.get();
				bRead.reset();
				state = State.WAITING_BODY;
			}
		}
		//System.out.println("Remaining : "+ bb.remaining());
		if (state == State.WAITING_BODY) {
			ps = strRead.process();
			if (ps == ProcessStatus.DONE) {
				body = (String) strRead.get();
				strRead.reset();
				state = State.DONE;
				//System.out.println("STR READ");
				return ProcessStatus.DONE;
			}
		}
		if (state == State.ERROR) {
			System.err.println("ERROR PROCESS");
			return ProcessStatus.ERROR;
		}
		//System.out.println("~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~");
		//bb.compact();
		return ProcessStatus.REFILL;
	}
	
	
	/**
	 * Be careful ! This method contain typo issues if you don't
	 * respect the Garfield Protocol
	 */
	@Override
	public Message get() throws IOException {
		String username;
		String userReq;
		String password;
		String data;
		int port;
		int token;
		String ip;
		if (state != State.DONE)
			throw new IllegalStateException();
		BodyParser bp = BodyParser.readBody(body);
		Opcode opcode = Opcode.valueOfId(op);
		switch(opcode) {
		case LOGIN:
			username = bp.getField("username");
			password = bp.getField("password");
			return new MessageTwoString(op,endFlag, username, password);
		case SIGNUP:
			username = bp.getField("username");
			password = bp.getField("password");
			return new MessageTwoString(op,endFlag, username, password);
			
			
		case MESSAGE:
			data = bp.getField("data");
			return new MessageOneString(op,endFlag, data);
		
		case WHISP:
			username = bp.getField("username");
			data = bp.getField("data");
			return new MessageTwoString(op,endFlag, username,data);
			
		case MESSAGEBROADCAST:
			username = bp.getField("username");
			data = bp.getField("data");
			return new MessageTwoString(op, endFlag, username, data);
		
		case REQUEST:
			userReq = bp.getField("userReq");
			return new MessageOneString(op,endFlag, userReq);
			
			
		case FILE :
			break;
			
		case IPRESPONSE:
			username = bp.getField("username");
			userReq = bp.getField("userReq");
			ip = bp.getField("ip");
			port = Integer.parseInt(bp.getField("port"));
			token = Integer.parseInt(bp.getField("token"));
			return new MessageIp(op,endFlag,ip,port, username, userReq,token);
			
			
		case WHISP_OK:
			username = bp.getField("username");
			userReq = bp.getField("userReq");
			ip = bp.getField("ip");
			port = Integer.parseInt(bp.getField("port"));
			token = Integer.parseInt(bp.getField("token"));
			return new MessageIp(op,endFlag,ip,port, username, userReq,token);
			
		case WHISP_REQUEST:
			username = bp.getField("username");
			return new MessageOneString(op, endFlag, username);
			
		case WHISP_ERR:
			userReq = bp.getField("userReq");
			return new MessageOneString(op,endFlag, userReq);
						
		case WHISP_REFUSED:
			userReq = bp.getField("userReq");
			return new MessageOneString(op,endFlag, userReq);
			
		case CHECK_PRIVATE:
			token = Integer.parseInt(bp.getField("token"));
			username = bp.getField("username");
			return new MessageStringToken(op, endFlag,username,token);

		default:
			return new MessageOpcode(op, endFlag);
		}
		return null; // new Message(body, headerSize,op,  endFlag);
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_OP;
	}
	
	
	
}
