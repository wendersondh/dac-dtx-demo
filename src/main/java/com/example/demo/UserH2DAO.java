package com.example.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Participante H2 do protocolo 2PC simulado.
 *
 * Diferença fundamental em relação ao UserJdbcDao:
 *   - prepare() abre uma conexão com autoCommit=false, executa o INSERT e
 *     MANTÉM a conexão aberta (sem commit), armazenada em ThreadLocal.
 *   - commit() e rollback() finalizam e liberam a conexão.
 *
 * Isso simula o comportamento de um Resource Manager real no protocolo 2PC:
 * o participante garante que pode commitar (escreveu em log durável, trava
 * os registros), mas aguarda a decisão final do coordenador.
 *
 * ThreadLocal garante isolamento entre requisições concorrentes, cada
 * thread gerenciando sua própria conexão pendente.
 */
@Component
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "dtx")
public class UserH2DAO implements Participant {

    private final DataSource dataSource;

    // Conexão mantida aberta entre prepare() e commit()/rollback()
    private final ThreadLocal<Connection> pendingConn = new ThreadLocal<>();

    public UserH2DAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Abre conexão, desliga autoCommit, executa INSERT e aguarda.
     * A conexão fica aberta (transação pendente) até commit() ou rollback().
     */
    @Override
    public boolean prepare(UserEntity user) {
        log("prepare() → abrindo conexão H2 e executando INSERT...");
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
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            ps.close();

            pendingConn.set(conn);
            log("prepare() → INSERT executado (voto: YES). Aguardando decisão...");
            return true;

        } catch (SQLException e) {
            log("prepare() → ERRO: " + e.getMessage() + " (voto: NO)");
            rollback();
            return false;
        }
    }

    // 2: confirma o INSERT pendente e libera a conexão.
    @Override
    public void commit() {
        Connection conn = pendingConn.get();
        if (conn == null) {
            log("commit() → nenhuma conexão pendente (ignorado)");
            return;
        }
        try {
            conn.commit();
            log("commit() → H2 confirmado com sucesso");
        } catch (SQLException e) {
            log("commit() → ERRO: " + e.getMessage());
            throw new RuntimeException("Falha no commit do H2", e);
        } finally {
            fecharConexao(conn);
        }
    }

    // 2: reverte o INSERT pendente e libera a conexão.
    @Override
    public void rollback() {
        Connection conn = pendingConn.get();
        if (conn == null) {
            log("rollback() → nenhuma conexão pendente (ignorado)");
            return;
        }
        try {
            conn.rollback();
            log("rollback() → H2 revertido com sucesso");
        } catch (SQLException e) {
            log("rollback() → ERRO no rollback: " + e.getMessage());
        } finally {
            fecharConexao(conn);
        }
    }


    // Operações de leitura

    public UserEntity findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário no H2", e);
        }
    }

    public List<UserEntity> findAll() {
        List<UserEntity> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, email FROM users")) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários no H2", e);
        }
        return users;
    }

    public void update(UserEntity user) {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setLong(3, user.getId());
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar usuário no H2", e);
        }
    }

    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            ps.setLong(1, id);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover usuário do H2", e);
        }
    }


    // Helpers

    private UserEntity mapRow(ResultSet rs) throws SQLException {
        UserEntity u = new UserEntity();
        u.setId(rs.getLong("id"));
        u.setName(rs.getString("name"));
        u.setEmail(rs.getString("email"));
        return u;
    }

    private void fecharConexao(Connection conn) {
        pendingConn.remove();
        try { conn.close(); } catch (SQLException ignored) {}
    }

    private void log(String msg) {
        System.out.println("[H2-DAO]    " + msg);
    }
}
