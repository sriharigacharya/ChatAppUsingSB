package com.chat.realtime.controller;

import com.chat.realtime.model.User;
import com.chat.realtime.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody AuthRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new UserDto(user.getId(), user.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@RequestBody AuthRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new UserDto(user.getId(), user.getUsername()));
    }

    @Data
    public static class AuthRequest {
        private String username;
        private String password;
    }

    @Data
    public static class UserDto {
        private final Long id;
        private final String username;
    }
}
