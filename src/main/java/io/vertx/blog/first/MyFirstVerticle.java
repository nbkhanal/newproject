package io.vertx.blog.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.mongo.MongoClient;
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

import static com.mysql.cj.api.xdevapi.DatabaseObject.DbObjectType.COLLECTION;

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
    private void createSomeData(Handler<AsyncResult<Void>> next, Future<Void> fut) {
        Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
        Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
        System.out.println(bowmore.toJson());
        // Do we have data in the collection ?
        mongo.count(COLLECTION, new JsonObject(), count -> {
            if (count.succeeded()) {
                if (count.result() == 0) {
                    // no whiskies, insert data
                    mongo.insert(COLLECTION, bowmore.toJson(), ar -> {
                        if (ar.failed()) {
                            fut.fail(ar.cause());
                        } else {
                            mongo.insert(COLLECTION, talisker.toJson(), ar2 -> {
                                if (ar2.failed()) {
                                    fut.failed();
                                } else {
                                    next.handle(Future.<Void>succeededFuture());
                                }
                            });
                        }
                    });
                } else {
                    next.handle(Future.<Void>succeededFuture());
                }
            } else {
                // report the error
                fut.fail(count.cause());
            }
        });
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
      /*JDBCClient jdbc = JDBCClient.createShared(vertx, config(), "MyWhiskyCollection");*/
      MongoClient mongo = MongoClient.createShared(vertx, config());
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
            mongo.removeOne(COLLECTION, new JsonObject().put("_id", id),
                    ar -> routingContext.response().setStatusCode(204).end());
        }
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

    private void updateOne(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        JsonObject json = routingContext.getBodyAsJson();
        if (id == null || json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            mongo.update(COLLECTION,
                    new JsonObject().put("_id", id), // Select a unique document
                    // The update syntax: {$set, the json object containing the fields to update}
                    new JsonObject()
                            .put("$set", json),
                    v -> {
                        if (v.failed()) {
                            routingContext.response().setStatusCode(404).end();
                        } else {
                            routingContext.response()
                                    .putHeader("content-type", "application/json; charset=utf-8")
                                    .end(Json.encodePrettily(
                                            new Whisky(id, json.getString("name"),
                                                    json.getString("origin"))));
                        }
                    });
        }
    }

    private void getAll(RoutingContext routingContext) {
        mongo.find(COLLECTION, new JsonObject(), results -> {
            List<JsonObject> objects = results.result();
            List<Whisky> whiskies = objects.stream().map(Whisky::new).collect(Collectors.toList());
            routingContext.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(whiskies));
        });
    }

}
