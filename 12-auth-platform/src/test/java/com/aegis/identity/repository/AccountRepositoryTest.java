package com.aegis.identity.repository;

import com.aegis.identity.entity.Account;
import com.aegis.identity.entity.AccountStatus;
import com.aegis.identity.entity.Permission;
import com.aegis.identity.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void loadsAnAccountWithItsRolesAndPermissions() {
        Permission read = new Permission();
        read.setName("document:read");
        read.setDescription("Read a document");
        entityManager.persist(read);

        Role viewer = new Role();
        viewer.setName("ROLE_VIEWER");
        viewer.setDescription("Reads documents");
        viewer.getPermissions().add(read);
        entityManager.persist(viewer);

        Account account = new Account();
        account.setEmail("karthik@aegis.test");
        account.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        account.setDisplayName("Karthik Rao");
        account.setStatus(AccountStatus.ACTIVE);
        account.getRoles().add(viewer);
        entityManager.persist(account);

        entityManager.flush();
        entityManager.clear();

        Optional<Account> found = accountRepository.findByEmail("karthik@aegis.test");

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).extracting(Role::getName).containsExactly("ROLE_VIEWER");
        assertThat(found.get().getRoles().iterator().next().getPermissions())
                .extracting(Permission::getName).containsExactly("document:read");
        assertThat(accountRepository.existsByEmail("nobody@aegis.test")).isFalse();
    }
}
