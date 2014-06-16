package com.ftpandroid.net.ftp;

final public class DirectoryEmptyStrings extends ServerStrings {

    /**
     * Server string indicating no files found (NO_FILES)
     */
    final public static String NO_FILES = "NO FILES";
        
    /**
     * Server string indicating no files found (wu-ftpd) (NO_SUCH_FILE_OR_DIR)
     */
    final public static String NO_SUCH_FILE_OR_DIR = "NO SUCH FILE OR DIRECTORY";
    
    /**  
     * Server string indicating no files found (EMPTY_DIR)
     */
    final public static String EMPTY_DIR = "EMPTY";
    
    /**
     * Server string for OS/390 indicating no files found (NO_DATA_SETS_FOUND)
     */
    final public static String NO_DATA_SETS_FOUND = "NO DATA SETS FOUND";
    
    /**
     * Constructor. Adds the fragments to match on
     */
    public DirectoryEmptyStrings() {
        add(NO_FILES);
        add(NO_SUCH_FILE_OR_DIR);
        add(EMPTY_DIR);
        add(NO_DATA_SETS_FOUND);
    }

}
