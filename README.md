# JSON-Lite
Lightweight JSON parser / builder for Java

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
## Getting values
Getting values involves specifying the "key path" where a value is to be found. For example, let's say we want to get the name
"Bob" from the following JSON:
```json
{
  "details": {
    "name": "Bob",
    "age": 22
  }
}
```
The key path to "bob" is: "details" then "name". So we can get the name by:
```java
JSON json = new JSON("{\"details\": {\"name\": \"Bob\",\"age\": 22}}");
Object name = json.get("details", "name");
```

## Adding values
One way to add values is to specify the key path as a String array. To create the above JSON we can do the following:
```java
JSON json = new JSON();
json.add(new String[]{"details", "name"}, "Bob")
    .add(new String[]{"details", "age"}, 22);
```
However, this is cumbersome. A better way is to do the following:
```java
JSON json = new JSON();
json.key("details", "name").value("Bob")
    .key("details", "age").value(22);
```
This too has redundancies and can be improved, as the "details" key is specified twice. The best way is to do the following:
```java
JSON json = new JSON();
json.key("details")
    .value("name", "Bob")
    .value("age", 22);
```


