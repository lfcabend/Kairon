package com.kairon.identity.app;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.PatPrincipal;
import com.kairon.common.security.PersonalAccessTokenAuthenticationFilter;
import com.kairon.identity.app.PersonalAccessTokenService.Issued;
import com.kairon.identity.domain.PersonalAccessToken;
import com.kairon.identity.repo.PersonalAccessTokenRepository;
import com.kairon.common.security.UserId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalAccessTokenServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000c1"));

    @Mock
    PersonalAccessTokenRepository repository;

    PersonalAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new PersonalAccessTokenService(repository);
        org.mockito.Mockito.lenient().when(repository.save(any(PersonalAccessToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issue_returnsARawValueOncePrefixedAndStoresOnlyItsHash() {
        Issued issued = service.issue(USER, "Claude Code", null);

        assertThat(issued.rawToken()).startsWith(PersonalAccessTokenAuthenticationFilter.PREFIX);
        assertThat(issued.view().name()).isEqualTo("Claude Code");
        assertThat(issued.view().revoked()).isFalse();

        org.mockito.ArgumentCaptor<PersonalAccessToken> captor =
                org.mockito.ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).doesNotContain(issued.rawToken());
        assertThat(captor.getValue().getTokenHash()).hasSize(64); // hex SHA-256
    }

    @Test
    void validate_resolvesAnActiveTokenAndTouchesLastUsedAt() {
        PersonalAccessToken stored = PersonalAccessToken.issue(USER.value(), "Claude Code", "irrelevant-hash", null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        Optional<PatPrincipal> result = service.validate("kairon_pat_whatever");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(USER);
        assertThat(stored.getLastUsedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    void validate_rejectsARevokedToken() {
        PersonalAccessToken stored = PersonalAccessToken.issue(USER.value(), "Claude Code", "irrelevant-hash", null);
        stored.revoke(Instant.now());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThat(service.validate("kairon_pat_whatever")).isEmpty();
    }

    @Test
    void validate_rejectsAnExpiredToken() {
        PersonalAccessToken stored = PersonalAccessToken.issue(USER.value(), "Claude Code", "irrelevant-hash",
                Instant.now().minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThat(service.validate("kairon_pat_whatever")).isEmpty();
    }

    @Test
    void validate_rejectsAnUnrecognizedToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.validate("kairon_pat_not-a-real-one")).isEmpty();
    }

    @Test
    void list_returnsOnlyTheCallersNonRevokedTokensMostRecentFirst() {
        PersonalAccessToken a = PersonalAccessToken.issue(USER.value(), "A", "hashA", null);
        when(repository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(USER.value())).thenReturn(List.of(a));

        List<PersonalAccessTokenService.PersonalAccessTokenView> views = service.list(USER);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).name()).isEqualTo("A");
    }

    @Test
    void revoke_onTheCallersOwnTokenSucceeds() {
        UUID id = UUID.randomUUID();
        when(repository.revokeByIdAndUser(eq(id), eq(USER.value()), any())).thenReturn(1);

        service.revoke(USER, id, Instant.now());

        verify(repository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void revoke_onAMissingOrForeignTokenIs404() {
        UUID id = UUID.randomUUID();
        when(repository.revokeByIdAndUser(eq(id), eq(USER.value()), any())).thenReturn(0);
        when(repository.findByIdAndUserId(id, USER.value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(USER, id, Instant.now()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }
}
