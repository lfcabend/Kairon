package com.kairon.identity.app;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.kairon.identity.domain.RefreshToken;
import com.kairon.identity.repo.RefreshTokenRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenProperties properties = new RefreshTokenProperties(null, null);

    RefreshTokenService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, properties);
    }

    @Test
    void hashIs64HexCharsAndDeterministic() {
        String a = RefreshTokenService.hash("token-value");
        String b = RefreshTokenService.hash("token-value");
        assertThat(a).isEqualTo(b).matches("[0-9a-f]{64}");
        assertThat(RefreshTokenService.hash("other")).isNotEqualTo(a);
    }

    @Test
    void generatedRawTokensAreLongAndUnique() {
        String one = RefreshTokenService.generateRawToken();
        String two = RefreshTokenService.generateRawToken();
        assertThat(one).hasSizeGreaterThanOrEqualTo(43).isNotEqualTo(two);
    }

    @Test
    void issueNewFamilyStoresOnlyTheHashAndReturnsTheRawTokenOnce() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-09T00:00:00Z");

        RefreshTokenService.Issued issued = service.issueNewFamily(userId, "JUnit", now);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.rawToken()))
                .isNotEqualTo(issued.rawToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(properties.ttl()));
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void rotateRevokesTheOldTokenAndKeepsTheFamily() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        RefreshToken current = RefreshToken.issue(UUID.randomUUID(),
                RefreshTokenService.hash("old"), "JUnit", now.plusSeconds(1000));

        RefreshTokenService.Issued rotated = service.rotate(current, "JUnit", now);

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.entity().getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(rotated.entity().isActive(now)).isTrue();
        assertThat(rotated.rawToken()).isNotBlank();
    }

    @Test
    void findByRawTokenLooksUpByHash() {
        RefreshToken token = RefreshToken.issue(UUID.randomUUID(),
                RefreshTokenService.hash("raw"), "JUnit", Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(any())).thenAnswer(inv ->
                RefreshTokenService.hash("raw").equals(inv.getArgument(0))
                        ? Optional.of(token) : Optional.empty());

        assertThat(service.findByRawToken("raw")).contains(token);
        assertThat(service.findByRawToken("missing")).isEmpty();
    }
}
