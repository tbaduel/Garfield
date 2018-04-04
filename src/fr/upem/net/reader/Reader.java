package fr.upem.net.reader;

import java.io.IOException;

public interface Reader {

    public static enum ProcessStatus {DONE,REFILL,ERROR};

    public ProcessStatus process();

    public Object get() throws IOException ;

    public void reset();

}
