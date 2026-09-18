# Доработка авторизации: refresh + logout

Это не документация проекта, а рабочая заметка с готовым кодом для доработки auth-модуля.
Ничего в `src/` не менялось — переносить фрагменты в проект нужно руками, по одному, с проверкой (`.\mvnw.cmd test`).

Сверялся с твоими же черновиками в `Service.MD` — сигнатуры `refresh(RefreshTokenRequest)` /
`logout(RefreshTokenRequest)` там уже намечены, ниже — их реализация вместе с недостающими
кусками (`RefreshTokenService`, контроллер, фикс фильтра).

## Порядок работ

1. **Обязательно перед всем остальным** — фикс `JwtAuthenticationFilter` (см. ниже). Без него
   `/auth/refresh` будет 500-ить в реальном сценарии использования.
2. `RefreshTokenService` — добавить `validateRefreshToken` и `revokeRefreshToken`.
3. `AuthService` / `AuthServiceImpl` — реализовать `refresh` и `logout`.
4. `AuthController` — добавить эндпоинты `/auth/refresh` и `/auth/logout`.
5. (опционально, но желательно) — минимальный `@RestControllerAdvice`, чтобы ошибки refresh/logout
   отдавались как 400/401, а не как 500.

---

## 1. Баг в `JwtAuthenticationFilter` (нужно исправить обязательно)

Сейчас:

```java
String token = authHeader.substring(7);
String email = jwtService.extractEmail(token);
```

`extractEmail` парсит JWT без try/catch. JJWT бросает `ExpiredJwtException` /
`MalformedJwtException` / `SignatureException` (все — подтипы `io.jsonwebtoken.JwtException`)
прямо во время парсинга — то есть на **истёкшем** или **битом** токене вызов упадёт с
исключением, которое никто не ловит. Обработчика ошибок в проекте нет, поэтому Spring
отдаёт голый 500 вместо ожидаемого "не аутентифицирован".

Это прямо бьёт по `refresh`: клиенты обычно шлют сохранённый access-токен на все запросы
по привычке (через общий interceptor/axios-defaults), включая `/auth/refresh` — а именно
туда чаще всего идут с **уже истёкшим** access-токеном. Итог: `/auth/refresh` ловит 500
вместо того, чтобы просто игнорировать протухший токен и отработать через тело запроса.

Фикс — оборачиваем парсинг в try/catch и **не аутентифицируем** запрос при ошибке,
остальное решит `SecurityConfig` (permitAll для `/auth/**`, 401 для остального):

```java
package com.kenshin.animetrackerserver.security;

import com.kenshin.animetrackerserver.entity.User;
import com.kenshin.animetrackerserver.repository.UserRepository;
import com.kenshin.animetrackerserver.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null && jwtService.isTokenValid(token, user)) {
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        user, null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException e) {
            // истёкший/битый/подделанный access-токен — просто не аутентифицируем запрос
        }

        filterChain.doFilter(request, response);
    }
}
```

Заодно ушёл неиспользуемый импорт `java.net.http.HttpRequest`, который был в оригинале.

---

## 2. `RefreshTokenService` — валидация и отзыв токена

Интерфейс:

```java
package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.entity.RefreshToken;
import com.kenshin.animetrackerserver.entity.User;

public interface RefreshTokenService {

    RefreshToken generateRefreshToken(User user);

    RefreshToken validateRefreshToken(String token);

    void revokeRefreshToken(String token);
}
```

Реализация (репозиторий `RefreshTokenRepository.findByToken` уже существует — им и пользуемся):

```java
@Override
public RefreshToken validateRefreshToken(String token) {
    RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

    if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
        refreshTokenRepository.delete(refreshToken);
        throw new RuntimeException("Refresh token expired");
    }

    return refreshToken;
}

@Override
public void revokeRefreshToken(String token) {
    refreshTokenRepository.findByToken(token)
            .ifPresent(refreshTokenRepository::delete);
}
```

`revokeRefreshToken` сделан идемпотентным (нет токена — просто no-op), чтобы повторный
logout или logout с уже истёкшим токеном не падал с ошибкой.

---

## 3. `AuthService` / `AuthServiceImpl`

Интерфейс (ровно то, что у тебя уже наброшено в `Service.MD`):

```java
public interface AuthService {
    RegisterAndAuthResponse register(RegisterRequest request);
    RegisterAndAuthResponse login(LoginRequest request);
    AccessTokenResponse refresh(RefreshTokenRequest request);
    void logout(RefreshTokenRequest request);
}
```

Реализация:

```java
@Override
public AccessTokenResponse refresh(RefreshTokenRequest request) {
    RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());

    String accessToken = jwtService.generateAccessToken(refreshToken.getUser());

    AccessTokenResponse response = new AccessTokenResponse();
    response.setAccessToken(accessToken);
    return response;
}

@Override
public void logout(RefreshTokenRequest request) {
    refreshTokenService.revokeRefreshToken(request.getRefreshToken());
}
```

`AccessTokenResponse` сейчас имеет только `@Getter/@Setter`, конструктора нет — поэтому
выше собран через `new` + `setAccessToken`. Если хочешь как в `RegisterAndAuthResponse`,
просто добавь `@AllArgsConstructor` к `AccessTokenResponse` и верни
`new AccessTokenResponse(accessToken)`.

**Про ротацию refresh-токена**: сознательно не делаю. Твоя модель — один живой refresh-токен
на пользователя (`@OneToOne`, строка переиспользуется при логине). При `refresh` можно
дополнительно перевыпускать и refresh-токен (`refreshTokenService.generateRefreshToken(user)`),
это безопаснее (короче окно на replay при утечке), но тогда `AccessTokenResponse` должен
уносить и новый `refreshToken`, а не только `accessToken` — это меняет контракт, который у
тебя уже зафиксирован в `Service.MD`. Оставляю как есть сейчас, ротацию можно добавить потом
отдельным изменением, если понадобится.

---

## 4. `AuthController` — эндпоинты

```java
@PostMapping("/refresh")
public AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return authService.refresh(request);
}

@PostMapping("/logout")
public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request);
    return ResponseEntity.noContent().build();
}
```

Оба эндпоинта identифицируют пользователя через refresh-токен из тела запроса, а не через
access-токен из заголовка — специально, по двум причинам:

- `/auth/refresh` вызывают именно тогда, когда access-токен уже истёк — требовать его же
  для аутентификации самого себя было бы противоречием.
- `/auth/logout` через `@AuthenticationPrincipal User user` работал бы только если
  `SecurityConfig` требовал бы авторизацию на этом пути, а сейчас весь `/auth/**` —
  `permitAll`. Если access-токен не пришёл (или уже истёк), principal будет `null`, и без
  дополнительной проверки это NPE. Схема "identify by refresh token" проще и не завязана
  на состояние access-токена.

`SecurityConfig` менять не нужно — `/auth/**` уже `permitAll`, оба новых пути подпадают
автоматически.

---

## 5. (опционально) Обработка ошибок для этих ручек

Сейчас любая ошибка в сервисе — это `throw new RuntimeException(...)`, и без
`@RestControllerAdvice` она превращается в голый 500. Для `refresh`/`logout` это особенно
заметно: неверный/просроченный refresh-токен должен быть 401, а не 500. Минимальный вариант,
не переusaживая архитектуру целиком:

```java
package com.kenshin.animetrackerserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }
}
```

Это грубый инструмент (всё летит в 400), но он уже лучше, чем сплошные 500. Полноценное
решение — завести отдельные исключения (`EmailAlreadyExistsException` → 409,
`InvalidCredentialsException` → 401, `InvalidRefreshTokenException` → 401) и матчить каждое
своим `@ExceptionHandler`. Это уже отмечено как известный пробел в `CLAUDE.md` — приведено
здесь только потому, что без него `refresh`/`logout` будет неудобно тестировать через
Postman/curl (не видно, 500 — это "токен невалиден" или реальный баг).

---

## Чек-лист

- [ ] Пофиксить `JwtAuthenticationFilter` (try/catch вокруг `extractEmail`/`isTokenValid`)
- [ ] `RefreshTokenService`: `validateRefreshToken`, `revokeRefreshToken`
- [ ] `AuthServiceImpl.refresh` / `AuthServiceImpl.logout`
- [ ] `AuthController`: `POST /auth/refresh`, `POST /auth/logout`
- [ ] Прогнать `.\mvnw.cmd test`, вручную проверить через curl/Postman:
  - логин → получить access+refresh
  - подождать/подделать истёкший access-токен → дёрнуть защищённый эндпоинт → должно быть 401, не 500
  - `/auth/refresh` с валидным refresh-токеном → новый access-токен
  - `/auth/refresh` с чужим/несуществующим refresh-токеном → ошибка, не 500 (или хотя бы осознанный 500, если пункт 5 не делаешь)
  - `/auth/logout` → после него `/auth/refresh` с тем же токеном должен отказать
- [ ] (опционально) `GlobalExceptionHandler`
