package fr.upem.net.tcp;

import java.io.IOException;

public class GFPException extends IOException {

    private static final long serialVersionUID = -1810727803680020453L;

    public GFPException() {
        super();
    }

    public GFPException(String s) {
        super(s);
    }

    public static void ensure(boolean b, String string) throws GFPException {
        if (!b)
            throw new GFPException(string);

    }
}