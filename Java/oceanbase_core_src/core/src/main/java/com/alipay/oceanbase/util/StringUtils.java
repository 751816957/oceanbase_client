package com.alipay.oceanbase.util;

import java.io.IOException;
import java.io.StringReader;

/**
 * 
 * 
 * @author liangjie.li
 * @version $Id: StringUtils.java, v 0.1 Jun 7, 2013 6:25:24 PM liangjie.li Exp $
 */
public class StringUtils {
    public static final String NL = System.getProperty("line.separator");

    /**
     * Determines whether or not the sting 'searchIn' contains the string
     * 'searchFor', disregarding case and leading whitespace
     *
     * @param searchIn
     *            the string to search in
     * @param searchFor
     *            the string to search for
     *
     * @return true if the string starts with 'searchFor' ignoring whitespace
     */
    public static boolean startsWithIgnoreCaseAndWs(String searchIn, String searchFor) {
        return startsWithIgnoreCaseAndWs(searchIn, searchFor, 0);
    }

    /**
     * Determines whether or not the sting 'searchIn' contains the string
     * 'searchFor', disregarding case and leading whitespace
     *
     * @param searchIn
     *            the string to search in
     * @param searchFor
     *            the string to search for
     * @param beginPos
     *            where to start searching
     *
     * @return true if the string starts with 'searchFor' ignoring whitespace
     */
    public static boolean startsWithIgnoreCaseAndWs(final String searchIn, final String searchFor,
                                                    final int beginPos) {
        int curPos = beginPos;

        if (searchIn == null) {
            return searchFor == null;
        }

        final int inLength = searchIn.length();

        for (; curPos < inLength; curPos++) {
            if (!Character.isWhitespace(searchIn.charAt(curPos))) {
                break;
            }
        }

        return startsWithIgnoreCase(searchIn, curPos, searchFor);
    }

    /**
     * Determines whether or not the string 'searchIn' contains the string
     * 'searchFor', dis-regarding case starting at 'startAt' Shorthand for a
     * String.regionMatch(...)
     *
     * @param searchIn
     *            the string to search in
     * @param startAt
     *            the position to start at
     * @param searchFor
     *            the string to search for
     *
     * @return whether searchIn starts with searchFor, ignoring case
     */
    public static boolean startsWithIgnoreCase(String searchIn, int startAt, String searchFor) {
        return searchIn.regionMatches(true, startAt, searchFor, 0, searchFor.length());
    }

    /**
     * Returns the given string, with comments removed
     *
     * @param src
     *            the source string
     * @param stringOpens
     *            characters which delimit the "open" of a string
     * @param stringCloses
     *            characters which delimit the "close" of a string, in
     *            counterpart order to <code>stringOpens</code>
     * @param slashStarComments
     *            strip slash-star type "C" style comments
     * @param slashSlashComments
     *            strip slash-slash C++ style comments to end-of-line
     * @param hashComments
     *            strip #-style comments to end-of-line
     * @param dashDashComments
     *            strip "--" style comments to end-of-line
     * @return the input string with all comment-delimited data removed
     */
    public static String stripComments(String src, String stringOpens, String stringCloses,
                                       boolean slashStarComments, boolean slashSlashComments,
                                       boolean hashComments, boolean dashDashComments) {
        if (src == null) {
            return null;
        }

        StringBuffer buf = new StringBuffer(src.length());

        // It's just more natural to deal with this as a stream when parsing..
        // This code is currently only called when parsing the kind of metadata that developers are strongly recommended to cache anyways, 
        // so we're not worried about the _1_ extra object allocation if it cleans up the code

        StringReader sourceReader = new StringReader(src);

        int contextMarker = Character.MIN_VALUE;
        boolean escaped = false;
        int markerTypeFound = -1;

        int ind = 0;

        int currentChar = 0;

        try {
            while ((currentChar = sourceReader.read()) != -1) {

                if (markerTypeFound != -1 && currentChar == stringCloses.charAt(markerTypeFound)
                    && !escaped) {
                    contextMarker = Character.MIN_VALUE;
                    markerTypeFound = -1;
                } else if ((ind = stringOpens.indexOf(currentChar)) != -1 && !escaped
                           && contextMarker == Character.MIN_VALUE) {
                    markerTypeFound = ind;
                    contextMarker = currentChar;
                }

                if (contextMarker == Character.MIN_VALUE && currentChar == '/'
                    && (slashSlashComments || slashStarComments)) {
                    currentChar = sourceReader.read();
                    if (currentChar == '*' && slashStarComments) {
                        int prevChar = 0;
                        while ((currentChar = sourceReader.read()) != '/' || prevChar != '*') {
                            if (currentChar == '\r') {

                                currentChar = sourceReader.read();
                                if (currentChar == '\n') {
                                    currentChar = sourceReader.read();
                                }
                            } else {
                                if (currentChar == '\n') {

                                    currentChar = sourceReader.read();
                                }
                            }
                            if (currentChar < 0)
                                break;
                            prevChar = currentChar;
                        }
                        continue;
                    } else if (currentChar == '/' && slashSlashComments) {
                        while ((currentChar = sourceReader.read()) != '\n' && currentChar != '\r'
                               && currentChar >= 0)
                            ;
                    }
                } else if (contextMarker == Character.MIN_VALUE && currentChar == '#'
                           && hashComments) {
                    // Slurp up everything until the newline
                    while ((currentChar = sourceReader.read()) != '\n' && currentChar != '\r'
                           && currentChar >= 0)
                        ;
                } else if (contextMarker == Character.MIN_VALUE && currentChar == '-'
                           && dashDashComments) {
                    currentChar = sourceReader.read();

                    if (currentChar == -1 || currentChar != '-') {
                        buf.append('-');

                        if (currentChar != -1) {
                            buf.append(currentChar);
                        }

                        continue;
                    }

                    // Slurp up everything until the newline
                    while ((currentChar = sourceReader.read()) != '\n' && currentChar != '\r'
                           && currentChar >= 0)
                        ;
                }

                if (currentChar != -1) {
                    buf.append((char) currentChar);
                }
            }
        } catch (IOException ioEx) {
            // we'll never see this from a StringReader
        }

        return buf.toString();
    }

    private StringUtils() {
    }

}
