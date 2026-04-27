package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação JDBC do UserDao.
 *
 * Usa DataSource/Connection diretamente, sem JPA, para tornar visível
 * o que o EntityManager abstrai: abertura de conexão, preparação de statements,
 * mapeamento de ResultSet e controle manual de transação.
 *
 * Comparativo com UserJpaDao:
 * - @Transactional         → conn.setAutoCommit(false) + commit() / rollback()
 * - entityManager.persist  → PreparedStatement.executeUpdate()
 * - entityManager.find     → PreparedStatement + ResultSet
 * - isolation level        → conn.setTransactionIsolation(Connection.TRANSACTION_*)
 *
 * @author DAC
 * @version 1.0
 */
@Repository
@Primary
@Qualifier("jdbc")
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "jdbc")
public class UserJdbcDao implements UserDao {

    // DataSource é injetado pelo Spring, configurado para usar o mesmo banco H2 in-memory
    private final DataSource dataSource;

    public UserJdbcDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Persiste um novo usuário via INSERT.
     * Recupera a chave gerada e popula o campo id da entidade.
     */
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
                    if (keys.next()) {
                        user.setId(keys.getLong(1));
                    }
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

    /**
     * Recupera um usuário pelo ID via SELECT.
     *
     * Nota: equivalente ao findById com READ_UNCOMMITTED do UserJpaDao.
     * Para reproduzir o mesmo comportamento em JDBC:
     *   conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
     */
    @Override
    public UserEntity findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário", e);
        }
    }

    /**
     * Recupera todos os usuários via SELECT.
     */
    @Override
    public List<UserEntity> findAll() {
        String sql = "SELECT id, name, email FROM users";
        List<UserEntity> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários", e);
        }
    }

    /**
     * Atualiza name e email de um usuário existente via UPDATE.
     *
     * Nota: sem @Transactional — assim como no UserJpaDao, intencional para
     * fins didáticos. O controle é feito manualmente via commit/rollback.
     */
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

    /**
     * Remove um usuário via DELETE.
     */
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
        UserEntity user = new UserEntity();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        return user;
    }
}
