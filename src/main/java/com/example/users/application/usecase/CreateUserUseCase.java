package com.example.users.application.usecase;

import com.example.users.application.port.outgoing.UserRepositoryPort;
import com.example.users.domain.model.User;
import io.opentelemetry.instrumentation.annotations.WithSpan;

public class CreateUserUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public CreateUserUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    @WithSpan
    public User execute(User user) {
        return userRepositoryPort.save(user);
    }
}
