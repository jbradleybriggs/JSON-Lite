/********************************************************************************
	Copyright (c) 2019 J. Bradley Briggs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*******************************************************************************/

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Lightweight JSON parser.
 * 
 * @author j. bradley briggs
 */
public class JSON {
	private final JSONReader reader;
	private JSONObject contents;
	private String[] ephemeral ; // ephemeral keys (set by the `key()` method, and used by the `value()` method to add a key-value pair to the json)
	
	/*Constructors ********************************************************************************************************************************************/
	public JSON(Reader reader) {
		this.reader = new JSONReader(reader);
		try { parse();}
		catch (IOException ex) { }
	}
	public JSON(String string) {
		this.reader = new JSONReader(string);
		try { parse();}
		catch (IOException ex) { }
	}
	public JSON(InputStream stream) {
		this.reader = new JSONReader(stream);
		try { parse();}
		catch (IOException ex) { }
	}
	public JSON() { this("") ;}
	public JSON(Map<String, Object> map) throws Exception {
		this.reader = null;
		this.contents = new JSONObject(map);
	}
	
	/*Basic Stuff ********************************************************************************************************************************************/
	public boolean isNull() { return contents == null; }
	public boolean isEmpty() { return this.contents.isEmpty(); }
	public LinkedHashMap<String, Object> getMap() { return this.contents; }
	public Set<Entry<String, Object>> entrySet() { return contents.entrySet();}
	public Set<String> keySet() { return contents.keySet(); }
	public Collection<Object> values() { return contents.values(); }
	public byte[] getBytes() { return this.toString().getBytes(); }
	
	/*To String ********************************************************************************************************************************************/
	@Override public String toString() {
		if (contents == null) return "{}" ;
		return this.contents.toString(false);
	}
	
	public String toString(boolean pretty) {
		if (contents == null) return (pretty ? "{\n}" : "{}") ;
		return this.contents.toString(pretty);
	}
	
	/*Parsing ********************************************************************************************************************************************/
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

			String key = "", value = "";		// keys and values will be accumulated here, we will know when we have either when the state changes to `STATE_KEY_READ` or to `STATE_VALUE_READ`
			int valueType = -1;				// the reader checks the first character of a value it is reading and returns its guess as to what the type might be
			while (!reader.done()) {			// read until the stream has been consumed
				char c = reader.read();		// read from the stream and 
				int state = reader.state();	// update the state accordingly
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
	
	/*Inserting and Removing values ***************************************************************************************************************************/
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
	
	/**
	 * Adds a value at a particular "key path". For example:<br>
	 * <code>
	 *		JSON json = new JSON() ;<br>
	 *		json.add(new String[]{"level1", "level2", "key"}, "value");<br>
	 * </code>
	 * This will produce the following JSONobject: <br>
	 * <code>
	 *		{<br>
	 *			"level1": {<br>
	 *				"level2": {<br>
	 *					"key": "value"<br>
	 *				}<br>
	 *		}
	 *</code>
	 * @param keys
	 * @param value
	 * @return 
	 */
	public JSON add(String[] keys, Object value) {
		if (keys == null || keys.length == 0) return this.add(value) ;
		JSONObject o = this.contents;
		int index = -1;
		boolean array;
		for (String key : keys) {
			index++ ;
			if (key != null && key.endsWith("[")) {
				array = true;
				key = key.substring(0, key.length()-1);
			} else array = false;
			if (index == keys.length-1) o.add(key, value) ; // final key: add value at this key
			else {								   // not the final key: create an object OR an array, an move into it
				Object next = o.get(key)  ; 
				if (next == null || !(next instanceof JSONObject)) o = (JSONObject) o.add(key, new JSONObject(array)); // add a new object if the key doesn't exist or the key does exist but isn't an object
				else o = (JSONObject) next ; // move into this object, DON'T create a new JSONObject, this will delete anything that might already be in there.
			}
		}
		return this;
	}
	
	public JSON addArray(String[] keys, Object... array) {
		this.add(keys, array) ;
		return this;
 	}
	
	/**
	 * Removes a value if the key exists and returns it; returns null otherwise.
	 * @param keys
	 * @return 
	 */
	public Object remove(String... keys) {
		if (keys == null || keys.length == 0) return null;
		Object current = this.contents;
		int index=-1;
		for (String key: keys) {
			index++;
			if (current instanceof Map) {
				if ( ((Map<String, Object>) current).containsKey(key) )  {
					if (index != keys.length-1) current = ((Map<String, Object>) current).get(key); // not the final key: move in
					else return ((Map<String, Object>) current).remove(key); // the final key: remove this value
				} else return null; //key does not exist
			}
		}
		return null ;
	}
	
	/**
	 * Gets a value at a particular key path, returns null if the key path is incorrect. If no key path is specified, the entire JSON object is returned.
	 * @param keys
	 * @return 
	 */
	public Object get(String... keys) {
		if (keys == null || keys.length == 0) return this.contents;
		LinkedHashMap<String, Object> current = this.contents;
		int index = 0;
		for (String key : keys) {
			index++;
			if (current.containsKey(key)) { //move in...
				Object inner = current.get(key);
				
				if (index == keys.length) { // the last key so simply return `inner`
					return inner;
				} else { // not the last key, change `current` to the value at this key only if it is a map, otherwise the key path is incorrect
					if (inner instanceof Map) current = (LinkedHashMap<String, Object>) inner;
					else return null;
				}
			} else return null;
		}
		return null;
	}
	
	public boolean hasKey(String... keys) { return this.get(keys) != null; }
	
	/**
	 * Creates a temporary key path. This should be used immediately before calling the `value()` method where the value for this key will be set.
	 * @param keys
	 * @return 
	 */
	public JSON key(String... keys) {
		if (keys != null && keys.length > 0) this.ephemeral = keys;
		else this.ephemeral = new String[] {"0"};
		return this;
	}
	
	/**
	 * Adds the value at the temporary key path created with the `key()` method.
	 * @param value
	 * @return 
	 */
	public JSON value(Object value) { return this.add(ephemeral, value); }
	
	/**
	 * Adds the values as an array at the temporary key path created with the `key()` method.
	 * @param array
	 * @return 
	 */
	public JSON value(Object... array) { return this.addArray(ephemeral, array); }
	
	/**
	 * Adds a value at the temporary key path created with the `key()` method, but appends `subKey` to the key path.
	 * @param subKey
	 * @param value
	 * @return 
	 */
	public JSON value(String subKey, Object value) {
		if (subKey == null || subKey.isEmpty()) return this.value(value);
		
		String[] ephemeralNew = arrayPush(ephemeral, subKey);
		return this.add(ephemeralNew, value);
	}
	/**
	 * Adds the values as an array at the temporary key path created with the `key()` method, but appends `subKey` to the key path.
	 * @param subKey
	 * @param value
	 * @return 
	 */
	public JSON value(String subKey, Object... value) {
		if (subKey == null || subKey.isEmpty()) return this.value(value);
		
		String[] ephemeralNew = arrayPush(ephemeral, subKey);
		return this.addArray(ephemeralNew, value);
	}
	
	/**
	 * Moves up the temporary key path created with the `key()` method, by the `numOfKeys` parameter.
	 * @param numOfKeys
	 * @return 
	 */
	public JSON up(int numOfKeys) {
		if (numOfKeys <= 0) return this;
		ephemeral = arrayPop(ephemeral, numOfKeys);
		return this;
	}
	
	/**
	 * Moves up 1 key in the temporary key path created with the `key()` method. This is equivalent to `up(1)`
	 * @return 
	 */
	public JSON up() { return up(1); }
	
	public JSON down(String key) {
		ephemeral = arrayPush(ephemeral, key);
		return this;
	}
	
	private Object[] arrayDelete(Object[] original, int index) {
		if (original == null) return null;
		if (index < 0 || index > original.length-1) return original;
		Object[] o = new Object[original.length-1];
		int j=-1;
		for (int i=0; i< original.length; i++) {
			if (i != index) {
				j++;
				o[j] = original[i];
			}
		}
		return o;
	}
	
	/**
	 * Pops a certain number of items off the end of the specified array.
	 * @param array
	 * @param num
	 * @return 
	 */
	private String[] arrayPop(String[] array, int num) {
		if (array == null || array.length == 0) return null;
		if (num <= 0) return array;
		if (num >= array.length) return new String[0];
		String[] out = new String[array.length-num];
		System.arraycopy(array, 0, out, 0, array.length-num);
		return out;
	}
	
	private String[] arrayPush(String[] array, String value) {
		String[] newArray = new String[array.length+1];
		System.arraycopy(array, 0, newArray, 0, array.length);
		newArray[newArray.length-1] = value;
		return newArray;
	}
	
	/*Functional stuff ********************************************************************************************************************************************/
	public void forEach(BiConsumer<? super String, ? super Object> action) {
		if (contents == null) return;
		this.contents.forEach(action) ;
	}
	
	public JSON filterByValue(Map<String, Object> map, Predicate<? super Object> predicate) throws Exception {
		if (contents == null) return new JSON();
		Map<String, Object> result = 
								map.entrySet()
								.stream()
								//.filter(entry -> predicate.test(entry.getValue()))
								.filter(predicate)
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return new JSON(result) ;
	}
	
	/**
	 * Filters the JSON with a given predicate. A predicate is a mathematical function that receives a value and
	 * returns a BOOLEAN. The predicate you specify must return `true` if you want to keep the key-value pair and
	 * return `false` if you wish to discard it. For example let's say we have the following object:<br>
	 * [ {"name": "Alice"}, {"name": "Allen"}, {"name": "Bob"} ]<br><br>
	 * If we want to filter this object and only display people whose names start with 'A', we can do the following:<br>
	 * <code> 
	 * JSON json = new JSON("[ {\"name\": \"Alice\"}, {\"name\": \"Allen\"}, {\"name\": \"Bob\"} ]") ;<br>
	 * JSON filteredJson = json.filter( (entry) -> {return ((LinkedHashMap<String, Object>) entry.getValue()) // cast this value to a map<br>
	 * .get("name") //get the 'name' key<br> 
	 * .startsWith("A");}); //check if it starts with an 'A'<br>
	 * </code>
	 * @param entryPredicate
	 * @return
	 * @throws Exception 
	 */
	public JSON filter(Predicate<? super Entry<String, Object>> entryPredicate) throws Exception {
		if (contents == null) return new JSON();
		Map<String, Object> map = this.contents
								.entrySet()
								.stream()
								.filter(entryPredicate)
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) ;
		return new JSON(map);
	}
	
	public JSON replaceAll(BiFunction<? super String, ? super Object, ? super Object> function) throws Exception {
		if (contents == null) return new JSON();
		this.contents.replaceAll(function);
		return this;
	}
	
	public static void main(String[] args) {
		JSON json = new JSON() ;
//		json.key("a", "b")
//				  .value("c", "hello")
//				  .value("d", "there")
//			.key("a", "e")
//				  .value("f", "General")
//				  .value("g", "Kenobi")
//			.key("h")
//				  .value("i", 56, 34, 2)
//			.key("j", "k", "l", "m")
//				  .value("n", "You Are")
//				  .value("o", "A Bold")
//				  .value("p", 1)
//			.up() //j, k, l
//				  .value("q", null, null)
//			.key("r", "s[", "0")
//				  .value("t", 12, 13)
//				  .value("u", false)
//			.key("r", "s[", "1")
//				  .value("t", 98, 223)
//				  .value("u", true)
//			.key("v[", "[")
//				  .value("[", 12.3, 23.2, 11.2)
//				  .value("[", 45.2, 0.65, 23.1)
//			.key("w", "x")
//				  .value("y", "Oh it's you")
//				  .value("z", "Hello again there") ;
				  
//		json.key("a[", "[")
//				  .value("[", 12, 13, 14, 15)
//				  .value("[", 34, 56, 12, 32)
//			.key("b[", "[")
//				  .value("[", 34, 56, 34, 12)
//				  .value("[", 122, 342, 223, 12)
//			;

		json.key("users[", "0")
				  .value("name", "Bob")
				  .value("age", 22)
			.key("users[", "1")
				   .value("name", "Alice")
				  .value("age", 31)
				  
				  ;
		
//		System.out.println(json.remove("j", "k", "l", "q"));
		System.out.println(json.toString(true));
		System.out.println(json.get("users", "0", "name"));
//		System.out.println(json.get("r"));
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

	public JSONReader(Reader reader) { this.reader = reader; }
	public JSONReader(InputStream stream) { this.reader = new InputStreamReader(stream); }
	public JSONReader(String string) { this.reader = new StringReader(string); }
	public int state() { return this.state; }
	public int type() { return this.type; }

	/**
	 * Whether the reader is done reading.
	 *
	 * @return
	 */
	public boolean done() { return this.done; }

	/**
	 * Read one more character and return it.
	 * @return
	 * @throws IOException 
	 */
	private char advance() throws IOException { return (char) reader.read(); }

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
//				case '/': return '/';
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
	public JSONObject() { super() ; }
	public JSONObject(Map<String, Object> map) {super(map) ;}
	public JSONObject(boolean isArray) {
		super() ;
		this.isArray = isArray;
	}
	public JSONObject(Object[] array) {
		super(new LinkedHashMap<String, Object>());
		this.isArray = true;
		if (array != null) {
			int i=-1;
			for (Object item : array) this.put(String.valueOf(++i), item);
		}
	};
	public Object add(String key, Object object) {
		if (key == null || key.isEmpty()) {
			autoKey++;
			key = String.valueOf(autoKey);
		}
		if (object == null) object = new JSONNull() ;
		if (object instanceof Object[] ) this.put(key, new JSONObject((Object[]) object)) ;
		else if (object instanceof JSON) this.put(key, ((JSON) object).getMap());
		else this.put(key, object) ;
		return this.get(key);
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
				case JSONReader.TYPE_NULL: this.add(key, new JSONNull()); break ;
				case JSONReader.TYPE_BOOLEAN: this.add(key, Boolean.valueOf(value)); break ;
				case JSONReader.TYPE_NUMBER: this.add(key, new JSONNumber(value)); break ;
				case JSONReader.TYPE_STRING: this.add(key, value) ; break ;
				default: this.add(key, value) ;
			}
		}
	}
	public void add(Object object) {
		this.add(null, object);
	}
	
	@Override
	public String toString() {
		return this.toString(false) ;
	}
	
	public String toString(boolean pretty) {
		JSONWriter jw = new JSONWriter(pretty) ;
		jw.writeValue(this) ;
		return jw.toString();
	}
	
	public Object[] toArray() { return this.values().toArray(); }
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
class JSONWriter {
	private StringBuilder sb = new StringBuilder() ;
	private final boolean pretty ; 
	private final String newLine  ;
	private int depth = 0 ;
	
	JSONWriter(boolean pretty) {
		this.pretty = pretty ;
		this.newLine = (pretty ? "\n" : "") ;
	}
	public void write(String value) { sb.append(value) ; }
	public void writeKey(String key) { if (key != null && !key.isEmpty()) write("\"" + key + "\": ") ; }
	public void writeValue(Object value) {
		if (value == null) this.write("null");
		else if (value instanceof List) this.writeValue( ((List) value).toArray() ); 
		else if  (value instanceof Collection) this.writeValue( ((Collection) value).toArray() ); 
		else if  (value instanceof Set) this.writeValue( ((Set) value).toArray() ); 
		else if  (value instanceof Object[]) this.writeArray(value) ;
		else if (value instanceof JSONObject) { // object OR array
			JSONObject jo = (JSONObject) value;
			if ( jo.isArray ) this.writeArray(jo.values().toArray());
			else this.writeMap(value);
		}
		else if (value instanceof Map) this.writeMap(value) ;
		else if (value instanceof Boolean) this.write(value.toString());
		else if (value instanceof Number) this.write(value.toString());
		else if (value instanceof JSONNull) this.write(value.toString());
		else if (value instanceof JSONNumber) this.write(value.toString());
		else {
			this.write("\"" + this.escapeString(value.toString()) + "\"");
		}
	}
	
	private void writeArray(Object array) {
		if (array instanceof Object[]) {
			Object[] a = (Object[]) array;
			write("[" + newLine);
			depth++;
			int index = -1 ;
			for (Object value : a) {
				index++ ;
				write(tab());
				writeValue(value); 
				if (index != a.length-1) write(", " + newLine);
			}
			depth--;
			write(newLine + tab() + "]")  ;
		}
	}
	
	private void writeMap(Object map) {
		if (map instanceof  Map) {
			Map<Object, Object> m = (Map) map;
			write("{" + newLine);
			depth++;
			int index = -1 ;
			for (Map.Entry<Object, Object> entry : m.entrySet()) {
				index++ ;
				write(tab());
				writeKey(entry.getKey().toString());
				writeValue(entry.getValue()); 
				if (index != m.size()-1) write(", " + newLine);
			}
			depth--;
			write(newLine + tab() + "}")  ;
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
//				case '/': sb.append("\\/") ; break ;
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

	private String tab() {
		String result = "" ;
		if (!pretty) return result ;
		for (int i=0; i< depth; i++) result += "\t" ;
		return result ;
	}
	
	@Override
	public String toString() {
		return this.sb.toString();
	}
}
