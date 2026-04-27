package com.example.demo;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Entidade JPA que representa um usuário no banco de dados.
 *
 * Mapeia a tabela "users" do banco de dados, contendo informações básicas
 * de um usuário como identificador, nome e endereço de email.
 *
 * @author DAC
 * @version 1.0
 */
@Entity
@Table(name = "users")
public class UserEntity {

    /** Identificador único do usuário (chave primária, auto-incrementada) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome do usuário */
    private String name;

    /** Endereço de email do usuário */
    private String email;

    /**
     * Construtor padrão (necessário para JPA).
     */
    public UserEntity() {
    }

    /**
     * Construtor com parâmetros para criar um novo usuário.
     *
     * @param name Nome do usuário
     * @param email Email do usuário
     */
    public UserEntity(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /**
     * Retorna o ID do usuário.
     *
     * @return Identificador único do usuário
     */
    public Long getId() {
        return id;
    }

    /**
     * Define o ID do usuário.
     *
     * @param id Novo identificador
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna o nome do usuário.
     *
     * @return Nome do usuário
     */
    public String getName() {
        return name;
    }

    /**
     * Define o nome do usuário.
     *
     * @param name Novo nome
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retorna o email do usuário.
     *
     * @return Email do usuário
     */
    public String getEmail() {
        return email;
    }

    /**
     * Define o email do usuário.
     *
     * @param email Novo email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Retorna uma representação em texto do usuário.
     *
     * @return String no formato "UserEntity [id=..., name=..., email=...]"
     */
    @Override
    public String toString() {
        return "UserEntity [id=" + id + ", name=" + name + ", email=" + email + "]";
    }

    /**
     * Compara este usuário com outro objeto.
     *
     * Dois usuários são considerados iguais se possuem o mesmo ID.
     *
     * @param obj Objeto a ser comparado
     * @return true se os usuários são iguais, false caso contrário
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserEntity other = (UserEntity) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    /**
     * Retorna o código hash do usuário.
     *
     * Baseado no ID do usuário para consistência com o método equals().
     *
     * @return Código hash
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }
}
