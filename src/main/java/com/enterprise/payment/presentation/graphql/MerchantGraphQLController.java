package com.enterprise.payment.presentation.graphql;

import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
public class MerchantGraphQLController {

    private final MerchantJpaRepository merchantJpaRepository;

    public MerchantGraphQLController(MerchantJpaRepository merchantJpaRepository) {
        this.merchantJpaRepository = merchantJpaRepository;
    }

    @QueryMapping
    public Merchant merchant(@Argument String id) {
        return merchantJpaRepository.findById(UUID.fromString(id)).orElse(null);
    }

    @QueryMapping
    public List<Merchant> merchants() {
        return merchantJpaRepository.findAll();
    }

    @MutationMapping
    public Merchant createMerchant(@Argument String name, @Argument String apiKey) {
        Merchant merchant = new Merchant(UUID.randomUUID(), name, apiKey);
        return merchantJpaRepository.save(merchant);
    }

    @MutationMapping
    public boolean deleteMerchant(@Argument String id) {
        UUID uuid = UUID.fromString(id);
        if (merchantJpaRepository.existsById(uuid)) {
            merchantJpaRepository.deleteById(uuid);
            return true;
        }
        return false;
    }
}
