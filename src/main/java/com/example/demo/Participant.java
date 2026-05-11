package com.example.demo;

/**
 * Contrato de um participante no protocolo 2PC simulado.
 *
 * Cada banco (JDBC e MongoDB) implementa esta interface.
 * O DTxCoord chama prepare() nos dois e, com base nos votos,
 * decide commitar ou reverter em ambos.
 *
 * Fase 1 — prepare(): executa a operação mas NÃO confirma.
 * Fase 2 — commit() ou rollback(): decisão final do coordenador.
 */
public interface Participant {
    boolean prepare(UserEntity user);
    void commit();
    void rollback();
}