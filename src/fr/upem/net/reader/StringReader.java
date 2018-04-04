package fr.upem.net.reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class StringReader implements Reader {

	private enum State {
		DONE, WAITING_SIZE, WAITING_STRING, ERROR
	};

	private static final Charset UTF8 = Charset.forName("utf-8");
	private final ByteBuffer bb;
	private State state = State.WAITING_SIZE;
	private int size;
	private String value;

	public StringReader(ByteBuffer bb) {
		this.bb = bb;
	}

	@Override
	public ProcessStatus process() {
		// TODO Auto-generated method stub

		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		//bb.flip();
		try {
			if (state == State.WAITING_SIZE) {
				if (bb.remaining() >= Integer.BYTES) {
					size = bb.getInt();
					state = State.WAITING_STRING;
					//System.out.println("\tSize = " + size);
				}
			}

			if (state == State.WAITING_STRING) {
				if (bb.remaining() >= size) {
					ByteBuffer tmp = readBytes(size);
					tmp.flip();
					value = UTF8.decode(tmp).toString();
					//System.out.println("\t\tstr = " + value);
					state = State.DONE;
					return ProcessStatus.DONE;
				}
			} else
				return ProcessStatus.REFILL;
			//System.out.println("after REfill");

		} catch (IOException e) {
			// Error case

		} finally {
			//System.out.println("Sors du String Reader");
			//bb.compact();
		}
		return ProcessStatus.REFILL;

	}

	@Override
	public Object get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return value;
	}

	public ByteBuffer readBytes(int size) throws IOException {
		ByteBuffer tmp = ByteBuffer.allocate(size);
		if (bb.hasRemaining()) {
			int pos = bb.position() + size;
			int limit = bb.limit();
			bb.limit(pos);
			tmp.put(bb);

			bb.limit(limit);

		}
		return tmp;
	}

	@Override
	public void reset() {
		state = State.WAITING_SIZE;
	}

}
