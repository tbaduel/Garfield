package fr.upem.net.tcp;

import java.io.IOException;
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
		//System.out.println("~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~");
		IntReader iRead = new IntReader(bb);
		ByteReader bRead = new ByteReader(bb);
		StringReader strRead = new StringReader(bb);
		//System.out.println("Remaining : "+ bb.remaining());
		if (state == State.WAITING_OP) {
			ps = iRead.process();
			//System.out.println("OP process");
			//System.out.println(ps);
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
				System.out.println("FLAG READ");
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
		//System.out.println("~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~");
		//bb.compact();
		return ProcessStatus.REFILL;
	}
	

	@Override
	public Message get() throws IOException {
		// TODO Auto-generated method stub
		
		if (state != State.DONE)
			throw new IllegalStateException();
		return new Message(body, headerSize,op,  endFlag);
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_OP;
	}
	
	
	
}
