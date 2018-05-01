package fr.upem.net.reader;

import java.io.IOException;

public interface Reader {

    public static enum ProcessStatus {DONE,REFILL,ERROR};
    
    /**
     * Process the reading
     * @return the ProcessStatus (DONE, REFILL or ERROR)
     */
    public ProcessStatus process();

    /**
     * Get the Object read on the Message
     * @return the Object in the Message
     * @throws IOException
     */
    public Object get() throws IOException ;
    
    /**
     * Reset the reader. You always need to reset the reader
     * if you want to read a new message
     */
    public void reset();

}
