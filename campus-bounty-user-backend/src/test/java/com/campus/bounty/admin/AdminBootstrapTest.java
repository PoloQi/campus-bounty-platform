package com.campus.bounty.admin;

import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, wallets, environment);

    @Test
    void defaultStartupDoesNotCreateADemoAdministrator() {
        bootstrap.run();

        verifyNoInteractions(users, encoder, wallets);
    }

    @Test
    void configuredAdministratorIsCreatedOnceWithoutResettingCredentials() {
        environment.withProperty("bounty.admin.username", "campus-operator")
                .withProperty("bounty.admin.password", "test-bootstrap-secret");
        var stored = new AtomicReference<User>();
        when(users.findByUsername("campus-operator")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(7L);
            stored.set(user);
            return user;
        });
        when(encoder.encode("test-bootstrap-secret")).thenReturn("encoded");

        bootstrap.run();
        environment.setProperty("bounty.admin.password", "different-bootstrap-secret");
        bootstrap.run();

        assertEquals("campus-operator", stored.get().getUsername());
        assertEquals("ADMIN", stored.get().getRole());
        assertEquals("encoded", stored.get().getPasswordHash());
        verify(users).save(any(User.class));
        verify(encoder).encode("test-bootstrap-secret");
        var wallet = ArgumentCaptor.forClass(Wallet.class);
        verify(wallets).save(wallet.capture());
        assertEquals(7L, wallet.getValue().getUserId());
    }

    @Test
    void incompleteProductionConfigurationFailsBeforeCreatingAnAccount() {
        environment.withProperty("bounty.admin.username", "campus-operator");

        assertThrows(IllegalStateException.class, bootstrap::run);
        verifyNoInteractions(users, encoder, wallets);
    }

    @Test
    void configuredUsernameCannotPromoteAnExistingStudent() {
        environment.withProperty("bounty.admin.username", "student")
                .withProperty("bounty.admin.password", "test-bootstrap-secret");
        var student = new User();
        when(users.findByUsername("student")).thenReturn(Optional.of(student));

        assertThrows(IllegalStateException.class, bootstrap::run);
        assertEquals("STUDENT", student.getRole());
        verifyNoInteractions(encoder, wallets);
    }
}
