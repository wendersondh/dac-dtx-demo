package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integração do protocolo 2PC via XATransactionCoordinator.
 *
 * Cada teste executa o fluxo completo do 2PC manualmente, tornando
 * cada fase visível no log:
 *
 *   [TM] begin()
 *   [TM] enlist()
 *   [XA-DAO] INSERT executado ... aguardando decisão do TM...
 *   [TM] end(TMSUCCESS)
 *   [TM] FASE 1 — PREPARE
 *   [TM] prepare() → ... votou: XA_OK
 *   [TM] FASE 2 — COMMIT  (ou ROLLBACK)
 *   [TM] commit() / rollback()
 */
@SpringBootTest
class UserXaTest {

    @Autowired private XATransactionCoordinator coordinator;
    @Autowired private UserXaDao                userXaDao;
    @Autowired private UserDao                  userDao;       // UserJpaDao (via app.dao.impl=jpa)

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 1: commit feliz
    // begin → enlist → SQL → delistAll → prepare → commit
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deveCommitarTransacaoXA() throws Exception {
        int antes = userDao.findAll().size();

        coordinator.begin();

        userXaDao.save(new UserEntity("Alice XA", "alice@xa.com"));

        coordinator.delistAll();

        boolean prepared = coordinator.prepare();
        assertTrue(prepared, "Fase 1 (prepare) deve retornar true quando todos os RMs votam OK");

        coordinator.commit();

        int depois = userDao.findAll().size();
        assertEquals(antes + 1, depois,
                "Após commit XA, o usuário deve estar persistido no banco");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 2: rollback — simula decisão negativa do TM após o SQL
    // begin → enlist → SQL → delistAll → rollback (sem prepare)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deveDesfazerTransacaoXANoRollback() throws Exception {
        int antes = userDao.findAll().size();

        coordinator.begin();

        userXaDao.save(new UserEntity("Bob XA", "bob@xa.com"));

        coordinator.delistAll();

        // TM decide não commitar (ex: outro RM votou ABORT)
        coordinator.rollback();

        int depois = userDao.findAll().size();
        assertEquals(antes, depois,
                "Após rollback XA, nenhum dado deve ter sido persistido");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 3: prepare → rollback — simula queda após fase 1 com rollback
    // begin → enlist → SQL → delistAll → prepare → rollback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deveDesfazerMesmoAposPrepareSeFaseDecisorRollback() throws Exception {
        int antes = userDao.findAll().size();

        coordinator.begin();

        userXaDao.save(new UserEntity("Carol XA", "carol@xa.com"));

        coordinator.delistAll();
        coordinator.prepare(); // fase 1 concluída — RM gravou em log durável
        coordinator.rollback();// mas TM decidiu rollback (ex: timeout, outro RM falhou)

        int depois = userDao.findAll().size();
        assertEquals(antes, depois,
                "RM deve desfazer mesmo após ter votado OK no prepare");
    }
}
