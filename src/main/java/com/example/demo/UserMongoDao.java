package com.example.demo;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação MongoDB do UserDao — também atua como participante 2PC.
 *
 * Ativa para: mongo | dtx | saga | outbox
 *
 * Modo standalone (mongo):
 *   save() faz insertOne direto, sem sessão de transação.
 *
 * Modo 2PC (dtx) — interface Participant:
 *   prepare() abre ClientSession, inicia transação, executa insertOne e mantém
 *   a sessão aberta no ThreadLocal, sem commitar.
 *   commit()/rollback() finalizam a transação e fecham a sessão.
 *
 * Nota: MongoDB Atlas (replica set) suporta transações via ClientSession.
 * O dado inserido via sessão NÃO fica visível a outros leitores até o commit.
 */
@Repository
@Qualifier("mongo")
@ConditionalOnExpression(
    "'${app.dao.impl:jpa}'.equals('mongo')  or " +
    "'${app.dao.impl:jpa}'.equals('dtx')    or " +
    "'${app.dao.impl:jpa}'.equals('saga')   or " +
    "'${app.dao.impl:jpa}'.equals('outbox')"
)
public class UserMongoDao implements UserDao, Participant {

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    // Sessão mantida aberta entre prepare() e commit()/rollback() no modo 2PC
    private final ThreadLocal<ClientSession> pendingSession = new ThreadLocal<>();

    private final AtomicLong idCounter = new AtomicLong(1);

    public UserMongoDao(MongoClient mongoClient,
                        @Value("${app.mongo.database}") String database) {
        this.mongoClient = mongoClient;
        this.collection = mongoClient.getDatabase(database).getCollection("users");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UserDao — operações standalone (modo mongo)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void save(UserEntity user) {
        if (user.getId() == null) user.setId(idCounter.getAndIncrement());
        collection.insertOne(toDocument(user));
    }

    @Override
    public UserEntity findById(Long id) {
        Document doc = collection.find(Filters.eq("id", id)).first();
        return doc != null ? toEntity(doc) : null;
    }

    @Override
    public List<UserEntity> findAll() {
        List<UserEntity> result = new ArrayList<>();
        for (Document doc : collection.find()) result.add(toEntity(doc));
        return result;
    }

    @Override
    public void update(UserEntity user) {
        collection.updateOne(
                Filters.eq("id", user.getId()),
                Updates.combine(
                        Updates.set("name",  user.getName()),
                        Updates.set("email", user.getEmail())
                )
        );
    }

    @Override
    public void delete(Long id) {
        collection.deleteOne(Filters.eq("id", id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Participant — protocolo 2PC (modo dtx)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FASE 1: abre sessão, inicia transação MongoDB, executa insertOne e aguarda.
     * A sessão fica aberta no ThreadLocal até commit() ou rollback() serem chamados.
     */
    @Override
    public boolean prepare(UserEntity user) {
        log("prepare() → iniciando sessão e executando insertOne...");
        try {
            ClientSession session = mongoClient.startSession();
            session.startTransaction();

            if (user.getId() == null) user.setId(idCounter.getAndIncrement());
            collection.insertOne(session, toDocument(user));

            pendingSession.set(session);
            log("prepare() → voto YES (sessão aberta, aguardando decisão)");
            return true;

        } catch (Exception e) {
            log("prepare() → voto NO: " + e.getMessage());
            rollback();
            return false;
        }
    }

    @Override
    public void commit() {
        ClientSession session = pendingSession.get();
        if (session == null) return;
        try {
            session.commitTransaction();
            log("commit() → confirmado");
        } catch (Exception e) {
            throw new RuntimeException("Falha no commit MongoDB", e);
        } finally {
            fechar(session);
        }
    }

    @Override
    public void rollback() {
        ClientSession session = pendingSession.get();
        if (session == null) return;
        try {
            if (session.hasActiveTransaction()) session.abortTransaction();
            log("rollback() → revertido");
        } catch (Exception e) {
            log("rollback() → erro: " + e.getMessage());
        } finally {
            fechar(session);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapeamento UserEntity ↔ Document
    // ─────────────────────────────────────────────────────────────────────────

    private Document toDocument(UserEntity user) {
        return new Document("id", user.getId())
                .append("name",  user.getName())
                .append("email", user.getEmail());
    }

    private UserEntity toEntity(Document doc) {
        UserEntity u = new UserEntity();
        u.setId(doc.getLong("id"));
        u.setName(doc.getString("name"));
        u.setEmail(doc.getString("email"));
        return u;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void fechar(ClientSession session) {
        pendingSession.remove();
        try { session.close(); } catch (Exception ignored) {}
    }

    private void log(String msg) {
        System.out.println("[MONGO-DAO] " + msg);
    }
}