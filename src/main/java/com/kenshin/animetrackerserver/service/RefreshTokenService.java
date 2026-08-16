package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.entity.RefreshToken;
import com.kenshin.animetrackerserver.entity.User;

public interface RefreshTokenService {

    RefreshToken generateRefreshToken(User user);
}
