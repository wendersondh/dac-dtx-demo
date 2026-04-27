package com.example.demo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação MongoDB do UserDao.
 *
 * Usa o driver MongoDB (mongodb-driver-sync) diretamente, sem Spring Data MongoDB,
 * mantendo o padrão do projeto de expor as operações de persistência sem abstrações.
 *
 * Comparativo entre as implementações:
 *
 * | Conceito        | JPA (UserJpaDao)          | JDBC (UserJdbcDao)         | Mongo (UserMongoDao)         |
 * |-----------------|---------------------------|----------------------------|------------------------------|
 * | Conexão         | EntityManager             | DataSource / Connection    | MongoClient / MongoCollection|
 * | Inserção        | entityManager.persist()   | PreparedStatement INSERT   | collection.insertOne(doc)    |
 * | Busca           | entityManager.find()      | SELECT + ResultSet         | collection.find(filter)      |
 * | Transação       | @Transactional (AOP)      | setAutoCommit/commit       | session.startTransaction()   |
 * | Linguagem query | JPQL                      | SQL                        | BSON / Filter                |
 * | Schema          | gerado pelo Hibernate     | criado manualmente         | schema-less (documento livre)|
 *
 * Nota sobre ID: MongoDB usa ObjectId por padrão. Para manter compatibilidade
 * com a interface UserDao (que usa Long), geramos um ID incremental simples.
 * Em produção, use ObjectId ou um gerador distribuído (Snowflake, ULID).
 */
@Repository
@Primary
@Qualifier("mongo")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "mongo")
public class UserMongoDao implements UserDao {

    private final MongoCollection<Document> collection;

    // Gerador de ID simples para compatibilidade com a interface (Long id)
    private final AtomicLong idCounter = new AtomicLong(1);

    public UserMongoDao(MongoClient mongoClient,
                        @Value("${app.mongo.database}") String database) {
        this.collection = mongoClient.getDatabase(database).getCollection("users");
    }

    /**
     * Insere um documento na coleção "users".
     * Gera um ID Long incremental para compatibilidade com a interface.
     */
    @Override
    public void save(UserEntity user) {
        if (user.getId() == null) {
            user.setId(idCounter.getAndIncrement());
        }
        Document doc = toDocument(user);
        collection.insertOne(doc);
    }

    /**
     * Busca um documento por id (campo "id", não o _id do Mongo).
     */
    @Override
    public UserEntity findById(Long id) {
        Document doc = collection.find(Filters.eq("id", id)).first();
        return doc != null ? toEntity(doc) : null;
    }

    /**
     * Retorna todos os documentos da coleção como lista de UserEntity.
     */
    @Override
    public List<UserEntity> findAll() {
        List<UserEntity> result = new ArrayList<>();
        for (Document doc : collection.find()) {
            result.add(toEntity(doc));
        }
        return result;
    }

    /**
     * Atualiza name e email de um documento existente.
     */
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

    /**
     * Remove um documento pelo id.
     */
    @Override
    public void delete(Long id) {
        collection.deleteOne(Filters.eq("id", id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapeamento UserEntity ↔ Document (Serialização manual, sem frameworks)
    // ─────────────────────────────────────────────────────────────────────────

    private Document toDocument(UserEntity user) {
        return new Document("id",    user.getId())
                .append("name",  user.getName())
                .append("email", user.getEmail());
    }

    private UserEntity toEntity(Document doc) {
        UserEntity user = new UserEntity();
        user.setId(doc.getLong("id"));
        user.setName(doc.getString("name"));
        user.setEmail(doc.getString("email"));
        return user;
    }
}
