package fr.upem.net.reader;

import java.nio.ByteBuffer;

public class IntReader implements Reader {

	private enum State {
		DONE, WAITING, ERROR
	};

	private final ByteBuffer bb;
	private State state = State.WAITING;
	private int value;

	public IntReader(ByteBuffer bb) {
		this.bb = bb;
	}

	@Override
	public ProcessStatus process() {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		try {
            if (bb.remaining() >= Integer.BYTES) {
                value = bb.getInt();
                state = State.DONE;
                //System.out.println("\t\tInt = " + value);
                return ProcessStatus.DONE;
            } else {
                return ProcessStatus.REFILL;
            }
        } catch (IllegalStateException | IllegalArgumentException ie) {
        	return ProcessStatus.ERROR;
        }

	}

	@Override
	public Object get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return value;
	}

	@Override
	public void reset() {
		state = State.WAITING;
	}
}
