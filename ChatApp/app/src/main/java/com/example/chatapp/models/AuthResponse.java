package com.example.chatapp.models;

/**
 * Matches the backend UserController.UserDto response: {"id": 1, "username": "foo"}
 * Both /api/auth/register and /api/auth/login return this shape.
 */
public class AuthResponse {
    private Long id;
    private String username;

    public AuthResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
