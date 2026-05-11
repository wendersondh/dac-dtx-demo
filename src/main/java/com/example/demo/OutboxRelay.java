package com.example.demo;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import javax.sql.DataSource;
import java.sql.*;


/**
 * Relay assíncrono do padrão Outbox.
 *
 * Ativa quando app.dao.impl=outbox.
 *
 * A cada 5 segundos lê registros PENDING da tabela outbox e os propaga
 * para o MongoDB. Após propagação bem-sucedida, marca o registro como DONE.
 *
 * Tabela outbox (criada automaticamente pelo Hibernate via ddl-auto=update
 * a partir da entidade OutboxEntry):
 *
 *   outbox (id, user_id, name, email, status, created_at)
 *
 * Garantia at-least-once:
 *   Se a aplicação cair durante a propagação, o registro permanece PENDING
 *   e será reprocessado no próximo ciclo.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.dao.impl", havingValue = "outbox")
public class OutboxRelay {


    private final DataSource   dataSource;
    private final UserMongoDao mongoDao;


    public OutboxRelay(DataSource dataSource, UserMongoDao mongoDao) {
        this.dataSource = dataSource;
        this.mongoDao   = mongoDao;
    }


    @Scheduled(fixedDelay = 5000)
    public void relay() {
        String selectPending = "SELECT id, user_id, name, email FROM outbox WHERE status = 'PENDING'";
        String markDone      = "UPDATE outbox SET status = 'DONE' WHERE id = ?";


        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectPending)) {


            while (rs.next()) {
                long outboxId = rs.getLong("id");
                long userId   = rs.getLong("user_id");
                String name   = rs.getString("name");
                String email  = rs.getString("email");


                try {
                    // Propaga para o MongoDB
                    UserEntity user = new UserEntity(name, email);
                    user.setId(userId);
                    mongoDao.save(user);


                    // Marca como processado no H2
                    try (PreparedStatement ps = conn.prepareStatement(markDone)) {
                        ps.setLong(1, outboxId);
                        ps.executeUpdate();
                    }


                    log("outbox id=" + outboxId + " propagado → MongoDB (userId=" + userId + ")");


                } catch (Exception e) {
                    log("ERRO ao propagar outbox id=" + outboxId + ": " + e.getMessage());
                    // Permanece PENDING para reprocessamento no próximo ciclo
                }
            }


        } catch (SQLException e) {
            log("ERRO ao ler outbox: " + e.getMessage());
        }
    }


    private void log(String msg) { System.out.println("[OutboxRelay] " + msg); }
}

