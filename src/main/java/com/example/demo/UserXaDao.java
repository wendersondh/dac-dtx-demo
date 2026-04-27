package com.example.demo;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.sql.*;

/**
 * DAO que usa XADataSource para participar do protocolo 2PC.
 *
 * Diferença em relação ao UserJdbcDao:
 * - Não gerencia transação diretamente (sem setAutoCommit / commit / rollback)
 * - Obtém XAConnection e enlista no XATransactionCoordinator (o TM)
 * - O TM decide quando commitar ou desfazer via XAResource
 *
 * O XADataSource é criado internamente (não é um Spring bean) para não
 * conflitar com o DataSource gerenciado pelo Spring Boot. Ambos apontam
 * para o mesmo banco H2 in-memory (spring.datasource.url).
 *
 * Isso demonstra o ponto central do XA:
 *   a conexão executa o SQL, mas quem decide o destino da transação é o TM.
 */
@Repository
public class UserXaDao {

    private final XADataSource xaDataSource;
    private final XATransactionCoordinator coordinator;

    public UserXaDao(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password,
            XATransactionCoordinator coordinator) {

        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url + ";DB_CLOSE_DELAY=-1");
        ds.setUser(user);
        ds.setPassword(password);
        this.xaDataSource = ds;
        this.coordinator  = coordinator;
    }

    /**
     * Insere um usuário dentro da transação XA corrente.
     *
     * O XAConnection é aberto, enlistado no coordenador e mantido aberto
     * até o TM chamar commit() ou rollback(). O inner Connection é fechado
     * após o SQL, mas o XAResource permanece ativo.
     */
    public void save(UserEntity user) throws Exception {
        XAConnection xaConn = xaDataSource.getXAConnection();
        coordinator.enlist(xaConn);

        try (Connection conn = xaConn.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (name, email) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
        }

        System.out.println("[XA-DAO] INSERT executado para '" + user.getName()
                + "' — aguardando decisão do TM...");
    }
}
