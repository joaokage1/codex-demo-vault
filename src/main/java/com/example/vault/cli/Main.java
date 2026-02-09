package com.example.vault.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String command = args[0];
        switch (command) {
            case "put" -> handlePut(args);
            case "get" -> handleGet(args);
            case "delete" -> handleDelete(args);
            case "list" -> handleList(args);
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void handlePut(String[] args) throws IOException, GeneralSecurityException {
        if (args.length < 7) {
            printUsage();
            return;
        }
        Path storePath = Path.of(args[1]);
        String secretPath = args[2];
        String secret = args[3];
        if ("-".equals(secret)) {
            secret = readStdin();
        }
        char[] passphrase = args[4].toCharArray();
        Path certificatePath = Path.of(args[5]);
        Path policiesPath = Path.of(args[6]);

        Commands commands = Commands.create(storePath, policiesPath, passphrase);
        commands.put(certificatePath, secretPath, secret);
    }

    private static void handleGet(String[] args) throws IOException, GeneralSecurityException {
        if (args.length < 6) {
            printUsage();
            return;
        }
        Path storePath = Path.of(args[1]);
        String secretPath = args[2];
        char[] passphrase = args[3].toCharArray();
        Path certificatePath = Path.of(args[4]);
        Path policiesPath = Path.of(args[5]);

        Commands commands = Commands.create(storePath, policiesPath, passphrase);
        String secret = commands.get(certificatePath, secretPath);
        System.out.println(secret);
    }

    private static void handleDelete(String[] args) throws IOException {
        if (args.length < 6) {
            printUsage();
            return;
        }
        Path storePath = Path.of(args[1]);
        String secretPath = args[2];
        char[] passphrase = args[3].toCharArray();
        Path certificatePath = Path.of(args[4]);
        Path policiesPath = Path.of(args[5]);

        try {
            Commands commands = Commands.create(storePath, policiesPath, passphrase);
            commands.delete(certificatePath, secretPath);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to initialize crypto", exception);
        }
    }

    private static void handleList(String[] args) throws IOException {
        if (args.length < 6) {
            printUsage();
            return;
        }
        Path storePath = Path.of(args[1]);
        String prefix = args[2];
        char[] passphrase = args[3].toCharArray();
        Path certificatePath = Path.of(args[4]);
        Path policiesPath = Path.of(args[5]);

        try {
            Commands commands = Commands.create(storePath, policiesPath, passphrase);
            List<String> paths = commands.list(certificatePath, prefix);
            for (String path : paths) {
                System.out.println(path);
            }
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to initialize crypto", exception);
        }
    }

    private static String readStdin() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = System.in.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8).trim();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  put <store> <path> <value|-> <passphrase> <cert> <policies>");
        System.out.println("  get <store> <path> <passphrase> <cert> <policies>");
        System.out.println("  delete <store> <path> <passphrase> <cert> <policies>");
        System.out.println("  list <store> <prefix> <passphrase> <cert> <policies>");
    }
}
