package com.ftpandroid.net.ftp;


import java.io.IOException;

import com.ftpandroid.connx.debug.Logger;

/**
 *  Represents an input stream of bytes coming from an FTP server, permitting
 *  the user to download a file by reading the stream. It can only be used
 *  for one download, i.e. after the stream is closed it cannot be reopened.
 *
 *  @author      Eric
 */
class FTPBinaryInputStream extends AbstractFTPInputStream {
    
    private static Logger log = Logger.getLogger("FTPBinaryInputStream");
    
    private static int SKIP_THRESHOLD = 64*1024;
    
    private long mark = -1;
    
    private int skipThreshold = SKIP_THRESHOLD;
    
    /**
     * Constructor. A connected FTPClient instance must be supplied. This sets up the
     * download 
     * 
     * @param client            connected FTPClient instance
     * @param remoteFile        remote file
     * @throws IOException
     * @throws FTPException
     */
    public FTPBinaryInputStream(FTPClient client, String remoteFile) throws IOException, FTPException {
        this(client, remoteFile, 0);            
    }
    
    /**
     * Constructor. A connected FTPClient instance must be supplied. This sets up the
     * download. If an offset > 0 is supplied, must be a binary transfer.
     * 
     * @param client            connected FTPClient instance
     * @param remoteFile        remote file
     * @param offset            offset to resume downloading from.
     * @throws IOException
     * @throws FTPException
     */
    public FTPBinaryInputStream(FTPClient client, String remoteFile, long offset) throws IOException, FTPException {
        super(client, remoteFile);
        
        pos = offset;
    }
    

    /**
     * Reads the next byte of data from the input stream. The value byte is 
     * returned as an int in the range 0 to 255. If no byte is available because 
     * the end of the stream has been reached, the value -1 is returned. 
     * This method blocks until input data is available, the end of the stream 
     * is detected, or an exception is thrown. 
     */
    public int read() throws IOException {
        if (!started) {
            start();
        }
        byte[] b = new byte[1];
        if (client.readChunk(in, b, 0, b.length) < 0)
            return -1;
        monitorCount++;
        pos++;
        checkMonitor();
        return 0xFF & b[0];
    }
    
    /**
     * Reads up to len bytes of data from the input stream into an array of bytes. 
     * An attempt is made to read as many as len bytes, but a smaller number may 
     * be read, possibly zero. The number of bytes actually read is returned as an integer. 
     * This method blocks until input data is available, end of file is detected, 
     * or an exception is thrown. 
     *
     * @param b    array to read into
     * @param off  offset into the array to start at
     * @param len  the number of bytes to be read
     * 
     * @return  the number of bytes read, or -1 if the end of the stream has been reached.
     */
    public int read(byte b[], int off, int len) throws IOException {
        if (!started) {
            start();
        }
        int count = client.readChunk(in, b, off, len);      
        monitorCount += count;
        pos += count;
        checkMonitor();        
        return count;
    }
    
    /**
     * Skips over and discards <code>n</code> bytes of data from this input
     * stream. The <code>skip</code> method may, for a variety of reasons, end
     * up skipping over some smaller number of bytes, possibly <code>0</code>.
     * This may result from any of a number of conditions; reaching end of file
     * before <code>n</code> bytes have been skipped is only one possibility.
     * The actual number of bytes skipped is returned.  If <code>n</code> is
     * negative, no bytes are skipped.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if the stream does not support seek,
     *              or if some other I/O error occurs.
     */
    public long skip(long n) throws IOException {
        // skip just by reading if not too much to read
        log.debug("Skipping " + n + " bytes");

        // otherwise restart the transfer
        if (started)
            stop();
        long bytes = n;
        long len = 0;
        pos += n;
        try {
            len = client.size(getRemoteFile());
            if (pos > len) {
                bytes = len-pos;
                pos = len;
            }
        }
        catch (FTPException ex) {
            throw new IOException(ex.getMessage());
        }
        return bytes;
    }
    
    /**
     * Marks the current position in this input stream. A subsequent call to
     * the <code>reset</code> method repositions this stream at the last marked
     * position so that subsequent reads re-read the same bytes.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before
     *                      the mark position becomes invalid.
     * @see     java.io.InputStream#reset()
     */
    public synchronized void mark(int readlimit) {
        mark = pos;
        log.debug("Mark set at " + pos + " bytes in file");
    }

    /**
     * Repositions this stream to the position at the time the
     * <code>mark</code> method was last called on this input stream.
     *
     * @exception  IOException  if this stream has not been marked or if the
     *               mark has been invalidated.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.IOException
     */
    public synchronized void reset() throws IOException {
        log.debug("Reset called - resetting to " + mark + " bytes");
        if (mark < 0) {
            throw new IOException("mark not called before reset()");
        }
        if (started)
            stop();
        pos = mark;
        mark = -1;
    }

    /**
     * Tests if this input stream supports the <code>mark</code> and
     * <code>reset</code> methods. Whether or not <code>mark</code> and
     * <code>reset</code> are supported is an invariant property of a
     * particular input stream instance. The <code>markSupported</code> method
     * of <code>InputStream</code> returns <code>false</code>.
     *
     * @return  <code>true</code> if this stream instance supports the mark
     *          and reset methods; <code>false</code> otherwise.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    public boolean markSupported() {
        return true;
    }
    
    /**
     * Stop the transfer
     * 
     * @throws IOException
     */
    private void stop() throws IOException {

        // close streams
        client.closeDataSocket(in);
            
        // read the server reply and discard
        try {
            client.readReply();
        }
        catch (FTPException ex) {
            throw new IOException(ex.getMessage());
        }
        started = false;
        closed = true;
    }
    
}
