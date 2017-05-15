package com.geekfactor.simpleblog;

import java.sql.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import java.net.URI;
import java.net.URISyntaxException;

// encryption
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import org.apache.commons.codec.binary.Hex;

// Spark
import static spark.Spark.*;
import spark.template.freemarker.FreeMarkerEngine;
import spark.ModelAndView;

public class Main {

  // from http://stackoverflow.com/questions/6312544/hmac-sha1-how-to-do-it-properly-in-java
  // discussion on keys: http://security.stackexchange.com/questions/95972/what-are-requirements-for-hmac-secret-key
  // how I created a sample key
  // # dd if=/dev/urandom of=key.bin count=16 bs=1
  // # base64 key.bin
  public static String hmacSha1(String value, String key) {
    try {
      // get an hmac_sha1 key from the raw bytes
      byte[] keyBytes = key.getBytes();
      SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");

      // initialise hmac_sha1 instance with signing key
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(signingKey);
      
      // compute the hmac on input data bytes
      byte[] rawHmac = mac.doFinal(value.getBytes());

      // convert raw bytes to hex
      byte[] hexBytes = new Hex().encode(rawHmac);

      // convert hex array to string
      return new String(hexBytes, "UTF-8");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {

    // get database host[:port] from environment    
    String pghost = System.getenv("PG_HOST");
    if (pghost == null) {
      pghost = "localhost";
    }

    System.out.println("Using " + pghost + " as PG host");

    // not really sure what this does
    staticFileLocation("/public");

    // database connection string
    String dbUrl = "jdbc:postgresql://" + pghost + "/blog?user=blog&password=bloggie";

    // retrieve blog articles
    get("/blog", (request, response) -> {
      // initialization
      Connection connection = null;

      // here's where I put the output
      Map<String, Object> attr = new HashMap<>();

      try {
        // set up database connection
        connection = DriverManager.getConnection(dbUrl);

        // create reusable statmement object
        Statement stmt = connection.createStatement();

        // get articles
        ResultSet res = stmt.executeQuery("SELECT title, name, posted, content FROM articles, users WHERE articles.author = users.id");

        ArrayList<String> output = new ArrayList<String>();
        while (res.next()) {
          output.add("<h1>" + res.getString("title") + "</h1><h2>by " + res.getString("name") + " (" + res.getTimestamp("posted") + ")</h2>" + res.getString("content") + "<hr noshade='true'/>");
        }
        attr.put("articles", output);
        return new ModelAndView(attr, "articles.ftl");
      } catch (Exception e) {
        attr.put("message", "There was an error: " + e);
        return new ModelAndView(attr, "error.ftl");
      } finally {
        if (connection != null)
          try {
            connection.close();
          }
          catch (SQLException e) {
          }
      }
    }, new FreeMarkerEngine());

    post("/blog", (request, response) -> {
      // initialization
      Connection connection = null;

      try {
        // set up database connection
        connection = DriverManager.getConnection(dbUrl);

        // get username from request
        String username = request.queryParams("user");

        // get secret key from DB
        PreparedStatement stmt1 = connection.prepareStatement(
          "SELECT id, key FROM users WHERE login=?");
        stmt1.setString(1, username);
        ResultSet res = stmt1.executeQuery();
        if (!res.next())
          throw new RuntimeException("No such user: " + username);
        
        // should only have what I need
        int author = res.getInt("id");
        String key = res.getString("key");

        // get stuff from request
        // leaving content out of it for now because it's possibly interfering
        String requestDigest = "" + request.queryParams("user") + request.queryParams("title") + request.queryParams("content");

        // hash digest with key matching purported author
        String serversideHash = hmacSha1(requestDigest, key);

        // compare against client-supplied hash
        if (!serversideHash.equals(request.queryParams("token")))
          throw new RuntimeException("No such user or not authorized (user " + username + ", hash " + serversideHash + ", token " + request.queryParams("token") + ") digest=(" + requestDigest + ")");

        // create new object
        PreparedStatement stmt2 = connection.prepareStatement(
          "INSERT INTO articles (title, author, content) VALUES (?, ?, ?)");
        stmt2.setString(1, request.queryParams("title"));
        stmt2.setInt(2, author);
        stmt2.setString(3, request.queryParams("content"));
        stmt2.executeUpdate();

      } catch (Exception e) {
        return "There was an error: " + e;
      } finally {
        if (connection != null)
          try {
            connection.close();
          }
          catch (SQLException e) {
          }
      }
      return "OK";
    });
  }
}
