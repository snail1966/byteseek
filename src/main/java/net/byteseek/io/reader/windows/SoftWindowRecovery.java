package net.byteseek.io.reader.windows;

import java.io.IOException;

/**
 * Created by matt on 02/10/15.
 */
public interface SoftWindowRecovery {

    byte[] reloadWindow(Window window) throws IOException;

}