package com.example.demo;


import javax.persistence.*;


/**
 * Entidade JPA que representa um evento pendente na tabela outbox.
 *
 * O Hibernate cria a tabela automaticamente (ddl-auto=update).
 * O OutboxRelay lê registros PENDING e os propaga para o MongoDB.
 */
@Entity
@Table(name = "outbox")
public class OutboxEntry {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "user_id")
    private Long userId;


    private String name;
    private String email;
    private String status;


    public Long getId()                { return id; }
    public Long getUserId()            { return userId; }
    public String getName()            { return name; }
    public String getEmail()           { return email; }
    public String getStatus()          { return status; }
    public void setId(Long id)         { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setName(String name)   { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(String s)    { this.status = s; }
}

