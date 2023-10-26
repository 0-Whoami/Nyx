package com.termux.shared.termux.data;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxUrlUtils {

    public static Pattern URL_MATCH_REGEX;

    public static Pattern getUrlMatchRegex() {
        if (URL_MATCH_REGEX != null)
            return URL_MATCH_REGEX;
        // Begin first matching group.
        String regex_sb = "(" +
                // Begin scheme group.
                "(?:" +
                // The DAV proto.
                "dav|" +
                // The DICT proto.
                "dict|" +
                // The DNS proto.
                "dns|" +
                // File path.
                "file|" +
                // The Finger proto.
                "finger|" +
                // The FTP proto.
                "ftp(?:s?)|" +
                // The Git proto.
                "git|" +
                // The Gemini proto.
                "gemini|" +
                // The Gopher proto.
                "gopher|" +
                // The HTTP proto.
                "http(?:s?)|" +
                // The IMAP proto.
                "imap(?:s?)|" +
                // The IRC proto.
                "irc(?:[6s]?)|" +
                // The IPFS proto.
                "ip[fn]s|" +
                // The LDAP proto.
                "ldap(?:s?)|" +
                // The POP3 proto.
                "pop3(?:s?)|" +
                // The Redis proto.
                "redis(?:s?)|" +
                // The Rsync proto.
                "rsync|" +
                // The RTSP proto.
                "rtsp(?:[su]?)|" +
                // The SFTP proto.
                "sftp|" +
                // The SAMBA proto.
                "smb(?:s?)|" +
                // The SMTP proto.
                "smtp(?:s?)|" +
                // The Subversion proto.
                "svn(?:(?:\\+ssh)?)|" +
                // The TCP proto.
                "tcp|" +
                // The Telnet proto.
                "telnet|" +
                // The TFTP proto.
                "tftp|" +
                // The UDP proto.
                "udp|" +
                // The VNC proto.
                "vnc|" +
                // The Websocket proto.
                "ws(?:s?)" +
                // End scheme group.
                ")://" +
                // End first matching group.
                ")" +
                // Begin second matching group.
                "(" +
                // User name and/or password in format 'user:pass@'.
                "(?:\\S+(?::\\S*)?@)?" +
                // Begin host group.
                "(?:" +
                // IP address (from http://www.regular-expressions.info/examples.html).
                "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|" +
                // Host name or domain.
                "(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*){1,}[a-z\\u00a1-\\uffff0-9]{1,}))?|" +
                // Just path. Used in case of 'file://' scheme.
                "/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)" +
                // End host group.
                ")" +
                // Port number.
                "(?::\\d{1,5})?" +
                // Resource path with optional query string.
                "(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?" +
                // Fragment.
                "(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?" +
                // End second matching group.
                ")";
        URL_MATCH_REGEX = Pattern.compile(regex_sb, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        return URL_MATCH_REGEX;
    }

    public static LinkedHashSet<CharSequence> extractUrls(String text) {
        LinkedHashSet<CharSequence> urlSet = new LinkedHashSet<>();
        Matcher matcher = getUrlMatchRegex().matcher(text);
        while (matcher.find()) {
            int matchStart = matcher.start(1);
            int matchEnd = matcher.end();
            String url = text.substring(matchStart, matchEnd);
            urlSet.add(url);
        }
        return urlSet;
    }
}
