package com.example.demo;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gerenciamento de usuários.
 *
 * Fornece endpoints HTTP para realizar operações CRUD (Create, Read, Update, Delete)
 * sobre usuários. Todos os endpoints estão mapeados sob o caminho base "/users".
 *
 * A implementação do DAO usada é selecionada pela propriedade app.dao.impl
 * em application.properties ("jpa", "jdbc" ou "mongo").
 *
 * @author DAC
 * @version 1.0
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserDao userDao;

    public UserController(UserDao userDao, javax.persistence.EntityManagerFactory emf) {
        this.userDao = userDao;
    }

    /**
     * Cria um novo usuário.
     *
     * @param user Objeto UserEntity contendo os dados do novo usuário
     *             (JSON no corpo da requisição)
     */
    @PostMapping
    public void createUser(@RequestBody UserEntity user) {
        userDao.save(user);
    }

    /**
     * Recupera um usuário específico por ID.
     *
     * @param id Identificador único do usuário
     * @return Objeto UserEntity correspondente ao ID fornecido
     */
    @GetMapping("/{id}")
    public UserEntity getUser(@PathVariable Long id) {
        return userDao.findById(id);
    }

    /**
     * Recupera todos os usuários cadastrados.
     *
     * @return Lista contendo todos os usuários
     */
    @GetMapping
    public List<UserEntity> getAllUsers() {
        return userDao.findAll();
    }

    /**
     * Atualiza um usuário existente.
     *
     * @param id Identificador único do usuário a ser atualizado
     * @param user Objeto UserEntity contendo os novos dados
     *             (JSON no corpo da requisição)
     */
    @PutMapping("/{id}")
    public void updateUser(@PathVariable Long id, @RequestBody UserEntity user) {
        // TODO: para JPA é necessário recuperar a entidade do banco antes de atualizar; 
        // para JDBC e MongoDB, pode-se atualizar diretamente
        user.setId(id);
        // usar reflection para recuperar o tipo
        System.out.println("User class: " + user.getClass().getName());
        userDao.update(user);
    }

    /**
     * Remove um usuário do banco de dados.
     *
     * @param id Identificador único do usuário a ser removido
     */
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userDao.delete(id);
    }
}
