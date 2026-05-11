package com.example.demo;

/**
 * Contrato de um participante no protocolo 2PC (Two-Phase Commit) simulado.
 *
 * Cada banco de dados (H2 e MongoDB) possui uma implementação desta interface.
 * O DTxCoord (coordenador) chama prepare() nos dois participantes e, com base
 * nos votos, decide commitar ou reverter a transação em ambos.
 *
 * Fluxo do protocolo:
 *
 *   FASE 1 — Prepare (votação):
 *     coordinator → prepare(user)  → H2
 *     coordinator → prepare(user)  → MongoDB
 *     Ambos executam a operação mas NÃO confirmam; guardam estado pendente.
 *
 *   FASE 2 — Commit ou Rollback (decisão):
 *     Se ambos retornaram true  → coordinator → commit()   em ambos
 *     Se algum retornou false   → coordinator → rollback() em ambos
 */
public interface Participant {

    /**
     * FASE 1: executa a operação no banco, mas mantém a transação aberta
     * (sem commit). Retorna true se pronto para confirmar, false em caso de erro.
     *
     * @param user entidade a ser persistida
     * @return true (voto YES) se a operação foi preparada com sucesso
     */
    boolean prepare(UserEntity user);

    /** FASE 2a: confirma definitivamente a operação preparada. */
    void commit();

    /** FASE 2b: reverte a operação preparada, como se nunca tivesse ocorrido. */
    void rollback();
}
