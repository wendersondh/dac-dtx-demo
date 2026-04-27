package com.example.demo;

import java.util.List;

/**
 * Contrato de acesso a dados para UserEntity.
 *
 * Implementado por:
 * - UserJpaDao: usa JPA/EntityManager (qualificador "jpa")
 * - UserJdbcDao: usa JDBC/DataSource diretamente (qualificador "jdbc")
 *
 * A implementação ativa é selecionada pela propriedade app.dao.impl
 * em application.properties.
 */
public interface UserDao {
    void save(UserEntity user);
    UserEntity findById(Long id);
    List<UserEntity> findAll();
    void update(UserEntity user);
    void delete(Long id);
}
