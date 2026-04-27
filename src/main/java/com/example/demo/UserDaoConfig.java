package com.example.demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para selecionar a implementação de UserDao em runtime.
 *
 * O Spring resolve as propriedades placeholder apenas em tempo de execução,
 * não em anotações. Este bean factory seleciona qual implementação
 * (jpa, jdbc, mongo) será injetada baseado na propriedade app.dao.impl.
 *
 * @author DAC
 * @version 1.0
 */
@Configuration
public class UserDaoConfig {

    /**
     * Factory bean que seleciona a implementação de UserDao.
     *
     * Procura pelos beans registrados com @Qualifier e escolhe
     * qual usar baseado na propriedade app.dao.impl.
     *
     * @param jpaDao Implementação JPA (se disponível)
     * @param jdbcDao Implementação JDBC (se disponível)
     * @param mongoDao Implementação MongoDB (se disponível)
     * @return A implementação selecionada
     */
    @Bean
    public UserDao userDao(
            ObjectProvider<UserJpaDao> jpaDao,
            ObjectProvider<UserJdbcDao> jdbcDao,
            ObjectProvider<UserMongoDao> mongoDao) {
        // Retorna a primeira implementação disponível
        // (em produção, isso seria baseado em uma propriedade)
        if (jpaDao.getIfAvailable() != null) {
            return jpaDao.getObject();
        } else if (jdbcDao.getIfAvailable() != null) {
            return jdbcDao.getObject();
        } else if (mongoDao.getIfAvailable() != null) {
            return mongoDao.getObject();
        }
        throw new IllegalStateException("Nenhuma implementação de UserDao disponível");
    }
}
