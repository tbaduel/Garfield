package fr.upem.net.reader;

import java.io.IOException;
import java.nio.ByteBuffer;

import fr.upem.net.message.Message;
import fr.upem.net.message.MessageFile;
import fr.upem.net.message.MessageIp;
import fr.upem.net.message.MessageOneString;
import fr.upem.net.message.MessageOpcode;
import fr.upem.net.message.MessageStringToken;
import fr.upem.net.message.MessageTwoString;
import fr.upem.net.message.MessageTwoStringOneInt;
import fr.upem.net.other.Opcode;
import fr.upem.net.parser.BodyParser;

public class MessageReader implements Reader {

	private enum State {
		DONE, WAITING_OP, WAITING_HEADER_SIZE, WAITING_END_FLAG, WAITING_BODY, WAITING_FILEID, ERROR
	};

	private final ByteBuffer bb;
	private State state = State.WAITING_OP;
	private int op;
	private int headerSize;
	private byte endFlag;
	private String body;
	private ByteBuffer bodyBuffer = null;
	private int fileId = -1;

	public MessageReader(ByteBuffer bbin) {
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
		ByteFileReader bfRead = new ByteFileReader(bb);
		try {
			if (state == State.WAITING_OP) {
				ps = iRead.process();
				if (ps == ProcessStatus.DONE) {
					op = (Integer) iRead.get();
					iRead.reset();
					state = State.WAITING_HEADER_SIZE;
				}
			}
			if (state == State.WAITING_HEADER_SIZE) {
				ps = iRead.process();
				if (ps == ProcessStatus.DONE) {
					headerSize = (Integer) iRead.get();
					iRead.reset();
					state = State.WAITING_END_FLAG;
				}
			}
			if (state == State.WAITING_END_FLAG) {
				ps = bRead.process();
				if (ps == ProcessStatus.DONE) {
					endFlag = (byte) bRead.get();
					bRead.reset();
					if (op == Opcode.FILE_SEND.op && headerSize == 9) {
						state = State.WAITING_FILEID;
					} else {
						state = State.WAITING_BODY;
					}
				}
			}
			if (state == State.WAITING_FILEID) {
				ps = iRead.process();
				if (ps == ProcessStatus.DONE) {
					fileId = (Integer) iRead.get();
					iRead.reset();
					state = State.WAITING_BODY;
				}
			}
			if (state == State.WAITING_BODY) {
				if (op == Opcode.FILE_SEND.op) {
					ps = bfRead.process();
					if (ps == ProcessStatus.DONE) {
						bodyBuffer = (ByteBuffer) bfRead.get();
						bfRead.reset();
						state = State.DONE;
						return ProcessStatus.DONE;
					}
				} else {
					ps = strRead.process();
					if (ps == ProcessStatus.DONE) {
						body = (String) strRead.get();
						strRead.reset();
						state = State.DONE;
						return ProcessStatus.DONE;
					}
				}
			}
		} catch (IOException e) {

		}
		if (state == State.ERROR) {
			System.err.println("ERROR PROCESS");
			return ProcessStatus.ERROR;
		}
		return ProcessStatus.REFILL;
	}

	/**
	 * Get the Message Be careful ! This method contain typo issues if you don't
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
		BodyParser bp = null;
		// int fileId;
		if (state != State.DONE)
			throw new IllegalStateException();
		Opcode opcode = Opcode.valueOfId(op);
		if (opcode != Opcode.FILE_SEND)
			bp = BodyParser.readBody(body);

		switch (opcode) {
		case LOGIN:
			username = bp.getField("username");
			password = bp.getField("password");
			if (username == null || password == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageTwoString(op, endFlag, username, password);
		case SIGNUP:
			username = bp.getField("username");
			password = bp.getField("password");
			if (username == null || password == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageTwoString(op, endFlag, username, password);

		case MESSAGE:
			data = bp.getField("data");
			if (data == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageOneString(op, endFlag, data);

		case WHISP:
			username = bp.getField("username");
			data = bp.getField("data");
			if (username == null || data == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageTwoString(op, endFlag, username, data);

		case MESSAGEBROADCAST:
			username = bp.getField("username");
			data = bp.getField("data");
			if (username == null || data == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageTwoString(op, endFlag, username, data);

		case REQUEST:
			userReq = bp.getField("userReq");
			if (userReq == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageOneString(op, endFlag, userReq);

		case FILE_REQUEST:
			try {
				username = bp.getField("username");
				data = bp.getField("file");
				fileId = Integer.parseInt(bp.getField("fileId"));
				if (username == null || data == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageTwoStringOneInt(op, endFlag, username, data, fileId);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case FILE_OK:
			try {
				username = bp.getField("username");
				data = bp.getField("file");
				fileId = Integer.parseInt(bp.getField("fileId"));
				if (username == null || data == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageTwoStringOneInt(op, endFlag, username, data, fileId);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case IPRESPONSE:
			try {
				username = bp.getField("username");
				userReq = bp.getField("userReq");
				ip = bp.getField("ip");
				port = Integer.parseInt(bp.getField("port"));
				token = Integer.parseInt(bp.getField("token"));
				if (username == null || userReq == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageIp(op, endFlag, ip, port, username, userReq, token);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case WHISP_OK:
			try {
				username = bp.getField("username");
				userReq = bp.getField("userReq");
				ip = bp.getField("ip");
				port = Integer.parseInt(bp.getField("port"));
				token = Integer.parseInt(bp.getField("token"));
				if (username == null || userReq == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageIp(op, endFlag, ip, port, username, userReq, token);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case WHISP_REQUEST:
			username = bp.getField("username");
			if (username == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageOneString(op, endFlag, username);

		case WHISP_ERR:
			userReq = bp.getField("userReq");
			if (userReq == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageOneString(op, endFlag, userReq);

		case WHISP_REFUSED:
			username = bp.getField("username");
			userReq = bp.getField("userReq");
			if (username == null || userReq == null) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}
			return new MessageTwoString(op, endFlag, username, userReq);

		case CHECK_PRIVATE:
			try {
				token = Integer.parseInt(bp.getField("token"));
				username = bp.getField("username");
				if (username == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageStringToken(op, endFlag, username, token);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case FILE_SEND:
			return new MessageFile(op, endFlag, fileId, bodyBuffer);

		case CHECK_FILE:
			try {
				token = Integer.parseInt(bp.getField("token"));
				username = bp.getField("username");
				if (username == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageStringToken(op, endFlag, username, token);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		case FILE_REFUSED:
			try {
				username = bp.getField("username");
				data = bp.getField("file");
				fileId = Integer.parseInt(bp.getField("fileId"));
				if (username == null || data == null) {
					return new MessageOpcode(Opcode.ERROR.op, endFlag);
				}
				return new MessageTwoStringOneInt(op, endFlag, username, data, fileId);
			} catch (NumberFormatException e) {
				return new MessageOpcode(Opcode.ERROR.op, endFlag);
			}

		default:
			return new MessageOpcode(op, endFlag);
		}
	}

	@Override
	public void reset() {

		state = State.WAITING_OP;
	}

}
