package fr.upem.net.tcp;

import java.nio.ByteBuffer;


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
				state = State.WAITING_BODY;
			}
		}
		
		if (state == State.WAITING_BODY) {
			ps = strRead.process();
			if (ps == ProcessStatus.DONE) {
				body = (String) strRead.get();
				strRead.reset();
				state = State.DONE;
				return ProcessStatus.DONE;
			}
		}
		return ProcessStatus.REFILL;
	}
	

	@Override
	public Message get() {
		// TODO Auto-generated method stub
		
		if (state != State.DONE)
			throw new IllegalStateException();
		return new Message(body, op, headerSize, endFlag);
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_OP;
	}
	
	
	
}
