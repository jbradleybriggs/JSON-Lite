# JSON-Lite
Lightweight JSON Parser / Builder for Java

## Parsing / Constructing

```java
// parse a string:
JSON json = new JSON("{\"key\": \"value\"}");

// parse an input stream:
InputStream stream = getSomeInputStream();
JSON json = new JSON(stream);

// parse a file:
File file = new File("/home/user/Documents/someJson.json");
JSON json = new JSON(file);

// constructing from some Map:
LinkedHashMap<String, Object> map = getSomeMap();
JSON json = new JSON(map);

// or construct an empty JSON:
JSON json = new JSON();

```

## Getting Values
Getting values involves specifying the "key path" where a value is to be found. For example, let's say we want to get the name
"Bob" from the following object:
```json
{
  "details": {
    "name": "Bob",
    "age": 22
  }
}
```
The key path to "bob" is: ["details", "name"]. So we can get this name by doing the following:
```java
JSON json = new JSON("{\"details\": {\"name\": \"Bob\",\"age\": 22}}");

Object name = json.get("details", "name");
```

## Getting Values: Arrays
Let's say we have the following object with a "users" array:
```json
{
	"users": [
		{
			"name": "Bob",
			"age": 22
		},
		{
			"name": "Alice",
			"age": 31
		}
	]
}
```
Working with this object to get values is very simple:
```java
	// get all the users:
	Object users = json.get("users");

	// get the first user in the array:
	Object item = json.get("users", "0");

	// get the age of "Bob":
	Object age = json.get("users", "0", "age");
```

## Adding / Replacing Values
One way to add values is to specify the key path as a String array. To create the above object we can do the following:
```java
JSON json = new JSON();
json.add(new String[]{"details", "name"}, "Bob")
    .add(new String[]{"details", "age"}, 22); //notice the method chaining
```
However, this is annoying and cumbersome, since we need to create a new String array for each item. A better way is to do the following:
```java
JSON json = new JSON();
json.key("details", "name").value("Bob")
    .key("details", "age").value(22); //notice the method chaining
```
This too has redundancies and can be improved, as the "details" key is specified twice. The best way is to do the following:
```java
JSON json = new JSON();
json.key("details")
    .value("name", "Bob")
    .value("age", 22);
```

## Adding / Replacing Values: Arrays
Let's say we want to build the following object:
```json
{
	"users": [
		{
			"name": "Bob",
			"age": 22
		},
		{
			"name": "Alice",
			"age": 31
		}
	]
}
```
To do this, we need to specify that the "users" key is an array. To do that, we simply add an opening square bracket at the end of the key, so instead of 
"users" we say "users[". 
```java
JSON json = new JSON();
json.key("users[", "0")
		.value("name", "Bob")
		.value("age", 22)
	.key("users[", "1")
		.value("name", "Alice")
		.value("age", 31);
```

## Removing Values
To remove a value, simply call the "remove()" method and specify the key path to the value you want to remove.
```json
{
	"users": [
		{
			"name": "Bob",
			"age": 22
		},
		{
			"name": "Alice",
			"age": 31
		}
	]
}
```
```java
// remove the entire users array:
json.remove("users");

// remove the second item in the users array:
json.remove("users", "1");

// remove the age of "Alice":
json.remove("users", "1", "age");
```

 