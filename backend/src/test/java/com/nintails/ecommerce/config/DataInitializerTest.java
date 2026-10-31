package com.nintails.ecommerce.config;

import com.nintails.ecommerce.repository.CategoryRepository;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DataInitializer initializer;

    @Test
    void defaultStartupCreatesCategoriesWithoutSeedingDemoProducts() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", false);
        when(userRepository.findByEmail("admin@9tails.com")).thenReturn(Optional.empty());

        initializer.run();

        verify(categoryRepository, times(9)).findByNameIgnoreCase(anyString());
        verify(categoryRepository, times(9)).save(any(Category.class));
        verifyNoInteractions(productRepository);
    }

    @Test
    void configuredAdminPasswordIsNotOverwrittenByDefault() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", false);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "a-strong-password-123");
        User existingAdmin = User.builder().id(1L).email("admin@example.com")
                .role(User.Role.ADMIN).password("existing-password-hash").build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));
        when(userRepository.findByEmail("admin@9tails.com")).thenReturn(Optional.empty());

        initializer.run();

        verify(passwordEncoder, never()).encode("a-strong-password-123");
        verify(userRepository, never()).save(existingAdmin);
    }

    @Test
    void explicitlyConfiguredStartupResetUpdatesOnlyTheSelectedAdminPassword() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", false);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "a-strong-password-123");
        ReflectionTestUtils.setField(initializer, "resetAdminPasswordOnStartup", true);
        User existingAdmin = User.builder().id(1L).email("admin@example.com")
                .role(User.Role.ADMIN).password("existing-password-hash").build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));
        when(userRepository.findByEmail("admin@9tails.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-strong-password-123")).thenReturn("new-password-hash");

        initializer.run();

        assertEquals("new-password-hash", existingAdmin.getPassword());
        verify(userRepository).save(existingAdmin);
    }

    @Test
    void invalidAdminBootstrapCredentialsDoNotPreventApplicationStartup() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", false);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "short");

        assertDoesNotThrow(() -> initializer.run());

        verify(userRepository, never()).save(argThat(user ->
                "admin@example.com".equals(user.getEmail())));
        verify(userRepository).findByEmail("admin@9tails.com");
    }

    @Test
    void incompleteAdminBootstrapCredentialsDoNotPreventApplicationStartup() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", false);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");

        assertDoesNotThrow(() -> initializer.run());

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository).findByEmail("admin@9tails.com");
    }
}
