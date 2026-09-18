package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.entity.User;

public interface JwtService {

    String generateAccessToken(User user);

    Long extractUserId(String token);

}
