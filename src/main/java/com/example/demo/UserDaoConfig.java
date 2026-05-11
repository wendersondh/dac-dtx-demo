package com.example.demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seleciona a implementação de UserDao conforme app.dao.impl:
 *
 *   dtx    → DTxCoord   (2PC simulado: JDBC + MongoDB)
 *   saga   → SagaCoord  (SAGA com compensação)
 *   outbox → OutboxCoord (Outbox + relay assíncrono)
 *   jpa    → UserJpaDao
 *   jdbc   → UserJdbcDao
 *   mongo  → UserMongoDao
 */
@Configuration
public class UserDaoConfig {

    @Bean
    public UserDao userDao(
            ObjectProvider<DTxCoord>    dtxCoord,
            ObjectProvider<SagaCoord>   sagaCoord,
            ObjectProvider<OutboxCoord> outboxCoord,
            ObjectProvider<UserJpaDao>  jpaDao,
            ObjectProvider<UserJdbcDao> jdbcDao,
            ObjectProvider<UserMongoDao> mongoDao) {

        if (dtxCoord.getIfAvailable()    != null) return dtxCoord.getObject();
        if (sagaCoord.getIfAvailable()   != null) return sagaCoord.getObject();
        if (outboxCoord.getIfAvailable() != null) return outboxCoord.getObject();
        if (jpaDao.getIfAvailable()      != null) return jpaDao.getObject();
        if (jdbcDao.getIfAvailable()     != null) return jdbcDao.getObject();
        if (mongoDao.getIfAvailable()    != null) return mongoDao.getObject();

        throw new IllegalStateException("Nenhuma implementação de UserDao disponível");
    }
}