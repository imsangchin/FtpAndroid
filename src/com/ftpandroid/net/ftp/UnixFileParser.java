package com.ftpandroid.net.ftp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.ftpandroid.connx.StringUtils;
import com.ftpandroid.connx.debug.Logger;

/**
 *  Represents a remote Unix file parser
 */
public class UnixFileParser extends FTPFileParser {

    
	/**
     * Logging object
     */
    private static Logger log = Logger.getLogger("UnixFileParser");

    /**
     * Symbolic link symbol
     */
    private final static String SYMLINK_ARROW = "->";
    
    /**
     * Indicates symbolic link
     */
    private final static char SYMLINK_CHAR = 'l';
        
    /**
     * These chars indicates ordinary files
     */
    private final static char[] FILE_CHARS = {'-', 'p'};
    
    /**
     * Indicates directory
     */
    private final static char DIRECTORY_CHAR = 'd';
    
    /**
     * Date formatter 1 with no HH:mm
     */
    private SimpleDateFormat noHHmmFormatter1;
    
    /**
     * Date formatter 2  with no HH:mm
     */
    private SimpleDateFormat noHHmmFormatter2;
    
    /**
     * Date formatter with no HH:mm
     */
    private SimpleDateFormat noHHmmFormatter;    
    
    
    /**
     * Date formatter with HH:mm
     */
    private SimpleDateFormat hhmmFormatter;
    
    /**
     * List of formatters
     */
    private List hhmmFormatters;
        
    /**
     * Minimum number of expected fields
     */
    private final static int MIN_FIELD_COUNT = 7;
    
    /**
     * Constructor
     */
    public UnixFileParser() {
        setLocale(Locale.getDefault());
    }
    
    /**
     * Set the locale for date parsing of listings
     * 
     * @param locale    locale to set
     */
    public void setLocale(Locale locale) {
        noHHmmFormatter1 = new SimpleDateFormat("MMM-dd-yyyy", locale);
        noHHmmFormatter2 = new SimpleDateFormat("dd-MMM-yyyy", locale);
        noHHmmFormatter = noHHmmFormatter1;
        
        hhmmFormatters = new ArrayList();
        hhmmFormatters.add(new SimpleDateFormat("MMM-d-yyyy-HH:mm", locale));
        hhmmFormatters.add(new SimpleDateFormat("MMM-dd-yyyy-HH:mm", locale));
        hhmmFormatters.add(new SimpleDateFormat("MMM-d-yyyy-H:mm", locale));
        hhmmFormatters.add(new SimpleDateFormat("MMM-dd-yyyy-H:mm", locale));
        hhmmFormatters.add(new SimpleDateFormat("MMM-dd-yyyy-H.mm", locale));
        hhmmFormatters.add(new SimpleDateFormat("dd-MMM-yyyy-HH:mm", locale));
    }  
    
    public String toString() {
        return "UNIX";
    }
    
    
    /**
     * Valid format for this parser
     * 
     * @param listing
     * @return true if valid
     */
    public boolean isValidFormat(String[] listing) {
        int count = Math.min(listing.length, 10);
        
        boolean perms1 = false;
        boolean perms2 = false;

        for (int i = 0; i < count; i++) {
            if (listing[i].trim().length() == 0)
                continue;
            String[] fields = split(listing[i]);
            if (fields.length < MIN_FIELD_COUNT)
                continue;
            // check perms
            char ch00 = fields[0].charAt(0);
            if (ch00 == '-' || ch00 == 'l' || ch00 == 'd')
                perms1 = true;
            
            if (fields[0].length() > 1) {
                char ch01 = fields[0].charAt(1);
                if (ch01 == 'r' || ch01 == '-')
                    perms2 = true;   
            }
            
             // last chance - Connect:Enterprise has -ART------TCP
            if (!perms2 && fields[0].length() > 2 && fields[0].indexOf('-', 2) > 0)
                perms2 = true;
        }
        if (perms1 && perms2)
            return true;
        log.debug("Not in UNIX format");
        return false;
    }
    
    /**
     * Is this a Unix format listing?
     * 
     * @param raw   raw listing line
     * @return true if Unix, false otherwise
     */
    public static boolean isUnix(String raw) {
        char ch = raw.charAt(0);
        if (ch == DIRECTORY_CHAR || ch == SYMLINK_CHAR)
            return true;
        for (int i = 0; i < FILE_CHARS.length; i++)
        	if (ch == FILE_CHARS[i])
        		return true;
        return false;
    }
    
    private boolean isNumeric(String field)
    {
        for (int i = 0; i < field.length(); i++)
        {
            if (!Character.isDigit(field.charAt(i)))
                return false;
        }
        return true;
    }
    
    /**
     * Parse server supplied string, e.g.:
     * 
     * lrwxrwxrwx   1 wuftpd   wuftpd         14 Jul 22  2002 MIRRORS -> README-MIRRORS
     * -rw-r--r--   1 b173771  users         431 Mar 31 20:04 .htaccess
     * 
     * @param raw   raw string to parse
     */
    public FTPFile parse(String raw) throws ParseException {
        
        // test it is a valid line, e.g. "total 342522" is invalid
        if (!isUnix(raw))
            return null;
        
        String[] fields = split(raw);
         
        if (fields.length < MIN_FIELD_COUNT) {
            StringBuffer msg = new StringBuffer("Unexpected number of fields in listing '");
            msg.append(raw).append("' - expected minimum ").append(MIN_FIELD_COUNT). 
                    append(" fields but found ").append(fields.length).append(" fields");
            log.warn(msg.toString());
            return null;
        }
        
        // field pos
        int index = 0;
        
        // first field is perms
        char ch = raw.charAt(0);
        String permissions = fields[index++];
        ch = permissions.charAt(0);
        boolean isDir = false;
        boolean isLink = false;
        if (ch == DIRECTORY_CHAR)
            isDir = true;
        else if (ch == SYMLINK_CHAR)
            isLink = true;
        
        // some servers don't supply the link count
        int linkCount = 0;
        if (Character.isDigit(fields[index].charAt(0))) {
            try {
                linkCount = Integer.parseInt(fields[index++]);
            }
            catch (NumberFormatException ignore) {}
        }
        else if (fields[index].charAt(0) == '-') { // IPXOS Treck FTP server
            index++;
        }
        
        // owner and group
        String owner = "";
        String group = "";
        // if 2 fields ahead is numeric and there's enough fields beyond (4) for
        // the date, then the next two fields should be the owner & group
        if (isNumeric(fields[index+2]) &&  fields.length-(index+2) > 4) {
            owner = fields[index++];
            group = fields[index++];
        }
        // no owner
        else if (isNumeric(fields[index + 1]) && fields.length - (index + 1) > 4) {
            group = fields[index++];
        }
        
        // size
        long size = 0L;
        String sizeStr = StringUtils.replaceAll(fields[index++], ".", ""); // get rid of .'s in size    
        try {
            size = Long.parseLong(sizeStr);
        }
        catch (NumberFormatException ex) {
            log.warn("Failed to parse size: " + sizeStr);
        }
        
        // next 3 are the date time
        
        // we expect the month first on Unix. 
        // Connect:Enterprise UNIX has a weird extra numeric field here - we test if the 
        // next field is numeric and if so, we skip it (except we check for a BSD variant
        // that means it is the day of the month)
        int dayOfMonth = -1;
        if (isNumeric(fields[index])) {
            // this just might be the day of month - BSD variant
            // we check it is <= 31 AND that the next field starts
            // with a letter AND the next has a ':' within it
            try
            {
                String str = fields[index];
                while (str.startsWith("0"))
                    str = str.substring(1);
                dayOfMonth = Integer.parseInt(str);
                if (dayOfMonth > 31) // can't be day of month
                    dayOfMonth = -1;
                if (!(Character.isLetter(fields[index + 1].charAt(0))))
                    dayOfMonth = -1;
                if (fields[index + 2].indexOf(':') <= 0)
                    dayOfMonth = -1;
            }
            catch (NumberFormatException ex) {}
            index++;
        }
        
        int dateTimePos = index;
        Date lastModified = null;
        StringBuffer stamp = new StringBuffer(fields[index++]);
        stamp.append('-');
        if (dayOfMonth > 0)
            stamp.append(dayOfMonth);
        else
            stamp.append(fields[index++]);
        stamp.append('-');
        
        String field = fields[index++];
        if (field.indexOf(':') < 0 && field.indexOf('.') < 0) {
            stamp.append(field); // year
            try {
                lastModified = noHHmmFormatter.parse(stamp.toString());
            }
            catch (ParseException ignore) {
                noHHmmFormatter = (noHHmmFormatter == noHHmmFormatter1 ? noHHmmFormatter2 : noHHmmFormatter1);
                try {
                    lastModified = noHHmmFormatter.parse(stamp.toString());
                }
                catch (ParseException ex) {
                    if (!ignoreDateParseErrors)
                        throw new DateParseException(ex.getMessage());
                }
            }
        }
        else { // add the year ourselves as not present
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            stamp.append(year).append('-').append(field);
            lastModified = parseTimestamp(stamp.toString());
            
            // can't be in the future - must be the previous year
            // add 2 days just to allow for different time zones
            cal.add(Calendar.DATE, 2);
            if (lastModified != null && lastModified.after(cal.getTime())) {
                cal.setTime(lastModified);
                cal.add(Calendar.YEAR, -1);
                lastModified = cal.getTime();
            }
        }
            
        // name of file or dir. Extract symlink if possible
        String name = null;
        String linkedname = null;
        
        // we've got to find the starting point of the name. We
        // do this by finding the pos of all the date/time fields, then
        // the name - to ensure we don't get tricked up by a userid the
        // same as the filename,for example
        int pos = 0;
        boolean ok = true;
        int dateFieldCount = dayOfMonth > 0 ? 2 : 3; // only 2 fields left if we had a leading day of month
        for (int i = dateTimePos; i < dateTimePos + dateFieldCount; i++) {
            pos = raw.indexOf(fields[i], pos);
            if (pos < 0) {
                ok = false;
                break;
            }
            else { // move on the length of the field
                pos += fields[i].length();
            }
        }
        if (ok) {
            String remainder = trimStart(raw.substring(pos));
            if (!isLink) 
                name = remainder;
            else { // symlink, try to extract it
                pos = remainder.indexOf(SYMLINK_ARROW);
                if (pos <= 0) { // couldn't find symlink, give up & just assign as name
                    name = remainder;
                }
                else { 
                    int len = SYMLINK_ARROW.length();
                    name = remainder.substring(0, pos).trim();
                    if (pos+len < remainder.length())
                        linkedname = remainder.substring(pos+len);
                }
            }
        }
        else {
            log.warn("Failed to retrieve name: " + raw);  
        }
        
        FTPFile file = new FTPFile(raw, name, size, isDir, lastModified);
        file.setGroup(group);
        file.setOwner(owner);
        file.setLink(isLink);
        file.setLinkCount(linkCount);
        file.setLinkedName(linkedname);
        file.setPermissions(permissions);
        return file;
    }
    
    
    private Date parseTimestamp(String ts) throws DateParseException {
        if (hhmmFormatter != null) {
            try {
                return hhmmFormatter.parse(ts);
            } 
            catch (ParseException ex) {
                if (!ignoreDateParseErrors)
                    throw new DateParseException(ex.getMessage());
            }
            return null;
        }
        else {
            Iterator i = hhmmFormatters.iterator();
            ParseException ex = null;
            while (i.hasNext()) {
                try {
                    hhmmFormatter = (SimpleDateFormat)i.next();
                    return hhmmFormatter.parse(ts);
                } 
                catch (ParseException ignore) {
                    ex = ignore; // record last one
                }
            }
            if (!ignoreDateParseErrors)
                throw new DateParseException(ex.getMessage());
            hhmmFormatter = null; // none of them worked
            return null;
        }
    }
}
