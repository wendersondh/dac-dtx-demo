package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Qualifier("dtx")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "dtx")
public class DTxCoord implements UserDao {

    private final UserH2DAO    h2Dao;
    private final UserMongoDAO mongoDao;

    public DTxCoord(UserH2DAO h2Dao, UserMongoDAO mongoDao) {
        this.h2Dao    = h2Dao;
        this.mongoDao = mongoDao;
    }


    // save — orquestra o 2PC entre H2 e MongoDB

    @Override
    public void save(UserEntity user) {
        log("══════════════════════════════════════════════════");
        log("INÍCIO DA TRANSAÇÃO DISTRIBUÍDA (2PC simulado)");
        log("Entidade: " + user.getName() + " / " + user.getEmail());
        log("══════════════════════════════════════════════════");

        // ── FASE 1: PREPARE ──────────────────────────────────────────────────
        log("FASE 1 — PREPARE (votação dos participantes)");

        boolean h2Ready    = h2Dao.prepare(user);
        boolean mongoReady = mongoDao.prepare(user);

        log("Votos recebidos: H2=" + voto(h2Ready) + " | MongoDB=" + voto(mongoReady));

        // ── FASE 2: COMMIT ou ROLLBACK ───────────────────────────────────────
        if (h2Ready && mongoReady) {
            log("FASE 2 — COMMIT (ambos os participantes votaram YES)");
            h2Dao.commit();
            mongoDao.commit();
            log("Transação distribuída CONFIRMADA. Usuário persistido em H2 e MongoDB.");

        } else {
            log("FASE 2 — ROLLBACK (um ou mais participantes votaram NO)");
            h2Dao.rollback();
            mongoDao.rollback();
            log("Transação distribuída ABORTADA. Nenhum banco foi alterado.");
            throw new RuntimeException(
                    "2PC abortado — H2=" + voto(h2Ready) + ", MongoDB=" + voto(mongoReady));
        }

        log("══════════════════════════════════════════════════");
    }


    // Leituras — delegadas ao H2 (fonte canônica de leitura)

    @Override
    public UserEntity findById(Long id) {
        return h2Dao.findById(id);
    }

    @Override
    public List<UserEntity> findAll() {
        return h2Dao.findAll();
    }


    // Update e Delete — propagados sequencialmente a ambos os bancos

    @Override
    public void update(UserEntity user) {
        log("update() → propagando para H2 e MongoDB...");
        h2Dao.update(user);
        mongoDao.update(user);
    }

    @Override
    public void delete(Long id) {
        log("delete() → propagando para H2 e MongoDB...");
        h2Dao.delete(id);
        mongoDao.delete(id);
    }



    private String voto(boolean ready) {
        return ready ? "YES ✓" : "NO  ✗";
    }

    private void log(String msg) {
        System.out.println("[DTxCoord] " + msg);
    }
}