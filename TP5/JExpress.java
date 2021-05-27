import static java.lang.System.out;
import static java.util.Collections.shuffle;
import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An express.js-like application framework
 *
 * Compile the application with : javac JExpress.java
 * Run the application with     : java JExpress
 * Get the documentation with   : javadoc -d ../doc JExpress.java
 */
@SuppressWarnings("restriction")
public class JExpress {
  /**
   * A HTTP request
   */
  public interface Request {
    /**
     * The HTTP method of the request.
     * @return the HTTP method.
     */
    String method();

    /**
     * The HTTP path of the request.
     * @return the HTTP path.
     */
    String path();

    /**
     * Get named route parameter
     * @param name name of the parameter
     * @return the value of the parameter or ""
     */
    String param(String name);

    /**
     * Returns the body of the request
     * @return the body of the request
     */
    InputStream body();

    /**
     * Returns the body of the request as a String.
     * @return the body of the request as a String.
     * @throws IOException if an I/O error occurs.
     */
    public String bodyText() throws IOException;
  }

  private static Request request(HttpExchange exchange) {
    return new Request() {
      @Override
      public String method() {
        return exchange.getRequestMethod().toUpperCase();
      }

      @Override
      public String path() {
        return exchange.getRequestURI().getPath();
      }

      @Override
      @SuppressWarnings("unchecked")
      public String param(String name) {
        return ((Map<String, String>)exchange.getAttribute("params")).getOrDefault(name, "");
      }

      @Override
      public InputStream body() {
        return exchange.getRequestBody();
      }

      @Override
      public String bodyText() throws IOException {
        try(InputStream in = exchange.getRequestBody();
            InputStreamReader reader = new InputStreamReader(in);
            BufferedReader buffered = new BufferedReader(reader)) {
          return buffered.lines().collect(joining("\n"));
        }
      }
    };
  }

  /**
   * A HTTP response
   */
  public interface Response {
    /**
     * Set the HTTP status of the response
     * @param status the HTTP status (200, 404, etc)
     * @return the current response.
     */
    Response status(int status);

    /**
     * Appends the specified value to the HTTP response header field.
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    Response append(String field, String value);

    /**
     * Sets the responseâ€™s HTTP header field to value
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    Response set(String field, String value);

    /**
     * Sets the Content-Type HTTP header to the MIME type.
     * @param type the MIME type.
     * @return the current response.
     */
    Response type(String type);

    /**
     * Sets the Content-Type HTTP header and the charset encoding.
     * @param type the MIME type.
     * @param charset the charset encoding
     * @return the current response.
     */
    default Response type(String type, String charset) {
      return type(type + "; charset=" + charset);
    }

    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param o a collection, a map or a stream.
     * @throws IOException if an I/O error occurs.
     */
    void json(Object o) throws IOException;

    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param json a JSON string
     * @throws IOException if an I/O error occurs.
     */
    void json(String json) throws IOException;

    /**
     * Send a Text response.
     * If the status is not defined 200 will be used.
     * If the Content-Type is not defined, "text/html" will be used.
     * @param body the text of the response
     * @throws IOException if an I/O error occurs.
     */
    void send(String body) throws IOException;

    /**
     * Send a file as response.
     * The status is set to 200 if the file is found or 404 if not found.
     * @param path the path of the file;
     * @throws IOException if an I/O error occurs.
     */
    void sendFile(Path path) throws IOException;
  }

  private static String asJSONItem(Object o) {
  	if (o instanceof Map | o instanceof Collection | o instanceof Stream) {
  		return asJSONString(o);
  	}
  	if (o instanceof String) {
  		return "\"" + o + "\"";
  	}
  	return String.valueOf(o);
  }

  private static String asJSONString(Object o) {
  	if (o instanceof Map) {
  		var m = (Map<?,?>) o;
  		return m.entrySet().stream().map(e -> "\"" + e.getKey() + "\": " + asJSONItem(e.getValue())).collect(joining(", ", "{", "}"));
  	}
  	if (o instanceof Collection) {
  		var c = (Collection<?>) o;
  		return asJSONString(c.stream());
  	}
  	if (o instanceof Stream) {
  		var s = (Stream<?>) o;
  		return s.map(JExpress::asJSONItem).collect(joining(", ", "[", "]"));
  	}
  	throw new IllegalArgumentException("unknown type " + o.getClass().getName());
  }

  private static Response response(HttpExchange exchange) {
    return new Response() {
      @Override
      public Response status(int status) {
        exchange.setAttribute("status", status);
        return this;
      }

      @Override
      public Response append(String field, String value) {
        exchange.getResponseHeaders().add(field, value);
        return this;
      }

      @Override
      public Response set(String field, String value) {
        exchange.getResponseHeaders().set(field, value);
        return this;
      }

      @Override
      public Response type(String type) {
        return set("Content-Type", type);
      }

      @Override
      public void json(Object o) throws IOException {
        json(asJSONString(o));
      }

      @Override
      public void json(String json) throws IOException {
        type("application/json", "utf-8");
        send(json);
      }

      @Override
      public void send(String body) throws IOException {
        byte[] content = body.getBytes("UTF8");
        Integer statusAttr = (Integer)exchange.getAttribute("status");
        int status = (statusAttr == null)? 200: statusAttr;
        exchange.sendResponseHeaders(status, content.length);
        Headers headers = exchange.getResponseHeaders();
        if (!headers.containsKey("Content-Type")) {
          type("text/html", "utf-8");
        }
        try(OutputStream output = exchange.getResponseBody()) {
          output.write(content);
        }
      }

      @Override
      public void sendFile(Path path) throws IOException {
        try {
          try(InputStream input = Files.newInputStream(path)) {
            exchange.sendResponseHeaders(200, Files.size(path));
            Headers headers = exchange.getResponseHeaders();
            if (!headers.containsKey("Content-Type")) {
              String contentType = Files.probeContentType(path);
              if (contentType.startsWith("text/")) {
                type(contentType, "utf-8");
              } else {
                type(contentType);
              }
            }

            try(OutputStream output = exchange.getResponseBody()) {
              byte[] buffer = new byte[8192];
              int read;
              while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
              }
            }
          }
        } catch(FileNotFoundException e) {
          String message = "Not Found " + e.getMessage();
          System.err.println(message);
          status(404).send("<html><h2>" + message + "</h2></html>");
        }
      }
    };
  }

  /**
   * A callback called to process a HTTP request in order to
   * create a HTTP response.
   */
  @FunctionalInterface
  public interface Callback {
    /**
     * Called to process a HTTP request in order to create a HTTP response.
     * @param request a HTTP request
     * @param response a HTTP response
     * @throws IOException if an I/O occurs
     */
    public void accept(Request request, Response response) throws IOException;
  }

  private static Function<String, Optional<Map<String, String>>> matcher(String uri) {
    String[] parts = uri.split("/");
    int length = parts.length;
    Predicate<String[]> predicate =  cs -> cs.length >= length;
    BiConsumer<String[], Map<String,String>> consumer = (_1, _2) -> { /* empty */ };
    for(int i = 0; i < length; i++) {
      int index = i;
      String part = parts[i];
      if (part.startsWith(":")) {
        String key = part.substring(1);
        BiConsumer<String[], Map<String,String>> c = consumer;
        consumer = (cs, map) -> { c.accept(cs, map); map.put(key, cs[index]); };
      } else {
        predicate = predicate.and(cs -> part.equals(cs[index]));
      }
    }

    Predicate<String[]> p =  predicate;
    BiConsumer<String[], Map<String,String>> c = consumer;
    return s -> {
      String[] components = s.split("/");
      if (!p.test(components)) {
        return Optional.empty();
      }

      HashMap<String,String> map = new HashMap<>();
      c.accept(components, map);
      return Optional.of(map);
    };
  }

  private JExpress() {
    // empty
  }

  /**
   * Creates an Express like application.
   * @return a new Express like application.
   */
  public static JExpress express() {
    return new JExpress();
  }

  interface Pipeline {
    void accept(HttpExchange exchange) throws IOException;
  }

  private static Pipeline asPipeline(Callback callback) {
    return exchange -> {
    	Response response = response(exchange);
    	try {
    	  callback.accept(request(exchange), response);
    	} catch(RuntimeException e) {
    		e.printStackTrace();
    		response.status(404).type("text/html", "utf-8").send("<html><h2>" + e.getMessage() + "</h2></html>");
    	}
    };
  }

  private Pipeline pipeline = asPipeline((request, response) -> {
    String message = "no match " + request.method() + " " + request.path();
    System.err.println(message);
    response.status(404).type("text/html", "utf-8").send("<html><h2>" + message + "</h2></html>");
  });

  /**
   * Routes an HTTP request if the HTTP method is GET.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void get(String path, Callback callback) {
    method("GET", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is POST.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void post(String path, Callback callback) {
    method("POST", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is PUT.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void put(String path, Callback callback) {
    method("PUT", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is DELETE.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void delete(String path, Callback callback) {
    method("DELETE", path, callback);
  }

  private void method(String method, String path, Callback callback) {
    Pipeline oldPipeline = this.pipeline;
    Function<String, Optional<Map<String, String>>> matcher = matcher(path);
    Pipeline stub = asPipeline(callback);
    this.pipeline = exchange -> {
      exchange.setAttribute("status", null);
      Optional<Map<String,String>> paramsOpt = Optional.of(exchange)
          .filter(req -> exchange.getRequestMethod().equalsIgnoreCase(method))
          .flatMap(matcher.compose(_exchange -> _exchange.getRequestURI().getPath()));
      if (paramsOpt.isPresent()) {
        exchange.setAttribute("params", paramsOpt.get());
        stub.accept(exchange);
      } else {
        oldPipeline.accept(exchange);
      }
    };
  }

  /**
   * Starts a server on the given port and listen for connections.
   * @param port a TCP port
   * @throws IOException if an I/O error occurs.
   */
  public void listen(int port) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", exchange -> {
      try {
        pipeline.accept(exchange);
      //exchange.close();
      } catch(IOException e) {
        e.printStackTrace();
        throw e;
      }
    });
    server.setExecutor(null);
    server.start();
  }



  // ---------------------------------------------------------- //
  //  DO NOT EDIT ABOVE THIS LINE                               //
  // ---------------------------------------------------------- //



  public static void main(String[] args) throws IOException {
  	var random = new Random(0);
  	var tickers = List.of(
  			"AAPL", "GOOG", "INTC", "MSFT", "HPQ", "KO", "WMT", "HOG");

  	var stocks = new HashMap<String,Integer>();
  	tickers.forEach(ticker -> {
  		stocks.put(ticker, 100 + random.nextInt(200));
  	});

    var app = express();
    app.get("/:text", (req, res) -> {
      var text = req.param("text");
      if (text.endsWith(".html")) {
      	out.println("serve html " + text);
        res.type("text/html", "utf-8").sendFile(Paths.get(text));
      } else if (text.endsWith(".js")) {
      	out.println("serve js " + text);
        res.type("text/javascript", "utf-8").sendFile(Paths.get(text));
      } else if (text.endsWith(".json")) {
      	out.println("serve json " + text);
        res.type("application/json", "utf-8").sendFile(Paths.get(text));
      } else {
      	res.status(404).type("text/html", "utf-8").send("<html>unknown resource " + text + "</html>");
      }
    });

    app.get("/api/ticker", (req, res) -> {
    	out.println("get all tickers " + tickers);
    	res.json(tickers);
    });
    app.post("/api/stock", (req, res) -> {
    	tickers.forEach(ticker -> { // 21 because the market always goes up :)
    		stocks.merge(ticker, random.nextInt(21) - 10, Integer::sum);
    	});
    	var tickerOrders = new ArrayList<>(tickers);
    	shuffle(tickerOrders, random);
    	var map = tickerOrders.stream().collect(toMap(ticker -> ticker, stocks::get, Integer::sum, LinkedHashMap::new));
    	out.println("provides stock values " + map);
    	res.json(map);
    });

    app.listen(8080);

    out.println("application started on port 8080");
  }
}