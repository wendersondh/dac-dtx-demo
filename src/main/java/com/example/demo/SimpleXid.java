package com.example.demo;

import javax.transaction.xa.Xid;
import java.util.UUID;

/**
 * Implementação didática do Xid (identificador de transação XA).
 *
 * Um Xid real de um Transaction Manager (ex: Atomikos) contém:
 * - formatId:            identifica o formato/versão do protocolo
 * - globalTransactionId: identifica a transação global (mesmo valor em todos os RMs)
 * - branchQualifier:     identifica o ramo dentro da transação (único por RM)
 *
 * O TM garante que o Xid sobreviva a falhas (gravado em log durável),
 * permitindo recuperação após queda entre as fases 1 e 2.
 */
public class SimpleXid implements Xid {

    private static final int FORMAT_ID = 1;

    private final byte[] globalTransactionId;
    private final byte[] branchQualifier;

    public SimpleXid() {
        this.globalTransactionId = UUID.randomUUID().toString().replace("-", "").getBytes();
        this.branchQualifier    = "branch-01".getBytes();
    }

    @Override public int    getFormatId()            { return FORMAT_ID; }
    @Override public byte[] getGlobalTransactionId() { return globalTransactionId; }
    @Override public byte[] getBranchQualifier()     { return branchQualifier; }

    @Override
    public String toString() {
        return "Xid[" + new String(globalTransactionId) + "]";
    }
}
