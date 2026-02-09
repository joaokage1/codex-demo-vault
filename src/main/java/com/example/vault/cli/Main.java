package com.example.vault.cli;

import com.example.vault.SecretManager;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        SecretManager manager = new SecretManager();
        String command = args[0];
        try {
            switch (command) {
                case "add" -> handleAdd(manager, args);
                case "get" -> handleGet(manager, args);
                default -> {
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            System.exit(2);
        }
    }

    private static void handleAdd(SecretManager manager, String[] args) throws Exception {
        if (args.length != 5) {
            printUsage();
            System.exit(1);
        }
        Path storePath = Path.of(args[1]);
        String alias = args[2];
        String secret = args[3];
        Path certificatePath = Path.of(args[4]);

        manager.addSecret(storePath, alias, secret, certificatePath);
        System.out.println("Secret stored for alias: " + alias);
    }

    private static void handleGet(SecretManager manager, String[] args) throws Exception {
        if (args.length != 6) {
            printUsage();
            System.exit(1);
        }
        Path storePath = Path.of(args[1]);
        String alias = args[2];
        Path keyStorePath = Path.of(args[3]);
        char[] keyStorePassword = args[4].toCharArray();
        String keyAlias = args[5];

        String secret = manager.getSecret(storePath, alias, keyStorePath, keyStorePassword, keyAlias);
        System.out.println(secret);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  add <storePath> <alias> <secret> <certificatePath>");
        System.out.println("  get <storePath> <alias> <keyStorePath> <keyStorePassword> <keyAlias>");
    }
}
