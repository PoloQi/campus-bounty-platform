package com.campus.bounty.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSecurityTest {
    @Test
    void database_credentials_and_jwt_secret_must_be_supplied_externally() throws IOException {
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(configuration.contains("url: ${DB_URL}"));
        assertTrue(configuration.contains("username: ${DB_USERNAME}"));
        assertTrue(configuration.contains("password: ${DB_PASSWORD}"));
        assertTrue(configuration.contains("secret: ${JWT_SECRET}"));
    }
}
