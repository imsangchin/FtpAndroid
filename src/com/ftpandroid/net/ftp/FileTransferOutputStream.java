package com.ftpandroid.net.ftp;

import java.io.OutputStream;

/**
 *  Super class of all output streams supported
 *
 *  @author      Eric
 */
abstract public class FileTransferOutputStream extends OutputStream {

    /** 
     * Name of remote file being transferred
     */
    protected String remoteFile;
    
    /**
     * Number of bytes transferred
     */
    protected long size = 0;
    
    /**
     * Has the stream been closed?
     */
    protected boolean closed = false;
    
    /**
     * Get the name of the remote file (including one that may
     * have been generated by the server).
     * 
     * @return remote filename
     */
    public String getRemoteFile() {
        return remoteFile;
    }
    
    /**
     * Get the number of bytes transferred
     * 
     * @return long
     */
    public long getBytesTransferred() {
        return size;
    }
}