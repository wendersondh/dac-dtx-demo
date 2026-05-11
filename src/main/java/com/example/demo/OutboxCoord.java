package com.example.demo;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;


import javax.sql.DataSource;
import java.sql.*;
import java.util.List;


/**
 * Coordenador Outbox — implementação manual sem frameworks.
 *
 * Ativa quando app.dao.impl=outbox.
 *
 * Fluxo:
 *   1. Em UMA transação local no H2:
 *      a. INSERT em users (tabela principal)
 *      b. INSERT em outbox (tabela de eventos pendentes)
 *   2. OutboxRelay (job agendado) lê a tabela outbox e propaga para o MongoDB.
 *      Após sucesso, marca o registro como DONE.
 *
 * Garantia:
 *   Se a aplicação cair entre o passo 1 e 2, o outbox garante que o registro
 *   será propagado quando o relay rodar novamente (at-least-once delivery).
 *
 * Diferença em relação ao 2PC e SAGA:
 *   O MongoDB nunca é acessado diretamente no save() — a propagação é assíncrona
 *   e desacoplada via tabela intermediária no banco local.
 */
@Repository
@Primary
@Qualifier("outbox")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "outbox")
public class OutboxCoord implements UserDao {


    private final DataSource dataSource;


    public OutboxCoord(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @Override
    public void save(UserEntity user) {
        log("══════════════════════════════════════");
        log("OUTBOX — INÍCIO");
        log("══════════════════════════════════════");


        // Transação local única: INSERT users + INSERT outbox
        String insertUser   = "INSERT INTO users (name, email) VALUES (?, ?)";
        String insertOutbox = "INSERT INTO outbox (user_id, name, email, status) VALUES (?, ?, ?, 'PENDING')";


        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Passo 1: salva o usuário na tabela principal
                long userId;
                try (PreparedStatement ps = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, user.getName());
                    ps.setString(2, user.getEmail());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        userId = keys.getLong(1);
                        user.setId(userId);
                    }
                }


                // Passo 2: registra evento pendente na tabela outbox
                try (PreparedStatement ps = conn.prepareStatement(insertOutbox)) {
                    ps.setLong(1, userId);
                    ps.setString(2, user.getName());
                    ps.setString(3, user.getEmail());
                    ps.executeUpdate();
                }


                conn.commit();
                log("Usuário id=" + userId + " salvo no H2 e evento registrado no outbox.");
                log("OutboxRelay propagará para o MongoDB em breve.");


            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar no outbox", e);
        }


        log("══════════════════════════════════════");
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
        List<UserEntity> users = new java.util.ArrayList<>();
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


    private UserEntity mapRow(ResultSet rs) throws SQLException {
        UserEntity u = new UserEntity();
        u.setId(rs.getLong("id"));
        u.setName(rs.getString("name"));
        u.setEmail(rs.getString("email"));
        return u;
    }


    private void log(String msg) { System.out.println("[OutboxCoord] " + msg); }
}

