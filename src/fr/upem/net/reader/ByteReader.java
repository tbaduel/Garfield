package fr.upem.net.reader;

import java.nio.ByteBuffer;

public class ByteReader implements Reader {
	private enum State {
		DONE, WAITING, ERROR
	};

	private final ByteBuffer bb;
	private State state = State.WAITING;
	private byte value;

	public ByteReader(ByteBuffer bb) {
		this.bb = bb;
	}

	@Override
	public ProcessStatus process() {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		try {
			if (bb.remaining() >= Byte.BYTES) {
				value = bb.get();
				state = State.DONE;
				return ProcessStatus.DONE;
			} else {
				return ProcessStatus.REFILL;
			}
		} catch (IllegalArgumentException | IllegalStateException ie) {
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
