package com.enterprise.payment.presentation.graphql;

import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
public class UserGraphQLController {

    private final UserJpaRepository userJpaRepository;

    public UserGraphQLController(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @QueryMapping
    public User user(@Argument String id) {
        return userJpaRepository.findById(UUID.fromString(id)).orElse(null);
    }

    @QueryMapping
    public List<User> users() {
        return userJpaRepository.findAll();
    }

    @MutationMapping
    public User createUser(@Argument String email, @Argument String fullName, @Argument String status) {
        User user = new User(UUID.randomUUID(), email, fullName, status != null ? status : "ACTIVE");
        return userJpaRepository.save(user);
    }

    @MutationMapping
    public boolean deleteUser(@Argument String id) {
        UUID uuid = UUID.fromString(id);
        if (userJpaRepository.existsById(uuid)) {
            userJpaRepository.deleteById(uuid);
            return true;
        }
        return false;
    }
}
