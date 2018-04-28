package fr.upem.net.reader;

import java.io.IOException;
import java.nio.ByteBuffer;


public class ByteFileReader implements Reader {

	private enum State {
		DONE, WAITING_SIZE, WAITING_BYTES, ERROR
	};

	private ByteBuffer bbin;

	private State state = State.WAITING_SIZE;
	private int size = 0;
	private ByteBuffer buffer;

	public ByteFileReader(ByteBuffer bbin) {
		this.bbin = bbin;
	}

	@Override
	public ProcessStatus process() {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		ProcessStatus ps;
		IntReader iRead = new IntReader(bbin);
		try {
			if (state == State.WAITING_SIZE) {
				ps = iRead.process();
				if (ps == ProcessStatus.DONE) {
					size = (Integer) iRead.get();
					iRead.reset();
					state = State.WAITING_BYTES;

				}
			}
			if (state == State.WAITING_BYTES) {

				if (bbin.remaining() >= size) {
					buffer = readBytes(size);
					buffer.flip();
					state = State.DONE;
					return ProcessStatus.DONE;
				}
			} else
				return ProcessStatus.REFILL;
		} catch (IllegalStateException | IllegalArgumentException | IOException ie) {
			return ProcessStatus.ERROR;
		}
		return ProcessStatus.REFILL;
	}

	public ByteBuffer readBytes(int size) throws IOException {
		ByteBuffer tmp = ByteBuffer.allocate(size);
		if (bbin.hasRemaining()) {
			int pos = bbin.position() + size;
			int limit = bbin.limit();
			bbin.limit(pos);
			tmp.put(bbin);

			bbin.limit(limit);

		}
		return tmp;
	}

	@Override
	public Object get() throws IOException {
		return buffer;
	}

	@Override
	public void reset() {
		state = State.WAITING_BYTES;

	}

}
