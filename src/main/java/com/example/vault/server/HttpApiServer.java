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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpApiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("VAULT_API_PORT", "8080"));
        Path dataDir = Path.of(System.getenv().getOrDefault("VAULT_DATA_DIR", "/data"));
        Path configDir = Path.of(System.getenv().getOrDefault("VAULT_CONFIG_DIR", "/config"));
        Files.createDirectories(dataDir);

        Path storePath = dataDir.resolve("secrets.properties");
        RuntimeConfig config = RuntimeConfig.load(configDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/api/put", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            String secret = requiredText(request, "secret");
            Commands commands = Commands.create(storePath, config.policiesPath(), config.passphrase());
            commands.put(config.certificatePath(), path, secret);
            return Map.of("status", "ok");
        }));

        server.createContext("/api/get", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            Commands commands = Commands.create(storePath, config.policiesPath(), config.passphrase());
            String secret = commands.get(config.certificatePath(), path);
            return Map.of("status", "ok", "secret", secret);
        }));

        server.createContext("/api/list", exchange -> withJson(exchange, request -> {
            String prefix = request.path("prefix").asText("");
            Commands commands = Commands.create(storePath, config.policiesPath(), config.passphrase());
            List<String> secrets = commands.list(config.certificatePath(), prefix);
            return Map.of("status", "ok", "items", secrets);
        }));

        server.createContext("/api/delete", exchange -> withJson(exchange, request -> {
            String path = requiredText(request, "path");
            Commands commands = Commands.create(storePath, config.policiesPath(), config.passphrase());
            commands.delete(config.certificatePath(), path);
            return Map.of("status", "ok");
        }));

        server.createContext("/api/health", exchange -> {
            addCors(exchange);
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            sendJson(exchange, 200, Map.of(
                    "status", "up",
                    "certificatePath", config.certificatePath().toString(),
                    "policiesPath", config.policiesPath().toString()));
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

    private static String requiredText(JsonNode request, String field) {
        String value = request.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
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

    private static Path findRequired(Path explicitPath, Path directory, String glob, String label) throws IOException {
        if (Files.exists(explicitPath)) {
            return explicitPath;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        throw new IllegalArgumentException("Unable to find " + label + ". Set explicit env var or add matching file under "
                + directory + " with glob " + glob);
    }

    private record RuntimeConfig(Path certificatePath, Path policiesPath, char[] passphrase) {
        static RuntimeConfig load(Path configDir) throws IOException {
            Files.createDirectories(configDir);
            Path certPath = findRequired(
                    Path.of(System.getenv().getOrDefault("VAULT_CERT_PATH", configDir.resolve("client-cert.pem").toString())),
                    configDir,
                    "*.pem",
                    "certificate PEM");
            Path policiesPath = findRequired(
                    Path.of(System.getenv().getOrDefault("VAULT_POLICIES_PATH", configDir.resolve("policies.json").toString())),
                    configDir,
                    "*.json",
                    "policies JSON");
            String passphrase = System.getenv().getOrDefault("VAULT_UNSEAL_PASSPHRASE", "");
            if (passphrase.isBlank()) {
                throw new IllegalArgumentException("Missing VAULT_UNSEAL_PASSPHRASE environment variable");
            }
            return new RuntimeConfig(certPath, policiesPath, passphrase.toCharArray());
        }
    }

    @FunctionalInterface
    private interface JsonHandler {
        Map<String, Object> handle(JsonNode request) throws Exception;
    }
}
