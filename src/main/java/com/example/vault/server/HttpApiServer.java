package com.example.vault.server;

import com.example.vault.cli.Commands;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpApiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("VAULT_API_PORT", "8080"));
        Path dataDir = Path.of(System.getenv().getOrDefault("VAULT_DATA_DIR", "/data"));
        Files.createDirectories(dataDir);
        Path storePath = dataDir.resolve("secrets.properties");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/api/put", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            String secret = requiredText(request, "secret");
            Commands commands = commandsFor(storePath, request);
            commands.put(writeTempFile(requiredText(request, "certificatePem"), ".pem"), path, secret);
            return Map.of("status", "ok");
        }));

        server.createContext("/api/get", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            Commands commands = commandsFor(storePath, request);
            String secret = commands.get(writeTempFile(requiredText(request, "certificatePem"), ".pem"), path);
            return Map.of("status", "ok", "secret", secret);
        }));

        server.createContext("/api/list", exchange -> withJson(exchange, request -> {
            String prefix = request.path("prefix").asText("");
            Commands commands = commandsFor(storePath, request);
            List<String> secrets = commands.list(writeTempFile(requiredText(request, "certificatePem"), ".pem"), prefix);
            return Map.of("status", "ok", "items", secrets);
        }));

        server.createContext("/api/delete", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            Commands commands = commandsFor(storePath, request);
            commands.delete(writeTempFile(requiredText(request, "certificatePem"), ".pem"), path);
            return Map.of("status", "ok");
        }));

        server.createContext("/api/health", exchange -> {
            addCors(exchange);
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            sendJson(exchange, 200, Map.of("status", "up"));
        });

        server.start();
        System.out.println("Vault API server listening on port " + port);
    }

    private static void withJson(HttpExchange exchange, JsonHandler handler) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try (InputStream inputStream = exchange.getRequestBody()) {
            JsonNode request = OBJECT_MAPPER.readTree(inputStream);
            Map<String, Object> response = handler.handle(request);
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (SecurityException exception) {
            sendJson(exchange, 403, Map.of("error", exception.getMessage()));
        } catch (GeneralSecurityException exception) {
            sendJson(exchange, 500, Map.of("error", "Security operation failed", "detail", exception.getMessage()));
        } catch (Exception exception) {
            sendJson(exchange, 500, Map.of("error", "Server error", "detail", exception.getMessage()));
        }
    }

    private static Commands commandsFor(Path storePath, JsonNode request) throws IOException, GeneralSecurityException {
        char[] passphrase = requiredText(request, "passphrase").toCharArray();
        String policiesJson = requiredText(request, "policiesJson");
        Path policyPath = writeTempFile(policiesJson, ".json");
        return Commands.create(storePath, policyPath, passphrase);
    }

    private static String requiredText(JsonNode request, String field) {
        String value = request.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static Path writeTempFile(String content, String suffix) throws IOException {
        Path path = Files.createTempFile("vault-ui-", suffix);
        Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
        path.toFile().deleteOnExit();
        return path;
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> payload) throws IOException {
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    }

    @FunctionalInterface
    private interface JsonHandler {
        Map<String, Object> handle(JsonNode request) throws Exception;
    }
}
