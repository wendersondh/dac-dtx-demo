package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Coordenador 2PC simulado — Two-Phase Commit manual sem frameworks.
 *
 * Ativa quando app.dao.impl=dtx.
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  FASE 1 — PREPARE (votação)                                  │
 * │    jdbcDao.prepare(user)  → INSERT JDBC, sem commit          │
 * │    mongoDao.prepare(user) → insertOne MongoDB, sem commit    │
 * │                                                              │
 * │  Se ambos votaram YES:                                       │
 * │    FASE 2a — COMMIT                                          │
 * │      jdbcDao.commit()  → conn.commit()                       │
 * │      mongoDao.commit() → session.commitTransaction()         │
 * │                                                              │
 * │  Se algum votou NO:                                          │
 * │    FASE 2b — ROLLBACK                                        │
 * │      jdbcDao.rollback()  → conn.rollback()                   │
 * │      mongoDao.rollback() → session.abortTransaction()        │
 * └──────────────────────────────────────────────────────────────┘
 */
@Repository
@Primary
@Qualifier("dtx")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "dtx")
public class DTxCoord implements UserDao {

    private final UserJdbcDao  jdbcDao;
    private final UserMongoDao mongoDao;

    public DTxCoord(UserJdbcDao jdbcDao, UserMongoDao mongoDao) {
        this.jdbcDao  = jdbcDao;
        this.mongoDao = mongoDao;
    }

    @Override
    public void save(UserEntity user) {
        log("══════════════════════════════════════");
        log("2PC — INÍCIO");
        log("══════════════════════════════════════");

        // FASE 1: PREPARE
        log("FASE 1 — PREPARE");
        boolean jdbcOk  = jdbcDao.prepare(user);
        boolean mongoOk = mongoDao.prepare(user);
        log("Votos: JDBC=" + voto(jdbcOk) + " | MongoDB=" + voto(mongoOk));

        // FASE 2: COMMIT ou ROLLBACK
        if (jdbcOk && mongoOk) {
            log("FASE 2 — COMMIT");
            jdbcDao.commit();
            mongoDao.commit();
            log("Usuário salvo em JDBC e MongoDB com sucesso.");
        } else {
            log("FASE 2 — ROLLBACK");
            jdbcDao.rollback();
            mongoDao.rollback();
            log("Transação abortada. Nenhum banco foi alterado.");
            throw new RuntimeException(
                "2PC abortado — JDBC=" + voto(jdbcOk) + ", MongoDB=" + voto(mongoOk));
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

    private String voto(boolean ok) { return ok ? "YES" : "NO"; }
    private void log(String msg) { System.out.println("[DTxCoord] " + msg); }
}