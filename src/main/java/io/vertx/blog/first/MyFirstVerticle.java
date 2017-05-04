package io.vertx.blog.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MyFirstVerticle extends AbstractVerticle {

    /*JDBCClient jdbc = JDBCClient.createShared(vertx, config(), "MyWhiskyCollection");*/
    // Store our product
    private Map<Integer, Whisky> products = new LinkedHashMap<>();
    // Create some product
    /*private void createSomeData() {
        Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
        products.put(bowmore.getId(), bowmore);
        Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
        products.put(talisker.getId(), talisker);
    }*/
    private void insert(Whisky whisky, SQLConnection connection, Handler<AsyncResult<Whisky>> next) {
        String sql = "INSERT INTO Whisky (name, origin) VALUES ?, ?";
        connection.updateWithParams(sql,
                new JsonArray().add(whisky.getName()).add(whisky.getOrigin()),
                (ar) -> {
                    if (ar.failed()) {
                        next.handle(Future.failedFuture(ar.cause()));
                        return;
                    }
                    UpdateResult result = ar.result();
                    // Build a new whisky instance with the generated id.
                    Whisky w = new Whisky(result.getKeys().getInteger(0), whisky.getName(), whisky.getOrigin());
                    next.handle(Future.succeededFuture(w));
                });
    }
    private void createSomeData(AsyncResult<SQLConnection> result,
                                Handler<AsyncResult<Void>> next, Future<Void> fut) {
        if (result.failed()) {
            fut.fail(result.cause());
        } else {
            SQLConnection connection = result.result();
            connection.execute(
                    "CREATE TABLE IF NOT EXISTS Whisky (id INTEGER IDENTITY, name varchar(100), " +
                            "origin varchar(100))",
                    ar -> {
                        if (ar.failed()) {
                            fut.fail(ar.cause());
                            connection.close();
                            return;
                        }
                        connection.query("SELECT * FROM Whisky", select -> {
                            if (select.failed()) {
                                fut.fail(ar.cause());
                                connection.close();
                                return;
                            }
                            if (select.result().getNumRows() == 0) {
                                insert(
                                        new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay"),
                                        connection,
                                        (v) -> insert(new Whisky("Talisker 57° North", "Scotland, Island"),
                                                connection,
                                                (r) -> {
                                                    next.handle(Future.<Void>succeededFuture());
                                                    connection.close();
                                                }));
                            } else {
                                next.handle(Future.<Void>succeededFuture());
                                connection.close();
                            }
                        });
                    });
        }
    }

    private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) {
        io.vertx.ext.jdbc.JDBCClient jdbc = new io.vertx.ext.jdbc.JDBCClient() {
            @Override
            public io.vertx.ext.jdbc.JDBCClient getConnection(Handler<AsyncResult<SQLConnection>> handler) {
                return null;
            }

            @Override
            public void close() {

            }
        };
        jdbc.getConnection(ar -> {
            if (ar.failed()) {
                fut.fail(ar.cause());
            } else {
                next.handle(Future.succeededFuture(ar.result()));
            }
        });
    }
  @Override
    public void start(Future<Void> fut) {


     // Create a router object.
     Router router = Router.router(vertx);
      JsonObject config = new JsonObject()
              .put("url", "jdbc:mysql://localhost/MyWhiskyCollection?"
                      + "user=root&password=root")
              .put("driver_class", "com.mysql.jdbc.Driver")
              .put("max_pool_size", 30);
      JDBCClient jdbc = JDBCClient.createShared(vertx, config(), "MyWhiskyCollection");
      // Bind "/" to our hello message - so we are still compatible.
      router.route("/").handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        response
       .putHeader("content-type", "text/html")
       .end("<h1>Hello from my first Vert.x 3 application</h1>");
         });

      // Serve static resources from the /assets directory
      router.route("/assets/*").handler(StaticHandler.create("assets"));
      router.get("/api/whiskies").handler(this::getAll);
      router.route("/api/whiskies*").handler(BodyHandler.create());
      router.post("/api/whiskies").handler(this::addOne);
      router.delete("/api/whiskies/:id").handler(this::deleteOne);

 // Create the HTTP server and pass the "accept" method to the request handler.
 vertx
     .createHttpServer()
     .requestHandler(router::accept)
     .listen(
         // Retrieve the port from the configuration,
         // default to 8080.
         config().getInteger("http.port", 8080),
         result -> {
           if (result.succeeded()) {
             fut.complete();
           } else {
             fut.fail(result.cause());
           }
         }
     );
}

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            Integer idAsInteger = Integer.valueOf(id);
            products.remove(idAsInteger);
        }
        routingContext.response().setStatusCode(204).end();
    }

    private void addOne(RoutingContext routingContext) {
        final Whisky whisky = Json.decodeValue(routingContext.getBodyAsString(),
                Whisky.class);
        products.put(whisky.getId(), whisky);
        routingContext.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(whisky));
    }

    private void getAll(RoutingContext routingContext) {
        io.vertx.ext.jdbc.JDBCClient jdbc = new io.vertx.ext.jdbc.JDBCClient() {
            @Override
            public io.vertx.ext.jdbc.JDBCClient getConnection(Handler<AsyncResult<SQLConnection>> handler) {
                return null;
            }

            @Override
            public void close() {

            }
        };
        jdbc.getConnection(ar -> {
            SQLConnection connection = ar.result();
            connection.query("SELECT * FROM Whisky", result -> {
                List<Whisky> whiskies = result.result().getRows().stream().map(Whisky::new).collect(Collectors.toList());
                routingContext.response()
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(whiskies));
                connection.close(); // Close the connection
            });
        });
    }

}
