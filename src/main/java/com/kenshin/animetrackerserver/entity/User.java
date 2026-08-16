package com.kenshin.animetrackerserver.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false, unique = true)
    @Size(min = 1, max = 20)
    private String username;

    @Column(length = 255, nullable = false, unique = true)
    @Email(message = "Пожалуйста, введите корректный email")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "profile_status", length = 70)
    private String profileStatus;

    private LocalDate birthDate;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;
}
