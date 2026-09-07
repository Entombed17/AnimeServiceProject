# Code Review — AnimeTrackerServer

Разбор текущего состояния проекта: `src/`, `pom.xml`, `application.yaml`.
Дата: 2026-09-07.

---

## Что сделано хорошо

**Слоистость выдержана честно.** `controller → service (интерфейс) → service.impl → repository → entity`, DTO разложены по `request/auth`, `request/profile`, `response/...`, `common`. Для пета это редкость — обычно всё валится в один пакет. Разделение интерфейс/реализация проведено последовательно во всех трёх сервисах.

**Инъекция через конструктор.** `@RequiredArgsConstructor` + `private final` везде (`AuthServiceImpl.java:24-30`, `SecurityConfig.java:18`, `JwtAuthenticationFilter.java:28-30`), ни одного `@Autowired` на поле. Это правильный современный стиль.

**Пароли.** BCrypt (`SecurityBeansConfig.java:12`), в БД лежит только `passwordHash` (`User.java:31`), наружу отдаётся `UserResponse` без него (`UserResponse.java`) — сущность не утекает в JSON. Это ровно то, что нужно.

**Логин не разглашает существование юзера.** `AuthServiceImpl.java:68,71` — одно и то же сообщение «Invalid email or password» и на отсутствующий email, и на неверный пароль. Многие про это забывают.

**Refresh-токен — непрозрачный UUID в БД, а не второй JWT** (`RefreshTokenServiceImpl.java:26`). Это осознанно верный выбор: такой токен можно реально отозвать, в отличие от подписанного JWT.

**JJWT подключен правильно.** `jjwt-api` в compile, `jjwt-impl` и `jjwt-jackson` в `runtime` (`pom.xml:80-96`) — реализация не светится в API проекта. Используется новый API 0.12 (`parser().verifyWith()`, `.subject()`), а не устаревший `parserBuilder()`.

**Security-конфиг по сути корректен:** `STATELESS`, csrf отключён (для JWT это правильно, а не «дырка»), фильтр вставлен перед `UsernamePasswordAuthenticationFilter` (`SecurityConfig.java:23-32`).

---

## Что плохо

### 1. Критично: секреты в гите — `src/main/resources/application.yaml:8,19`

```yaml
password: Dwen2key123
jwt.secret: NArLxxoDiEuntV6hSpCDBiSb4SOMySCsL7AFeEbxCdndGCqJoNvGsTOZE91IpHyjz9MXY/dlTBl+uhXIM4DBqg==
```

Файл в `git ls-files`, то есть и пароль от БД, и подписной ключ JWT лежат в истории репозитория. Если репозиторий когда-нибудь станет публичным — любой сможет выпустить валидный access-токен на любой email.

Правильно: `password: ${DB_PASSWORD}`, `secret: ${JWT_SECRET}`, значения — в переменных окружения или `application-local.yaml` в `.gitignore`. И **ключ надо перегенерировать** — удаления из файла мало, он останется в истории коммитов.

### 2. Критично: битый токен = 500 вместо 401 — `security/JwtAuthenticationFilter.java:47`

```java
String email = jwtService.extractEmail(token);
```

`extractAllClaims` бросает `ExpiredJwtException` / `SignatureException` / `MalformedJwtException`. Здесь их никто не ловит, они улетают из фильтра. Итог: **любой протухший токен** (а он живёт 15 минут, то есть это будет постоянно) даёт клиенту 500 со стектрейсом вместо 401. Это самый часто срабатывающий баг в проекте.

Нужен `try/catch` вокруг разбора: при исключении — просто `filterChain.doFilter(...)` и выход, пусть Security сам отдаст 401.

Заодно там же: неиспользуемый импорт `java.net.http.HttpRequest` (`:20`) — это вообще класс HTTP-клиента, попал по автоимпорту.

### 3. Все ошибки бизнес-логики — это 500

`AuthServiceImpl.java:35,39,68,71` — голый `RuntimeException`, `@RestControllerAdvice` нет. Значит:

- «Email already exists» → 500 вместо 409
- «Invalid email or password» → 500 вместо 401
- Ошибка `@Valid` → дефолтный спринговый JSON, не ваш формат

Клиент физически не может отличить «неверный пароль» от «БД упала». Нужны свои исключения (`EmailAlreadyExistsException`, `InvalidCredentialsException`) + один `@RestControllerAdvice` с общим телом ошибки. Это самая крупная архитектурная дыра после секретов.

### 4. `refresh` и `logout` не существуют — и это ломает UX

`AuthService.java` объявляет только `register` и `login`. При этом access-токен живёт 15 минут (`application.yaml:20`), а refresh-токен выдаётся и сохраняется. Получается: **клиент обязан заново логиниться каждые 15 минут**, потому что обменять refresh не на что.

Мёртвый код, который под это уже написан, но никем не используется: `RefreshTokenRequest.java`, `AccessTokenResponse.java`, `RefreshTokenRepository.findByToken()` (`:13`), поле `RefreshToken.expiresAt` (`:31` — оно записывается и **нигде не проверяется**). Это следующая по приоритету фича.

### 5. `register` без транзакции и с гонкой — `AuthServiceImpl.java:33-47`

Нет `@Transactional`. Проверка `existsByUsername` / `existsByEmail` и последующий `save` — классический check-then-act: два одновременных запроса с одним email пройдут обе проверки, второй упадёт на `DataIntegrityViolationException` → снова 500. Unique-констрейнты в `User` стоят (хорошо), но их нарушение надо ловить и превращать в 409.

Также стоит подумать про регистр email: `findByEmail` регистрозависим, `User@mail.ru` и `user@mail.ru` — два разных аккаунта. Обычно email нормализуют в lower-case при сохранении.

### 6. `@Service` на интерфейсе — `service/JwtService.java:6`

```java
@Service
public interface JwtService {
```

Аннотация на интерфейсе не делает ничего (бин создаётся из `JwtServiceImpl`, где `@Service` стоит правильно). Убрать — она вводит в заблуждение, тем более что на `AuthService` и `RefreshTokenService` её нет, то есть стиль ещё и несогласован.

### 7. `LocalDateTime` для времени жизни токена — `entity/RefreshToken.java:28,31`

Для момента времени нужен `Instant` (или `OffsetDateTime`). `LocalDateTime` не несёт зоны: при смене таймзоны сервера или переводе часов срок жизни токена поедет. Плюс `createdAt` и `expiresAt` считаются двумя разными вызовами `LocalDateTime.now()` (`RefreshTokenServiceImpl.java:27-28`) — мелочь, но нужен один `Instant now`.

Там же: у `createdAt`/`expiresAt` нет `nullable = false`, хотя они всегда заполняются.

### 8. Одна сессия на пользователя — `RefreshTokenServiceImpl.java:21-23`

`@OneToOne` + переиспользование строки означает, что вход с телефона молча выкидывает сессию на ноутбуке. Для трекера аниме с мобильным клиентом это почти наверняка не то поведение, которое вы хотите. Обычно делают `@ManyToOne` (много токенов на юзера) + поле device/user-agent, а `logout` удаляет конкретный токен.

Если односессионность выбрана осознанно — ок, но тогда это стоит записать как решение, а не как случайность.

### 9. Валидация продублирована в сущности — `entity/User.java:23,27`

`@Size` и `@Email` на `User` дублируют то же самое из `RegisterRequest`. Валидация — задача DTO на границе; на сущности она отработает только при flush и даст совсем другую ошибку. Плюс сообщение написано по-русски (`:27`), тогда как остальной проект англоязычный.

Мелочь рядом: `birthDate` (`:36`) — единственное поле без `@Column`, тогда как у соседей он явный. Работать будет, но глаз цепляется.

### 10. Тесты фактически отсутствуют

Один `contextLoads()` (`AnimeTrackerServerApplicationTests.java`), и он **требует поднятого Postgres** — значит, суита не запускается в CI и на чужой машине. При этом нетривиальная логика есть: `JwtServiceImpl` (подпись, срок, `isTokenValid`) и `AuthServiceImpl` — это идеальные кандидаты на юнит-тесты с моками, вообще без БД. Для интеграционных — Testcontainers или отдельный профиль.

Зависимости для тестов в `pom.xml:60-79` уже подключены и не используются.

### 11. Security: нет CORS и нет обработчика 401 — `SecurityConfig.java`

- CORS не настроен вообще. Как только появится веб-фронт на другом порту — все запросы упрутся в preflight.
- Нет `exceptionHandling(...)`: запрос без токена к защищённому эндпоинту вернёт пустой 403, а не 401 с телом. Клиенту непонятно, надо ли обновлять токен.

### 12. `ChangePasswordRequest.java:14` — смена пароля без текущего пароля

В DTO только `newPassword`. Если реализовать как есть, утёкший access-токен позволит сразу сменить пароль и увести аккаунт целиком. Нужно поле `currentPassword` + повторная проверка через `passwordEncoder.matches`, и после смены — инвалидация refresh-токена. Лучше поправить сейчас, пока эндпоинта нет.

### 13. Мелочи

- Мёртвые импорты валидации в response-DTO: `UserResponse.java:3-6`, `RegisterAndAuthResponse.java:4`, `AccessTokenResponse.java:3`, `ChangePasswordResponse.java:3-4`, `UpdateProfileResponse.java:3-4` — `@NotBlank` в ответе смысла не имеет, это следы копипасты. В `AuthServiceImpl.java:5,7,11` тоже три неиспользуемых импорта.
- DTO — изменяемые классы с `@Getter/@Setter`. На Java 21 это просятся `record`: код каждого DTO ужмётся до одной строки и они станут иммутабельными.
- `AuthController.register` отдаёт 200; для создания ресурса корректнее 201.
- Нет префикса версии: `/auth/**` вместо `/api/v1/auth/**`. Добавить сейчас дешевле, чем ломать клиентов потом.
- `pom.xml` с модульными стартерами Boot 4 (`spring-boot-starter-webmvc`, `*-test`) — проверено, всё резолвится, тут вопросов нет.

---

## Если делать по порядку

1. Секреты из `application.yaml` в env + ротация JWT-ключа
2. `try/catch` в `JwtAuthenticationFilter` (сейчас каждый протухший токен = 500)
3. `@RestControllerAdvice` + свои исключения вместо `RuntimeException`
4. `refresh` / `logout` — иначе 15-минутный токен делает приложение неюзабельным
5. `@Transactional` на `register` + обработка гонки
6. Юнит-тесты на `JwtServiceImpl` и `AuthServiceImpl`

Общее впечатление: скелет собран грамотно и по канону, ошибки — не «архитектурные», а типичные для незаконченного слоя (обработка ошибок, секреты, тесты). Домена аниме пока нет вообще, но фундамент под него нормальный.
