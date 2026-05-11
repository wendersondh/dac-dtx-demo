package com.example.demo;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Participante MongoDB do protocolo 2PC simulado.
 *
 * O MongoDB Atlas (replica set) suporta transações multi-documento via
 * ClientSession. Isso permite simular a fase de prepare do 2PC:
 *
 *   prepare():
 *     1. startSession()         — abre uma sessão de transação
 *     2. startTransaction()     — inicia a transação na sessão
 *     3. insertOne(session, doc)— executa o insert dentro da sessão
 *     4. NÃO comita             — mantém sessão no ThreadLocal
 *
 *   commit():   session.commitTransaction() → confirma tudo de uma vez
 *   rollback(): session.abortTransaction()  → descarta tudo
 *
 * Diferença em relação ao UserMongoDao:
 *   - Aquele faz insert direto (sem sessão, sem transação explícita)
 *   - Este mantém a sessão aberta (prepare) até a decisão do coordenador
 *
 * Nota: MongoDB não implementa o protocolo XA/JTA, mas o uso de ClientSession
 * com startTransaction/commitTransaction nos dá semântica equivalente à fase
 * de prepare do 2PC: o dado está "quase lá", mas não visível a outros leitores
 * até o commit ser chamado.
 */
@Component
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "dtx")
public class UserMongoDAO implements Participant {

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    // Sessão de transação mantida aberta entre prepare() e commit()/rollback()
    private final ThreadLocal<ClientSession> pendingSession = new ThreadLocal<>();

    // Gerador de ID numérico para compatibilidade com a interface (Long id)
    private final AtomicLong idCounter = new AtomicLong(1);

    public UserMongoDAO(MongoClient mongoClient,
                        @Value("${app.mongo.database}") String database) {
        this.mongoClient = mongoClient;
        this.collection = mongoClient.getDatabase(database).getCollection("users");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROTOCOLO 2PC
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 1: inicia sessão + transação no MongoDB, executa insertOne() e aguarda.
     * A sessão fica aberta (transação pendente) até commit() ou rollback().
     *
     * O documento inserido NÃO está visível a outras leituras enquanto a
     * transação não for commitada — mesma garantia de isolamento do H2.
     */
    @Override
    public boolean prepare(UserEntity user) {
        log("prepare() → iniciando sessão MongoDB e executando insertOne...");
        try {
            ClientSession session = mongoClient.startSession();
            session.startTransaction();

            if (user.getId() == null) {
                user.setId(idCounter.getAndIncrement());
            }
            Document doc = toDocument(user);
            collection.insertOne(session, doc);

            pendingSession.set(session);
            log("prepare() → insertOne executado (voto: YES). Aguardando decisão...");
            return true;

        } catch (Exception e) {
            log("prepare() → ERRO: " + e.getMessage() + " (voto: NO)");
            rollback();
            return false;
        }
    }

    /** FASE 2a: confirma o insertOne pendente e fecha a sessão. */
    @Override
    public void commit() {
        ClientSession session = pendingSession.get();
        if (session == null) {
            log("commit() → nenhuma sessão pendente (ignorado)");
            return;
        }
        try {
            session.commitTransaction();
            log("commit() → MongoDB confirmado com sucesso");
        } catch (Exception e) {
            log("commit() → ERRO: " + e.getMessage());
            throw new RuntimeException("Falha no commit do MongoDB", e);
        } finally {
            fecharSessao(session);
        }
    }

    /** FASE 2b: aborta a transação pendente e fecha a sessão. */
    @Override
    public void rollback() {
        ClientSession session = pendingSession.get();
        if (session == null) {
            log("rollback() → nenhuma sessão pendente (ignorado)");
            return;
        }
        try {
            if (session.hasActiveTransaction()) {
                session.abortTransaction();
            }
            log("rollback() → MongoDB revertido com sucesso");
        } catch (Exception e) {
            log("rollback() → ERRO no rollback: " + e.getMessage());
        } finally {
            fecharSessao(session);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operações de escrita sequencial
    // ─────────────────────────────────────────────────────────────────────────

    public void update(UserEntity user) {
        collection.updateOne(
                Filters.eq("id", user.getId()),
                Updates.combine(
                        Updates.set("name",  user.getName()),
                        Updates.set("email", user.getEmail())
                )
        );
        log("update() → documento id=" + user.getId() + " atualizado");
    }

    public void delete(Long id) {
        collection.deleteOne(Filters.eq("id", id));
        log("delete() → documento id=" + id + " removido");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapeamento UserEntity ↔ Document
    // ─────────────────────────────────────────────────────────────────────────

    private Document toDocument(UserEntity user) {
        return new Document("id",    user.getId())
                .append("name",  user.getName())
                .append("email", user.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void fecharSessao(ClientSession session) {
        pendingSession.remove();
        try { session.close(); } catch (Exception ignored) {}
    }

    private void log(String msg) {
        System.out.println("[MONGO-DAO] " + msg);
    }
}