package com.example.users.domain.model;

import java.util.regex.Pattern;

public class User {
    private final Long id;
    private final String name;
    private final String email;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public User(Long id, String name, String email) {
        validateName(name);
        validateEmail(email);
        this.id = id;
        this.name = name;
        this.email = email;
    }

    private void validateName(String name) {
        if (name == null || name.length() < 2) {
            throw new IllegalArgumentException("Name must be at least 2 characters long");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
