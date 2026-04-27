package com.example.demo;

import java.lang.instrument.ClassDefinition;
import java.sql.Driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação Spring Boot.
 *
 * Responsável por inicializar e executar a aplicação de gerenciamento de usuários.
 * Esta aplicação demonstra o uso de JPA para persistência de dados,
 * incluindo operações CRUD básicas e configurações de transação.
 *
 * @author DAC
 * @version 1.0
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * Ponto de entrada da aplicação.
     *
     * Inicia o contexto do Spring Boot e carrega todas as configurações,
     * incluindo a injeção de dependências e o servidor web embarcado.
     *
     * @param args Argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
