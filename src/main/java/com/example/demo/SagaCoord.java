package com.example.demo;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;


import java.util.List;


/**
 * Coordenador SAGA — implementação.
 *
 * Ativa quando app.dao.impl=saga.
 *
 * Fluxo:
 *   1. Salva o usuário no JDBC (passo local)
 *   2. Tenta salvar no MongoDB
 *   3. Se MongoDB falhar → compensa deletando o registro do JDBC (rollback manual)
 *
 * Diferença em relação ao 2PC:
 *   No 2PC ambos os bancos ficam em estado "preparado" antes de qualquer confirmação.
 *   No SAGA o primeiro banco é confirmado imediatamente; a compensação é uma
 *   operação inversa explícita (não um rollback de transação).
 */
@Repository
@Primary
@Qualifier("saga")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "saga")
public class SagaCoord implements UserDao {


    private final UserJdbcDao  jdbcDao;
    private final UserMongoDao mongoDao;


    public SagaCoord(UserJdbcDao jdbcDao, UserMongoDao mongoDao) {
        this.jdbcDao  = jdbcDao;
        this.mongoDao = mongoDao;
    }


    @Override
    public void save(UserEntity user) {
        log("══════════════════════════════════════");
        log("SAGA — INÍCIO");
        log("══════════════════════════════════════");


        // PASSO 1: salva no JDBC (confirmado imediatamente)
        log("PASSO 1 — salvando no JDBC...");
        jdbcDao.save(user);
        log("PASSO 1 — salvo. id=" + user.getId());


        // PASSO 2: tenta salvar no MongoDB
        log("PASSO 2 — salvando no MongoDB...");
        try {
            mongoDao.save(user);
            log("PASSO 2 — salvo. SAGA concluída com sucesso.");
        } catch (Exception e) {
            // COMPENSAÇÃO: reverte o passo 1 deletando o registro do JDBC
            log("PASSO 2 — FALHOU: " + e.getMessage());
            log("COMPENSAÇÃO — deletando id=" + user.getId() + " do JDBC...");
            try {
                jdbcDao.delete(user.getId());
                log("COMPENSAÇÃO — concluída. Nenhum banco foi alterado.");
            } catch (Exception compensationEx) {
                log("COMPENSAÇÃO — FALHOU: " + compensationEx.getMessage());
                log("ATENÇÃO: inconsistência detectada — JDBC tem o registro, MongoDB não.");
            }
            throw new RuntimeException("SAGA abortada — compensação executada", e);
        }


        log("══════════════════════════════════════");
    }


    @Override
    public UserEntity findById(Long id) { return jdbcDao.findById(id); }


    @Override
    public List<UserEntity> findAll() { return jdbcDao.findAll(); }


    @Override
    public void update(UserEntity user) {
        jdbcDao.update(user);
        mongoDao.update(user);
    }


    @Override
    public void delete(Long id) {
        jdbcDao.delete(id);
        mongoDao.delete(id);
    }


    private void log(String msg) { System.out.println("[SagaCoord] " + msg); }
}

