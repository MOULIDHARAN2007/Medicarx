package com.medicarex.service;

import com.medicarex.auth.JwtUtil;
import com.medicarex.auth.PasswordHasher;
import com.medicarex.model.User;
import com.medicarex.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

public class AuthService {
    private final UserRepository userRepo = new UserRepository();

    public Map<String, Object> authenticate(String email, String password) {
        if (email == null || password == null) return null;

        User user = userRepo.findByEmail(email.trim().toLowerCase());
        if (user == null || !user.isActive()) {
            return null;
        }

        if (!PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash())) {
            return null;
        }

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user.toSafeMap());
        return result;
    }

    public User register(String email, String password, String fullName, String phone, String role) {
        if (userRepo.findByEmail(email) != null) {
            return null;
        }
        String salt = PasswordHasher.generateSalt();
        String passHash = PasswordHasher.hashPassword(password, salt);
        return userRepo.createUser(email, passHash, salt, fullName, phone, role);
    }
}
