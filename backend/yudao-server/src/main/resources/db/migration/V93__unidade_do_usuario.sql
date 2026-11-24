-- Unidade de uma pessoa = unidade da máquina que ela usa (a de contato mais recente no inventário):
-- a unidade dada à mão na máquina ou, se não houver, a faixa de IP mais específica que contém o IP dela.
-- A unidade que a máquina herdou da própria pessoa não conta (seria circular): sem faixa, fica sem unidade.
CREATE INDEX IF NOT EXISTS idx_cmdb_ativo_usuario ON cmdb.ativo (usuario_id) WHERE deleted = 0 AND usuario_id IS NOT NULL;

CREATE OR REPLACE FUNCTION cmdb.unidade_do_usuario(p_usuario bigint) RETURNS bigint
LANGUAGE plpgsql STABLE AS $$
DECLARE
    a record;
    d bigint;
BEGIN
    SELECT ip, dept_id, dept_manual INTO a FROM cmdb.ativo
    WHERE usuario_id = p_usuario AND deleted = 0 AND situacao <> 'baixado' AND mesclado_em IS NULL
    ORDER BY ultimo_contato DESC NULLS LAST, id DESC LIMIT 1;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    IF a.dept_manual THEN
        RETURN a.dept_id;
    END IF;
    IF a.ip IS NULL OR a.ip !~ '^\d{1,3}(\.\d{1,3}){3}$' THEN
        RETURN NULL;
    END IF;
    BEGIN
        SELECT f.dept_id INTO d FROM cmdb.unidade_rede f
        WHERE a.ip::inet <<= f.cidr::inet ORDER BY masklen(f.cidr::inet) DESC LIMIT 1;
    EXCEPTION WHEN others THEN
        d := NULL;
    END;
    RETURN d;
END
$$;
