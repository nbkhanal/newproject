package io.vertx.blog.first;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.atomic.AtomicInteger;

public class Whisky {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private  int id;

    private String name;

    private String origin;

    private String a;

    public Whisky(int a,String name, String origin) {
        this.id = COUNTER.getAndIncrement();
        this.name = name;
        this.origin = origin;
    }

    public Whisky(String s, String s1) {
        this.id = COUNTER.getAndIncrement();
    }

    public Whisky(JsonObject entries) {
        entries.fieldNames();
    }

    public String getName() {
        return name;
    }

    public String getOrigin() {
        return origin;
    }

    public String getA() {
        return a;
    }

    public int getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setA(String a) {
        this.name = a;
    }


    public void setOrigin(String origin) {
        this.origin = origin;
    }


}