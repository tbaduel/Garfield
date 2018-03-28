package fr.upem.net.tcp;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;


public class MessageReader implements Reader {
	
	private enum State {
		DONE, WAITING_NAME_SIZE, WAITING_NAME, WAITING_MSG_SIZE ,WAITING_MSG, ERROR
	};
	
	private static final Charset UTF8 = Charset.forName("utf-8");
	private final ByteBuffer bb;
	private State state = State.WAITING_NAME;
	private int nameSize;
	private int msgSize;
	private String name;
	private String msg;
	
	

	
	public MessageReader(ByteBuffer bbin) {
		// TODO Auto-generated constructor stub
		bb = bbin;
	}

	@Override
	public ProcessStatus process() {
		// TODO Auto-generated method stub
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		ProcessStatus ps;

		StringReader strRead = new StringReader(bb);

		if (state == State.WAITING_NAME) {
			ps = strRead.process();
			if (ps == ProcessStatus.DONE) {
				name = (String) strRead.get();
				strRead.reset();
				state = State.WAITING_MSG;
			}
		}

		if (state == State.WAITING_MSG) {
			ps = strRead.process();
			if (ps == ProcessStatus.DONE) {
				msg = (String) strRead.get();
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
		//return new Message(nameSize, name, msgSize, msg);
		return null;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_NAME;
	}
	
	
	
}
