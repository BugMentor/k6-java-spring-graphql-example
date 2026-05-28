package com.example.users.application.port.outgoing;

import com.example.users.domain.model.User;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(Long id);
}
