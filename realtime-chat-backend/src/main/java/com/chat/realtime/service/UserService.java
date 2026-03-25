package com.chat.realtime.service;

import com.chat.realtime.model.User;
import com.chat.realtime.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .status(User.Status.OFFLINE)
                .build();
        return userRepository.save(user);
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
        user.setStatus(User.Status.ONLINE);
        user.setLastSeen(LocalDateTime.now());
        return userRepository.save(user);
    }
    
    public void updateUserStatus(String username, User.Status status) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setStatus(status);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
