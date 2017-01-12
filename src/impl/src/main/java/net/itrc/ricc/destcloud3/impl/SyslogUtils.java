package net.itrc.ricc.destcloud3.impl;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SyslogUtils {
    
    // CONSTANTS
    
    // PRIORITY
    public static final int LOG_EMERG   = 0;
    public static final int LOG_ALERT   = 1;
    public static final int LOG_CRIT    = 2;
    public static final int LOG_ERR     = 3;
    public static final int LOG_WARNING = 4;
    public static final int LOG_NOTICE  = 5;
    public static final int LOG_INFO    = 6;
    public static final int LOG_DEBUG   = 7;
    public static final int LOG_PRIMASK = 0x0007; // mask to extract priority

    // FACILITY
    public static final int LOG_KERN    = (0 << 3);
    public static final int LOG_USER    = (1 << 3);
    public static final int LOG_MAIL    = (2 << 3);
    public static final int LOG_DAEMON  = (3 << 3);
    public static final int LOG_AUTH    = (4 << 3);
    public static final int LOG_SYSLOG  = (5 << 3);
    public static final int LOG_LPR     = (6 << 3);
    public static final int LOG_NEWS    = (7 << 3);
    public static final int LOG_UUCP    = (8 << 3);
    public static final int LOG_CRON    = (9 << 3);
    public static final int LOG_AUTHPRIV = (10 << 3);
    public static final int LOG_FTP     = (11 << 3);
    public static final int LOG_NTP     = (12 << 3);
    public static final int LOG_SECURITY = (13 << 3);
    public static final int LOG_CONSOLE = (14 << 3);
    
    public static final int LOG_LOCAL0  = (16 << 3);
    public static final int LOG_LOCAL1  = (17 << 3);
    public static final int LOG_LOCAL2  = (18 << 3);
    public static final int LOG_LOCAL3  = (19 << 3);
    public static final int LOG_LOCAL4  = (20 << 3);
    public static final int LOG_LOCAL5  = (21 << 3);
    public static final int LOG_LOCAL6  = (22 << 3);
    public static final int LOG_LOCAL7  = (23 << 3);

    public static final int LOG_FACMASK = 0x03F8; 
  
    private static final int LOCAL_SYSLOG_PORT = 514;

    private String      identString;
    private int         opt;
    private int         facility;
    
    private DatagramSocket udpSock;
    
    public SyslogUtils( String ident, int logopt, int facility ) throws Exception {
        
        if (ident == null) {
            throw new Exception ("ident string must be set");
        }
        this.identString = ident;
        this.opt = logopt;
        this.facility = facility;
  
        try {
            this.udpSock = new DatagramSocket();
        } catch (SocketException e) {
            throw new Exception ("can not create socket for syslog");
        }
    }
    
    public void log(int pri, String msg) throws Exception {
        // construct UDP packet
        Integer priCode;
        Integer length;
        
        // create priCode from pri | facility
        priCode = (this.facility & LOG_FACMASK ) | pri;
        
        // calc packet length 
        length = 4 + this.identString.length() + msg.length() + 1
                + ((priCode > 99) ? 3 : ((priCode > 9) ? 2 : 1));
       
        // allocate buffer
        byte[] buf = new byte[length];
        int nPos = 0;
        
        // construct buffer
        // 1st : set priority code
        buf[nPos++] = '<';
        String priStr = priCode.toString();
        byte[] priBytes = priStr.getBytes();
        for (int i = 0; i < priBytes.length; i++) {
            buf[nPos++] = priBytes[i];
        }
    
        buf[nPos++] = '>';
    
        // 2nd : set ident string
        byte[] identBytes = this.identString.getBytes();
        for (int i = 0; i < identBytes.length; i++) {
            buf[nPos++] = identBytes[i];
        }
        buf[nPos++] = ':';
        buf[nPos++] = ' ';
     
        // 3rd : set message
        byte[] msgBytes = msg.getBytes();
        for (int i = 0; i < msgBytes.length; i++) {
            buf[nPos++] = msgBytes[i];
        }
        buf[nPos] = 0;
        
        // SEND IT!
        InetAddress address;
        try {
            address = InetAddress.getByAddress(new byte[] { (byte)127, 0, 0, 1 });
            DatagramPacket packet = new DatagramPacket(buf, length, address, LOCAL_SYSLOG_PORT);
            this.udpSock.send(packet);
        } catch (Exception e) {
            throw new Exception ("can not send packet to syslog daemon");
        }
    }

}
