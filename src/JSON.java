package report_generator.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight JSON parser.
 * 
 * @author j. bradley briggs
 */
public class JSON {
	private final JSONReader reader;
	private JSONObject contents;

	public JSON(Reader reader) throws IOException {
		this.reader = new JSONReader(reader);
		parse();
	}

	public JSON(String string) throws IOException {
		this.reader = new JSONReader(string);
		parse();
	}

	public JSON(InputStream stream) throws IOException {
		this.reader = new JSONReader(stream);
		parse();
	}
	
	public JSON() throws IOException {
		this("") ;
	}
	
	private void parse() throws IOException {
		reader.fastForward(); // move to the first non-whitespace character
		contents = this.parseObject(); 
		if (contents == null) contents = new JSONObject() ;
	}
	
	/**
	 * Parses a JSON object or a JSON array
	 * @return
	 * @throws IOException 
	 */
	private JSONObject parseObject() throws IOException {
		if (this.reader.state() == JSONReader.STATE_OBJECT || this.reader.state() == JSONReader.STATE_ARRAY) { //if we are in fact reading an object or array, otherwise don't bother
			JSONObject obj = new JSONObject();
			if (this.reader.state() ==  JSONReader.STATE_ARRAY) obj.isArray = true ; // check if this is an array or not

			String key = "", value = ""; //keys and values will be accumulated in here, we will know when we have either when the state changes to `STATE_KEY_READ` or to `STATE_VALUE_READ`
			int valueType = -1;  // the reader checks the first character of a value it is reading and returns its guess as to what the type might be
			while (!reader.done()) { // read until the stream has been consumed
				char c = reader.read(); // read from the stream and update the state accordingly
				int state = reader.state(); 
				switch (state) {
					case JSONReader.STATE_ARRAY: // started reading an array OR an object: recurse and return the object
					case JSONReader.STATE_OBJECT: 
						JSONObject subObj = this.parseObject();
						obj.add(key, subObj);
						key = "" ; value = "" ;
						break;
					case JSONReader.STATE_KEY_READ: // finished reading a key: a value follows
						key = value ; value = "" ;
						break;
					case JSONReader.STATE_VALUE: // started reading a value: accumulate characters into the `value` variable
						value += c;
						valueType = reader.type(); 
						break;
					case JSONReader.STATE_ESCAPE: // just read the start of an escape sequence in a string: read and unescape and add it to the `value` variable
						value += reader.readEscapeSequence();
						break;
					case JSONReader.STATE_VALUE_READ: // finished reading a value: add it to the object and reset the key and value
						obj.add(key, value, valueType) ;
						key = ""; value = "";
						break;
					case JSONReader.STATE_OBJECT_READ: // finished reading an array OR object: add it to the parent object and return
					case JSONReader.STATE_ARRAY_READ:
						if (!value.isEmpty()) obj.add(key, value);
						return obj;
				}
			}
			return obj;
		}
		return null;
	}
	
	/**
	 * Adds a key-value pair to the top-level of the JSON. If the key supplied is null, one will be auto-generated.
	 * @param value
	 * @param key
	 * @return 
	 */
	public JSON add(String key, Object value) {
		this.contents.add(key, value);
		return this;
	}
	
	/**
	 * Adds a value to the top-level of the JSON. The key will be auto-generated.
	 * @param value
	 * @return 
	 */
	public JSON add(Object value) {
		this.contents.add(null, value);
		return this ;
	}
	
	public JSON add(String[] keys, Object value) {
		if (keys == null || keys.length == 0) return this.add(value) ;
		JSONObject o = this.contents;
		int index = -1;
		for (String key : keys) {
			index++ ;
			if (index == keys.length-1) o.add(key, value) ;
			else {
				Object next = o.get(key)  ; 
				if (next == null || !(next instanceof JSONObject)) { // add a new object if the key doesn't exist or the key does exist but isn't an object
					o.add(key, new JSONObject());
					o = (JSONObject) o.get(key) ; // `o = (JSONObject) next ; ` is incorrect. We have overwritten the reference to `next` with the last line.
				}
				else o = (JSONObject) next ; // move into this object, don't create a new JSONObject, this will delete anything that might be in there 
			}
		}
		return this;
	}
	
	/**
	 * Removes a value from the top-level only if the key exists.
	 * @param key
	 * @return 
	 */
	public JSON remove(String key) {
		this.contents.remove(key) ;
		return this ;
	}
	
	@Override
	public String toString() {
		if (contents == null) return "{}" ;
		return this.contents.toString(false);
	}
	
	public String toString(boolean pretty) {
		if (contents == null) return (pretty ? "{\n}" : "{}") ;
		return this.contents.toString(pretty);
	}

	public static void main(String[] args) throws IOException {
		JSON json = new JSON() ;
		json.add(new String[]{"hello there", "general kenobi", "you are", "a bold one"}, "YES!") ;
		json.add(new String[]{"hello there", "general kenobi", "you are", "very bold"}, "YES!") ;
		json.add(new String[]{"hello there", "general kenobi", "you are", "very bold", "YES!"}, "\"500\u00B0C/") ;
		json.add(new String[]{"hello there", "general kenobi", "you are", "very bold", "NO!"}, "\uD834\uDD1E") ;
		System.out.println(json.toString(true));
//		
		
//		String surrogate = "\\uD834\\uDD1E" ;
//		System.out.println(Long.parseLong(surrogate, 16));

 	}
}

/**
 * JSON reader / state machine
 * @author j. bradley briggs
 */
class JSONReader {
	private final Reader reader;

	// Internal states
	private int state = -1;
	private int type = -1; // data type being read, this will save parsing time as the parser doesn't need to figure out what the type is.
	private boolean done = false; // done reading from the stream

	// JSON states
	public static final int STATE_WS = 0;			 // whitespace (NOT in  a string) has just been read.
	public static final int STATE_KEY_READ = 1;		 // finished reading a key.
	public static final int STATE_VALUE = 2;			 // started reading a value.
	public static final int STATE_VALUE_READ = 3;	// finished reading a value.
	public static final int STATE_OBJECT = 4;		// started reading an object.
	public static final int STATE_OBJECT_READ = 5; // finished reading an object.
	public static final int STATE_ARRAY = 6;		// started reading an array.
	public static final int STATE_ARRAY_READ = 7;	// finished reading an array.
	public static final int STATE_ESCAPE = 8;		// started reading an escape sequence (use `this.readEscapeSequence()` to get the unescaped string and update the state)
	
	// Data types  (we don't need data types for objects or arrays: we know straight away from the state that we have one of those.)
	public static final int TYPE_NONE = -1;
	public static final int TYPE_NULL = 0 ;
	public static final int TYPE_STRING = 1 ;
	public static final int TYPE_NUMBER = 2 ;
	public static final int TYPE_BOOLEAN = 3 ;

	public JSONReader(Reader reader) {
		this.reader = reader;
	}

	public JSONReader(InputStream stream) {
		this.reader = new InputStreamReader(stream);
	}

	public JSONReader(String string) {
		this.reader = new StringReader(string);
	}

	public int state() {
		return this.state;
	}
	
	public int type() {
		return this.type;
	}

	/**
	 * Whether the reader is done reading.
	 *
	 * @return
	 */
	public boolean done() {
		return this.done;
	}

	/**
	 * Read one more character and return it.
	 * @return
	 * @throws IOException 
	 */
	private char advance() throws IOException {
		return (char) reader.read();
	}

	/**
	 * Skips whitespace to the next non-whitespace character and returns it.
	 *
	 * @return
	 */
	public char fastForward() throws IOException {
		char out = ' ';
		while (!done && (state == STATE_WS || state == -1)) {
			out = read();
		}
		return out;
	}

	/**
	 * After a slash is read, this method reads the escape sequence, unescapes it, and updates the state.
	 * @return
	 * @throws IOException 
	 */
	public char readEscapeSequence() throws IOException {
		if (state == STATE_ESCAPE) {
			char next = this.read(); // the slash has already been read, so read the escape type
			switch (next) {
				case 'u':
				case 'U': // next 4 chars should be a 'control character' in hex 
					String hex = read(4); // this can be parsed like this: `	int codePoint = Integer.parseInt(hex, 16); `
						//TO DO: add support for UTF-16 - these are usually displayed as surrogate pairs e.g: \uD834\uDD1E
					return (char) Integer.parseInt(hex, 16)  ;
				case '"': return '\"'; // quotation mark
				case 'b': return '\b'; // backspace
				case 'f': return '\f'; // form feed
				case 'n': return '\n'; // new line
				case 'r': return '\r'; // carriage return
				case 't': return '\t'; //tab
				case '\\': return '\\' ; // slash
				case '/': return '/';
			}
			state = STATE_VALUE; //we can only encounter an escape sequence in a value, so return to this state.
		}
		return ' ';
	}

	private String read(int chars) throws IOException {
		String result = "";
		for (int i = 0 ; i < chars ; i++) {
			result += read();
		}
		return result;
	}

	/**
	 * Reads a character from the stream and updates the state accordingly.
	 *
	 * @return
	 * @throws IOException
	 */
	public char read() throws IOException {
		if (reader.ready()) {
			int read = reader.read();
			if (read == -1) {
				done = true;
				state = -1;
			}
			else {
				char c = (char) read;
				if (type == TYPE_STRING) { // Reading in a string, so we needn't worry about brackets, colons, commas - we do however need to worry about escape characters and we can't ignore whitespace.
					switch (c) {
						case '\\': state = STATE_ESCAPE; // an escape sequence follows, merely set the state and break; the method `readEscapeSequence` will now need to be called.
							break;
						case '"':
							/* The second quotation mark:
									- if this is for a key, then the COLON will indicate that a key has been read, and the STATE_KEY_READ will be set.
									- if this is for a value, then a comma / brace / square bracket will indicate that a value has been read, after which the STATE_VALUE_READ will be set.
								 so all we need to do here is change the state to something OTHER than `STATE_STRING` and the type to TYPE_NONE so we enter the 2ND switch statement on the next read.
							*/
							if (state != STATE_ESCAPE) {
								type = TYPE_NONE;
								state = STATE_WS;
							}
							else return c;
					}
				}
				else { // NOT reading in a string, we needn't worry about escaped characters or whitespace.
					switch (c) {
						case '"': // first quotation mark
							type = TYPE_STRING;
							state = STATE_VALUE; // this could be a string value OR a key, until we encounter a colon we don't know.
							char next = this.advance(); // advance past this quotation mark so we don't see it in the value.
							if (next == '"') { // the very next value is also a quotation mark, so the value we just read was a blank string.
								state = STATE_VALUE_READ;
							}
							return next ;
						case ':': state = STATE_KEY_READ; type = TYPE_NONE ; break; 
						case ',': state = STATE_VALUE_READ; break;
						case '{': state = STATE_OBJECT; break;
						case '}': state = STATE_OBJECT_READ ; type = TYPE_NONE ; break;
						case '[': state = STATE_ARRAY; break;
						case ']': state = STATE_ARRAY_READ; type = TYPE_NONE ; break;
						default: // non-string values (null, true, false, numbers)
							if ((c+"").matches("\\s")) { // whitespace character
								if (state == STATE_VALUE) state = STATE_VALUE_READ;
								else state = STATE_WS;
								type = TYPE_NONE ; 
							}
							else {
								if (type == TYPE_NONE) { // if we haven't set the type yet, guess by looking at the first character
									state = STATE_VALUE;
									switch (c) {
										case 'n': case 'N': type = TYPE_NULL; break;
										case 't': case 'T': case 'f': case 'F': type = TYPE_BOOLEAN; break ;
										default:  // very likely a number, otherwise default to a string
											if ((c+"").matches("[-+0-9.Ee]")) type =TYPE_NUMBER;
											else type = TYPE_STRING;
									}
								} else state = STATE_VALUE;
							}
					}
				}
				return c;
			}
		}
		return ' ';
	}

}

/**
 * Simple class for JSON objects AND JSON arrays.
 * @author j. bradley briggs
 */
class JSONObject extends LinkedHashMap<String, Object> {
	private int autoKey = -1;
	boolean isArray = false;
	public JSONObject() {
		super() ;
	}
	public JSONObject(boolean isArray) {
		super() ;
		this.isArray = isArray;
	}
	public void add(String key, Object object) {
		if (key == null || key.isEmpty()) {
			autoKey++;
			key = String.valueOf(autoKey);
		}
		if (object == null) object = new JSONNull() ;
		this.put(key, object) ;
	}
	public void add(String key, String value, int valueType) {
		if (key == null) key = "" ;
		if (value == null) value = "" ;
		if (key.isEmpty() && value.isEmpty()) ;
		else {
			/* a value could be:
					- Number
					- Boolean
					- Null
					- String (default) 
			*/
			switch (valueType) {
				case 0: this.add(key, new JSONNull()); break ;
				case 3: this.add(key, Boolean.valueOf(value)); break ;
				case 2: this.add(key, new JSONNumber(value)); break ;
				case 1:
				default: this.add(key, value) ;
			}
		}
	}
	
	/**
	 * Creates an iterator for moving through code points instead of characters.
	 *
	 * @param string
	 * @return
	 */
	private static Iterable<Integer> codePointIterator(final String string) {
		return new Iterable<Integer>() {
			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					private int next = 0;
					private final int length = string.length();

					@Override
					public boolean hasNext() {
						return this.next < this.length;
					}

					/**
					 * Advance to the next code point.
					 *
					 * @return
					 */
					@Override
					public Integer next() {
						int codePoint = string.codePointAt(next);
						next += Character.charCount(codePoint);
						return codePoint;
					}
				};
			}
		};
	}
	
	/**
	 * Escapes all special characters in JSON
	 * @param value
	 * @return 
	 */
	private String escapeString(String value) {
		StringBuilder sb = new StringBuilder(value.length()) ;
		for (int codePoint : codePointIterator(value)) {
			switch (codePoint) {
				case '"': sb.append("\\\"") ; break ;
				case '\\': sb.append("\\\\") ; break ;
				case '\n': sb.append("\\\n") ; break ;
				case '\t': sb.append("\\\t") ; break ;
				case '\f': sb.append("\\\f"); break ;
				case '\r': sb.append("\\\r"); break ;
				case '\b': sb.append("\\\b"); break;
				case '/': sb.append("\\/") ; break ;
				default:
					if (needsEscaping(codePoint) || Character.isSurrogate((char) codePoint)) {
						if (Character.isSurrogate((char) codePoint)) {
							char hi = Character.highSurrogate(codePoint) ;
							char lo = Character.lowSurrogate(codePoint) ;
							String escapeSequence = "\\" + Integer.toHexString(hi) + "\\" + Integer.toHexString(lo) ;
							sb.append(escapeSequence) ;
						} else {
							String escapeSequence = "\\" + Integer.toHexString(codePoint) ;
							sb.append(escapeSequence) ;
						}
					}
					else sb.appendCodePoint(codePoint) ;
			}
		}
		return sb.toString();
	}
	
	private boolean needsEscaping(int codePoint) {
		boolean inRange = (codePoint >= 0x20 && codePoint <= 0x21) ||
				   (codePoint >= 0x23 && codePoint <= 0x5B) ||
				   (codePoint >= 0x5D && codePoint <= 0x10FFFF) ;
		boolean isControl = Character.isISOControl(codePoint) ;
		return isControl || !inRange ;
	}

	private String tab(boolean pretty, int depth) {
		String result = "" ;
		if (!pretty) return result ;
		for (int i=0; i< depth; i++) result += "\t" ;
		return result ;
	}

	private String toString(JSONObject o, boolean pretty, int depth) {
		if (o == null) return "{}" ;
		String nl = (pretty ? "\n" : "") ;
		String result = (o.isArray ? "[" : "{") + nl;
		
		int index = -1 ;
		for (Map.Entry<String, Object> entry : o.entrySet()) {
			index++ ;
			String key = (o.isArray ? "": "\"" + entry.getKey() + "\": ") ;
			Object value = entry.getValue();
			String stringValue ;
			if (value instanceof JSONObject) stringValue = this.toString((JSONObject) value, pretty, depth+1) ; // object OR array
			else if (value instanceof String) stringValue = "\"" + this.escapeString(value.toString()) + "\"";
			else stringValue = value.toString()  ;
			
			result += tab(pretty, depth+1) + key + stringValue ;
			if (index != o.size()-1) result += ", " + nl;
		}
		
		return result + nl + tab(pretty, depth) + (o.isArray ? "]" : "}")  ;
	}
	
	@Override
	public String toString() {
		return this.toString(this, false, 0);
	}
	
	public String toString(boolean pretty) {
		return this.toString(this, pretty, 0) ;
	}
	
}

class JSONNull {
	public JSONNull() {}
	@Override public String toString() { return "null";}
}

class JSONNumber {
	public final String value;
	public JSONNumber(String value) {
		if (value == null) value = "NaN";
		if (value.isEmpty()) value = "0";
		this.value = value;
	}
	@Override
	public String toString() {
		return value;
	}
}