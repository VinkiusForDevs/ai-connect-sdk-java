/*
 * Copyright (c) Vinkius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vinkius.connect.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Exercises the default JDK-backed sender against a real loopback HTTP server. */
class JdkHttpSenderTest {

  private static HttpServer startServer() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.start();
    return server;
  }

  private static String baseUrl(HttpServer server) {
    String host = server.getAddress().getAddress().getHostAddress();
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    return "http://" + host + ":" + server.getAddress().getPort();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    if (body.isEmpty()) {
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
      return;
    }
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }

  @Test
  void getWithoutBodyFlattensResponseHeadersToLowerCase() throws IOException {
    HttpServer server = startServer();
    try {
      server.createContext(
          "/echo",
          exchange -> {
            String seen =
                exchange.getRequestMethod()
                    + "|"
                    + exchange.getRequestHeaders().getFirst("X-Custom");
            exchange.getResponseHeaders().set("X-Reply", "yes");
            respond(exchange, 200, seen);
          });
      JdkHttpSender sender = new JdkHttpSender();

      HttpSender.Response response =
          sender
              .send(
                  new HttpSender.Request(
                      baseUrl(server) + "/echo",
                      "GET",
                      Map.of("x-custom", "abc"),
                      null,
                      5000,
                      null))
              .join();

      assertThat(response.status()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("GET|abc");
      assertThat(response.headers()).containsEntry("x-reply", "yes");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void postWithStringBodyReachesTheServer() throws IOException {
    HttpServer server = startServer();
    try {
      server.createContext(
          "/echo",
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 201, exchange.getRequestMethod() + ":" + body);
          });
      JdkHttpSender sender = new JdkHttpSender();

      HttpSender.Response response =
          sender
              .send(
                  new HttpSender.Request(
                      baseUrl(server) + "/echo",
                      "POST",
                      Map.of("content-type", "application/json"),
                      "{\"a\":1}",
                      5000,
                      null))
              .join();

      assertThat(response.status()).isEqualTo(201);
      assertThat(response.body()).isEqualTo("POST:{\"a\":1}");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void returnsNonSuccessStatusesWithoutThrowing() throws IOException {
    HttpServer server = startServer();
    try {
      server.createContext("/flaky", exchange -> respond(exchange, 503, "try later"));
      JdkHttpSender sender = new JdkHttpSender();

      HttpSender.Response response =
          sender
              .send(
                  new HttpSender.Request(
                      baseUrl(server) + "/flaky", "GET", Map.of(), null, 5000, null))
              .join();

      assertThat(response.status()).isEqualTo(503);
      assertThat(response.body()).isEqualTo("try later");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void emptyResponseBodyBecomesEmptyString() throws IOException {
    HttpServer server = startServer();
    try {
      server.createContext("/empty", exchange -> respond(exchange, 200, ""));
      JdkHttpSender sender = new JdkHttpSender();

      HttpSender.Response response =
          sender
              .send(
                  new HttpSender.Request(
                      baseUrl(server) + "/empty", "GET", Map.of(), null, 5000, null))
              .join();

      assertThat(response.status()).isEqualTo(200);
      assertThat(response.body()).isEmpty();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void invalidUrlFailsTheFutureInsteadOfThrowing() {
    JdkHttpSender sender = new JdkHttpSender();

    assertThatThrownBy(
            () ->
                sender
                    .send(new HttpSender.Request("not a url", "GET", Map.of(), null, 1000, null))
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }
}
