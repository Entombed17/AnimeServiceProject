package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.entity.User;
import org.springframework.stereotype.Service;

@Service
public interface JwtService {

    String generateAccessToken(User user);

    String extractEmail(String token);

    boolean isTokenValid(String token, User user);
}
