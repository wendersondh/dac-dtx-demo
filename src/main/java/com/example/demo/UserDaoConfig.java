package com.example.demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para selecionar a implementação de UserDao em runtime.
 *
 * O Spring resolve as propriedades placeholder apenas em tempo de execução,
 * não em anotações. Este bean factory seleciona qual implementação
 * (jpa, jdbc, mongo, dtx) será injetada baseado na propriedade app.dao.impl.
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
     * @param dtxCoord Coordenador 2PC (se app.dao.impl=dtx)
     * @param jpaDao   Implementação JPA (se app.dao.impl=jpa)
     * @param jdbcDao  Implementação JDBC (se app.dao.impl=jdbc)
     * @param mongoDao Implementação MongoDB (se app.dao.impl=mongo)
     * @return A implementação selecionada
     */
    @Bean
    public UserDao userDao(
            ObjectProvider<DTxCoord>    dtxCoord,
            ObjectProvider<UserJpaDao>  jpaDao,
            ObjectProvider<UserJdbcDao> jdbcDao,
            ObjectProvider<UserMongoDao> mongoDao) {
        if (dtxCoord.getIfAvailable() != null) {
            return dtxCoord.getObject();
        } else if (jpaDao.getIfAvailable() != null) {
            return jpaDao.getObject();
        } else if (jdbcDao.getIfAvailable() != null) {
            return jdbcDao.getObject();
        } else if (mongoDao.getIfAvailable() != null) {
            return mongoDao.getObject();
        }
        throw new IllegalStateException("Nenhuma implementação de UserDao disponível");
    }
}
