package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação JDBC do UserDao — também atua como participante 2PC.
 *
 * Ativa para: jdbc | dtx | saga | outbox
 *
 * Modo standalone (jdbc):
 *   save() abre conexão, executa INSERT e comita tudo na mesma chamada.
 *
 * Modo 2PC (dtx) — interface Participant:
 *   prepare() executa INSERT com autoCommit=false e mantém a conexão aberta
 *   no ThreadLocal, sem commitar. commit()/rollback() finalizam a transação.
 *
 * Comparativo com UserJpaDao:
 *   @Transactional         → conn.setAutoCommit(false) + commit()/rollback()
 *   entityManager.persist  → PreparedStatement.executeUpdate()
 *   entityManager.find     → PreparedStatement + ResultSet
 */
@Repository
@Qualifier("jdbc")
@ConditionalOnExpression(
    "'${app.dao.impl:jpa}'.equals('jdbc')   or " +
    "'${app.dao.impl:jpa}'.equals('dtx')    or " +
    "'${app.dao.impl:jpa}'.equals('saga')   or " +
    "'${app.dao.impl:jpa}'.equals('outbox')"
)
public class UserJdbcDao implements UserDao, Participant {

    private final DataSource dataSource;

    // Conexão mantida aberta entre prepare() e commit()/rollback() no modo 2PC
    private final ThreadLocal<Connection> pendingConn = new ThreadLocal<>();

    public UserJdbcDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UserDao — operações standalone (modo jdbc)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void save(UserEntity user) {
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getName());
                ps.setString(2, user.getEmail());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) user.setId(keys.getLong(1));
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar usuário", e);
        }
    }

    @Override
    public UserEntity findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário", e);
        }
    }

    @Override
    public List<UserEntity> findAll() {
        List<UserEntity> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, email FROM users")) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários", e);
        }
        return users;
    }

    @Override
    public void update(UserEntity user) {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.getName());
                ps.setString(2, user.getEmail());
                ps.setLong(3, user.getId());
                ps.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar usuário", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover usuário", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Participant — protocolo 2PC (modo dtx)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FASE 1: abre conexão, desliga autoCommit, executa INSERT e aguarda.
     * A conexão fica aberta no ThreadLocal até commit() ou rollback() serem chamados.
     */
    @Override
    public boolean prepare(UserEntity user) {
        log("prepare() → executando INSERT...");
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) user.setId(keys.getLong(1));
            }
            ps.close();

            pendingConn.set(conn);
            log("prepare() → voto YES (conexão aberta, aguardando decisão)");
            return true;

        } catch (SQLException e) {
            log("prepare() → voto NO: " + e.getMessage());
            rollback();
            return false;
        }
    }

    @Override
    public void commit() {
        Connection conn = pendingConn.get();
        if (conn == null) return;
        try {
            conn.commit();
            log("commit() → confirmado");
        } catch (SQLException e) {
            throw new RuntimeException("Falha no commit JDBC", e);
        } finally {
            fechar(conn);
        }
    }

    @Override
    public void rollback() {
        Connection conn = pendingConn.get();
        if (conn == null) return;
        try {
            conn.rollback();
            log("rollback() → revertido");
        } catch (SQLException e) {
            log("rollback() → erro: " + e.getMessage());
        } finally {
            fechar(conn);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private UserEntity mapRow(ResultSet rs) throws SQLException {
        UserEntity u = new UserEntity();
        u.setId(rs.getLong("id"));
        u.setName(rs.getString("name"));
        u.setEmail(rs.getString("email"));
        return u;
    }

    private void fechar(Connection conn) {
        pendingConn.remove();
        try { conn.close(); } catch (SQLException ignored) {}
    }

    private void log(String msg) {
        System.out.println("[JDBC-DAO]  " + msg);
    }
}