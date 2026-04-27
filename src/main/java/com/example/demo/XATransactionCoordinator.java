package com.example.demo;

import org.springframework.stereotype.Component;

import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordenador de transação XA — implementação didática do protocolo 2PC.
 *
 * Em produção esse papel é do Transaction Manager (Atomikos, Narayana, etc.).
 * Aqui tornamos cada etapa explícita e visível para fins de aprendizado.
 *
 * Fluxo do protocolo:
 *
 *   begin()
 *     └─ gera um TxId ou Xid global
 *
 *   enlist(xaConn)           ← um por banco de dados participante
 *     └─ XAResource.start(xid)
 *
 *   [executa SQL em cada conexão enlistada]
 *
 *   delistAll()
 *     └─ XAResource.end(xid, TMSUCCESS)
 *
 *   prepare()                ← FASE 1: cada RM vota OK ou ABORT
 *     └─ XAResource.prepare(xid) por banco
 *
 *   commit()  ou  rollback() ← FASE 2: decisão final do TM
 *     └─ XAResource.commit(xid) / rollback(xid) em todos os bancos
 *
 * Estado da transação é mantido por thread (ThreadLocal), como um TM real faria
 * para suportar múltiplas transações concorrentes.
 */
@Component
public class XATransactionCoordinator {

    private final ThreadLocal<Xid>             currentXid = new ThreadLocal<>();
    private final ThreadLocal<List<XAConnection>> enlisted =
            ThreadLocal.withInitial(ArrayList::new);

    // ─────────────────────────────────────────────────────────────────────────
    // begin
    // ─────────────────────────────────────────────────────────────────────────

    public void begin() {
        Xid xid = new SimpleXid();
        currentXid.set(xid);
        enlisted.set(new ArrayList<>());
        log("begin() → " + xid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // enlist — registra um Resource Manager na transação atual
    // ─────────────────────────────────────────────────────────────────────────

    public void enlist(XAConnection xaConn) throws XAException, SQLException {
        Xid xid = currentXid.get();
        XAResource res = xaConn.getXAResource();
        res.start(xid, XAResource.TMNOFLAGS);
        enlisted.get().add(xaConn);
        log("enlist() → " + res.getClass().getSimpleName() + " iniciado com xid=" + xid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // delistAll — sinaliza fim do trabalho SQL em cada RM
    // ─────────────────────────────────────────────────────────────────────────

    public void delistAll() throws XAException, SQLException {
        Xid xid = currentXid.get();
        for (XAConnection xaConn : enlisted.get()) {
            xaConn.getXAResource().end(xid, XAResource.TMSUCCESS);
        }
        log("end(TMSUCCESS) → " + enlisted.get().size() + " recurso(s) delistado(s)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 1: prepare — cada RM vota se pode commitar
    // ─────────────────────────────────────────────────────────────────────────

    public boolean prepare() throws XAException, SQLException {
        log("══════════════════════════════");
        log("FASE 1 — PREPARE (votação)");
        log("══════════════════════════════");

        Xid xid = currentXid.get();
        for (XAConnection xaConn : enlisted.get()) {
            XAResource res = xaConn.getXAResource();
            int vote = res.prepare(xid);
            String voteLabel = switch (vote) {
                case XAResource.XA_OK    -> "XA_OK    ✓ (pode commitar)";
                case XAResource.XA_RDONLY -> "XA_RDONLY (só leitura, nada a commitar)";
                default                  -> "ABORT    ✗";
            };
            log("prepare() → " + res.getClass().getSimpleName() + " votou: " + voteLabel);

            if (vote != XAResource.XA_OK && vote != XAResource.XA_RDONLY) {
                log("Voto negativo recebido — decisão: ROLLBACK");
                return false;
            }
        }
        log("Todos votaram OK — decisão: COMMIT");
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 2a: commit
    // ─────────────────────────────────────────────────────────────────────────

    public void commit() throws XAException, SQLException {
        log("══════════════════════════════");
        log("FASE 2 — COMMIT");
        log("══════════════════════════════");

        Xid xid = currentXid.get();
        for (XAConnection xaConn : enlisted.get()) {
            XAResource res = xaConn.getXAResource();
            res.commit(xid, false);
            log("commit() → " + res.getClass().getSimpleName() + " commitado");
        }
        cleanup();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 2b: rollback
    // ─────────────────────────────────────────────────────────────────────────

    public void rollback() throws XAException, SQLException {
        log("══════════════════════════════");
        log("ROLLBACK (sem prepare ou voto negativo)");
        log("══════════════════════════════");

        Xid xid = currentXid.get();
        for (XAConnection xaConn : enlisted.get()) {
            XAResource res = xaConn.getXAResource();
            res.rollback(xid);
            log("rollback() → " + res.getClass().getSimpleName() + " desfeito");
        }
        cleanup();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void cleanup() throws SQLException {
        for (XAConnection xaConn : enlisted.get()) {
            xaConn.close();
        }
        enlisted.get().clear();
        currentXid.remove();
        enlisted.remove();
    }

    private void log(String msg) {
        System.out.println("[TM] " + msg);
    }
}
