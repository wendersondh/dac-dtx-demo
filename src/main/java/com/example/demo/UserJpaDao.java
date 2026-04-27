package com.example.demo;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Implementação JPA do UserDao.
 *
 * Usa EntityManager diretamente (sem Spring Data JPA) para demonstrar
 * o funcionamento de baixo nível do JPA: ciclo de vida das entidades,
 * contexto de persistência e gerenciamento de transações via @Transactional.
 *
 * @author DAC
 * @version 1.0
 */
@Repository
@Primary
@Qualifier("jpa")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "jpa", matchIfMissing = true)
public class UserJpaDao implements UserDao {

    /** EntityManager injetado para gerenciar operações de persistência */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Persiste um novo usuário no banco de dados.
     *
     * @param user Objeto UserEntity a ser salvado
     */
    @Transactional
    public void save(UserEntity user) {
        entityManager.persist(user);
    }

    /**
     * Recupera um usuário pelo seu ID.
     *
     * Nota: Este método usa isolamento de transação READ_UNCOMMITTED,
     * permitindo leitura de dados não confirmados (dirty reads).
     *
     * @param id Identificador único do usuário
     * @return Objeto UserEntity encontrado, ou null se não existir
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public UserEntity findById(Long id) {
        return entityManager.find(UserEntity.class, id);
    }

    /**
     * Recupera todos os usuários cadastrados.
     *
     * @return Lista com todos os usuários no banco de dados
     */
    public List<UserEntity> findAll() {
        return entityManager.createQuery("SELECT u FROM UserEntity u", UserEntity.class).getResultList();
    }

    /**
     * Atualiza um usuário existente.
     *
     * Recupera o usuário do banco de dados pelo ID, atualiza seus campos
     * (name e email) com os novos valores e sincroniza com o banco.
     *
     * Nota: Este método está sem @Transactional. Considere adicionar
     * a anotação se necessário garantir transação explícita.
     *
     * @param user Objeto UserEntity contendo o ID e novos dados
     */
    public void update(UserEntity user) {
        UserEntity user0 = findById(user.getId());
        user0.setName(user.getName());
        user0.setEmail(user.getEmail());
        entityManager.merge(user0);
        // usar reflection para recuperar o tipo
        System.out.println("User class (inner): " + user0.getClass().getName());
    }

    /**
     * Remove um usuário do banco de dados.
     *
     * @param id Identificador único do usuário a ser removido
     */
    @Transactional
    public void delete(Long id) {
        UserEntity user = findById(id);
        if (user != null) {
            entityManager.remove(user);
        }
    }
}
