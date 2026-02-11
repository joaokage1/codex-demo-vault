package com.example.vault.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PolicyService {
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("\"fingerprint\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern READ_PATTERN = Pattern.compile("\"read\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern WRITE_PATTERN = Pattern.compile("\"write\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    private final Map<String, Policy> policies;

    public PolicyService(Path policiesPath) throws IOException {
        Objects.requireNonNull(policiesPath, "policiesPath");
        if (!Files.exists(policiesPath)) {
            this.policies = Collections.emptyMap();
        } else {
            String raw = Files.readString(policiesPath);
            this.policies = parsePolicies(raw);
        }
    }

    public boolean canRead(String fingerprint, String path) {
        Policy policy = policies.get(fingerprint);
        return policy != null && policy.matchesRead(path);
    }

    public boolean canWrite(String fingerprint, String path) {
        Policy policy = policies.get(fingerprint);
        return policy != null && policy.matchesWrite(path);
    }

    private Map<String, Policy> parsePolicies(String raw) {
        List<String> objects = extractObjects(raw);
        Map<String, Policy> parsed = new HashMap<>();
        for (String object : objects) {
            Matcher fingerprintMatcher = FINGERPRINT_PATTERN.matcher(object);
            if (!fingerprintMatcher.find()) {
                continue;
            }
            String fingerprint = fingerprintMatcher.group(1).trim();
            Set<String> read = extractPermissions(object, READ_PATTERN);
            Set<String> write = extractPermissions(object, WRITE_PATTERN);
            parsed.put(fingerprint, new Policy(read, write));
        }
        return parsed;
    }

    private Set<String> extractPermissions(String object, Pattern pattern) {
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            return Collections.emptySet();
        }
        return parseStringArray(matcher.group(1));
    }

    private Set<String> parseStringArray(String content) {
        Set<String> values = new HashSet<>();
        for (String token : content.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("\"")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private List<String> extractObjects(String raw) {
        String trimmed = raw.trim();
        List<String> objects = new ArrayList<>();
        if (trimmed.isEmpty()) {
            return objects;
        }
        int braceDepth = 0;
        int start = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char value = trimmed.charAt(i);
            if (value == '{') {
                if (braceDepth == 0) {
                    start = i;
                }
                braceDepth++;
            } else if (value == '}') {
                braceDepth--;
                if (braceDepth == 0 && start >= 0) {
                    objects.add(trimmed.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static class Policy {
        private final Set<String> read;
        private final Set<String> write;

        private Policy(Set<String> read, Set<String> write) {
            this.read = read;
            this.write = write;
        }

        private boolean matchesRead(String path) {
            return matchesAny(read, path);
        }

        private boolean matchesWrite(String path) {
            return matchesAny(write, path);
        }

        private boolean matchesAny(Set<String> patterns, String path) {
            for (String pattern : patterns) {
                if (matchesPattern(pattern, path)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesPattern(String pattern, String path) {
            StringBuilder regex = new StringBuilder();
            regex.append('^');
            for (char value : pattern.toCharArray()) {
                if (value == '*') {
                    regex.append(".*");
                } else if (".[]{}()\\^$|+?".indexOf(value) >= 0) {
                    regex.append('\\').append(value);
                } else {
                    regex.append(value);
                }
            }
            regex.append('$');
            return path.matches(regex.toString());
        }
    }
}
