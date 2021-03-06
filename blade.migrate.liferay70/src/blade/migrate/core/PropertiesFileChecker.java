package blade.migrate.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class PropertiesFileChecker {

	public static class KeyInfo
	    {
	        public final int offset;
	        public final int length;
	        public String value;

	        public KeyInfo( int offset, int length )
	        {
	            this.offset = offset;
	            this.length = length;
	        }
	    }

	class LineReader {
	        public LineReader(Reader reader) {
	            this.reader = reader;
	            inCharBuf = new char[8192];
	        }

	        char[] inCharBuf;
	        char[] lineBuf = new char[1024];
	        int inLimit = 0;
	        int inOff = 0;
	        int total = 0;
	        Reader reader;

	        int[] readLine() throws IOException {
	            int len = 0;
	            char c = 0;

	            boolean skipWhiteSpace = true;
	            boolean isCommentLine = false;
	            boolean isNewLine = true;
	            boolean appendedLineBegin = false;
	            boolean precedingBackslash = false;
	            boolean skipLF = false;

	            while (true) {
	                if (inOff >= inLimit) {
	                    inLimit = reader.read(inCharBuf);
	                    inOff = 0;
	                    if (inLimit <= 0) {
	                        if (len == 0 || isCommentLine) {
	                            return new int[] { -1, total };
	                        }
	                        return new int[] { len, total };
	                    }
	                }
	                c = inCharBuf[inOff++];
	                total++;
	                if (skipLF) {
	                    skipLF = false;
	                    if (c == '\n') {
	                        continue;
	                    }
	                }
	                if (skipWhiteSpace) {
	                    if (c == ' ' || c == '\t' || c == '\f') {
	                        continue;
	                    }
	                    if (!appendedLineBegin && (c == '\r' || c == '\n')) {
	                        continue;
	                    }
	                    skipWhiteSpace = false;
	                    appendedLineBegin = false;
	                }
	                if (isNewLine) {
	                    isNewLine = false;
	                    if (c == '#' || c == '!') {
	                        isCommentLine = true;
	                        continue;
	                    }
	                }

	                if (c != '\n' && c != '\r') {
	                    lineBuf[len++] = c;
	                    if (len == lineBuf.length) {
	                        int newLength = lineBuf.length * 2;
	                        if (newLength < 0) {
	                            newLength = Integer.MAX_VALUE;
	                        }
	                        char[] buf = new char[newLength];
	                        System.arraycopy(lineBuf, 0, buf, 0, lineBuf.length);
	                        lineBuf = buf;
	                    }
	                    //flip the preceding backslash flag
	                    if (c == '\\') {
	                        precedingBackslash = !precedingBackslash;
	                    } else {
	                        precedingBackslash = false;
	                    }
	                }
	                else {
	                    // reached EOL
	                    if (isCommentLine || len == 0) {
	                        isCommentLine = false;
	                        isNewLine = true;
	                        skipWhiteSpace = true;
	                        len = 0;
	                        continue;
	                    }
	                    if (inOff >= inLimit) {
	                        inLimit = reader.read(inCharBuf);
	                        inOff = 0;
	                        if (inLimit <= 0) {
	                            return new int[] { len, total };
	                        }
	                    }
	                    if (precedingBackslash) {
	                        len -= 1;
	                        //skip the leading whitespace characters in following line
	                        skipWhiteSpace = true;
	                        appendedLineBegin = true;
	                        precedingBackslash = false;
	                        if (c == '\r') {
	                            skipLF = true;
	                        }
	                    } else {
	                        return new int[] { len, total };
	                    }
	                }
	            }
	        }
	    }

	private final File file;
	private Map<String, KeyInfo> keyInfos = new HashMap<>();

	public PropertiesFileChecker(File file){
		this.file = file;

		try {
			this.keyInfos = parse(new FileInputStream(file));
		}
		catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	 private String loadConvert (char[] in, int off, int len, char[] convtBuf) {
	        if (convtBuf.length < len) {
	            int newLen = len * 2;
	            if (newLen < 0) {
	                newLen = Integer.MAX_VALUE;
	            }
	            convtBuf = new char[newLen];
	        }
	        char aChar;
	        char[] out = convtBuf;
	        int outLen = 0;
	        int end = off + len;

	        while (off < end) {
	            aChar = in[off++];
	            if (aChar == '\\') {
	                aChar = in[off++];
	                if(aChar == 'u') {
	                    // Read the xxxx
	                    int value=0;
	                    for (int i=0; i<4; i++) {
	                        aChar = in[off++];
	                        switch (aChar) {
	                          case '0': case '1': case '2': case '3': case '4':
	                          case '5': case '6': case '7': case '8': case '9':
	                             value = (value << 4) + aChar - '0';
	                             break;
	                          case 'a': case 'b': case 'c':
	                          case 'd': case 'e': case 'f':
	                             value = (value << 4) + 10 + aChar - 'a';
	                             break;
	                          case 'A': case 'B': case 'C':
	                          case 'D': case 'E': case 'F':
	                             value = (value << 4) + 10 + aChar - 'A';
	                             break;
	                          default:
	                              throw new IllegalArgumentException("Malformed \\uxxxx encoding."); //$NON-NLS-1$
	                        }
	                     }
	                    out[outLen++] = (char)value;
	                } else {
	                    if (aChar == 't') aChar = '\t';
	                    else if (aChar == 'r') aChar = '\r';
	                    else if (aChar == 'n') aChar = '\n';
	                    else if (aChar == 'f') aChar = '\f';
	                    out[outLen++] = aChar;
	                }
	            } else {
	                out[outLen++] = aChar;
	            }
	        }
	        return new String (out, 0, outLen);
	 }

	 private Map<String, KeyInfo> parse( InputStream input ) throws IOException
	 {
	        final LineReader lr = new LineReader( new InputStreamReader( input ) );
	        Map<String, KeyInfo> keyInfos = new HashMap<String,KeyInfo>();
	        char[] convtBuf = new char[1024];
	        int[] limit;
	        int keyLen;
	        int valueStart;
	        char c;
	        boolean hasSep;
	        boolean precedingBackslash;

	        while ((limit = lr.readLine())[0] >= 0) {
	            c = 0;
	            keyLen = 0;
	            valueStart = limit[0];
	            hasSep = false;

	            //System.out.println("line=<" + new String(lineBuf, 0, limit) + ">");
	            precedingBackslash = false;
	            while (keyLen < limit[0]) {
	                c = lr.lineBuf[keyLen];
	                //need check if escaped.
	                if ((c == '=' ||  c == ':') && !precedingBackslash) {
	                    valueStart = keyLen + 1;
	                    hasSep = true;
	                    break;
	                } else if ((c == ' ' || c == '\t' ||  c == '\f') && !precedingBackslash) {
	                    valueStart = keyLen + 1;
	                    break;
	                }
	                if (c == '\\') {
	                    precedingBackslash = !precedingBackslash;
	                } else {
	                    precedingBackslash = false;
	                }
	                keyLen++;
	            }
	            while (valueStart < limit[0]) {
	                    c = lr.lineBuf[valueStart];
	                    if (c != ' ' && c != '\t' &&  c != '\f') {
	                        if (!hasSep && (c == '=' ||  c == ':')) {
	                            hasSep = true;
	                        } else {
	                            break;
	                        }
	                    }
	                    valueStart++;
	            }

	            String key = loadConvert(lr.lineBuf, 0, keyLen, convtBuf);
	            KeyInfo info = new KeyInfo( limit[1] - limit[0] - 1, keyLen );

	            info.value = loadConvert(lr.lineBuf, valueStart, limit[0] - valueStart, convtBuf);

	            keyInfos.put( key, info );
	        }
	        return keyInfos;
	}

	public SearchResult findProperty( String key ){
		KeyInfo keyInfo = keyInfos.get(key);
		if(keyInfo != null){
			SearchResult searchResult = new SearchResult(file, keyInfo.offset, keyInfo.offset + keyInfo.length);
			return searchResult;
		}
		return null;
	}

}
