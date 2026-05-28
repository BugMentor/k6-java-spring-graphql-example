package com.enterprise.payment.presentation.rest;

import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    private final MerchantJpaRepository merchantJpaRepository;

    public MerchantController(MerchantJpaRepository merchantJpaRepository) {
        this.merchantJpaRepository = merchantJpaRepository;
    }

    @PostMapping
    @WithSpan("create-merchant")
    public ResponseEntity<Merchant> createMerchant(
            @SpanAttribute("merchant.name") @RequestBody CreateMerchantRequest request) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                request.name(),
                request.apiKey()
        );
        Merchant saved = merchantJpaRepository.save(merchant);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    @WithSpan("get-merchant")
    public ResponseEntity<Merchant> getMerchant(@PathVariable UUID id) {
        return merchantJpaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @WithSpan("list-merchants")
    public ResponseEntity<List<Merchant>> listMerchants() {
        return ResponseEntity.ok(merchantJpaRepository.findAll());
    }

    @DeleteMapping("/{id}")
    @WithSpan("delete-merchant")
    public ResponseEntity<Void> deleteMerchant(@PathVariable UUID id) {
        if (merchantJpaRepository.existsById(id)) {
            merchantJpaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    public record CreateMerchantRequest(String name, String apiKey) {}
}