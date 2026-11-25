-- Unidade da pessoa pela faixa de IP: a mesma comparação do inventário (Faixas.java), em números.
-- Antes: o IP ia direto para ::inet; um espaço sobrando no IP, um zero à esquerda ("172.21.009.105") ou uma faixa
-- que o PostgreSQL não convertesse fazia a função cair no EXCEPTION e devolver "sem unidade" — a máquina aparecia na
-- unidade no Inventário e "fora das faixas" na tela de usuários. Agora faixa inválida é só ignorada.

-- "172.21.9.105" (com ou sem espaços) → número; inválido → NULL
CREATE OR REPLACE FUNCTION cmdb.ip_numero(p_ip text) RETURNS bigint
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    s text := btrim(coalesce(p_ip, ''));
    o text[];
    n bigint := 0;
    i int;
BEGIN
    IF s !~ '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$' THEN
        RETURN NULL;
    END IF;
    o := string_to_array(s, '.');
    FOR i IN 1..4 LOOP
        IF o[i]::int > 255 THEN
            RETURN NULL;
        END IF;
        n := n * 256 + o[i]::int;
    END LOOP;
    RETURN n;
END
$$;

CREATE OR REPLACE FUNCTION cmdb.unidade_do_usuario(p_usuario bigint) RETURNS bigint
LANGUAGE plpgsql STABLE AS $$
DECLARE
    a record;
    n bigint;
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
    n := cmdb.ip_numero(a.ip);
    IF n IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN (
        SELECT f.dept_id
        FROM (
            SELECT dept_id, cmdb.ip_numero(split_part(cidr, '/', 1)) AS rede,
                   CASE WHEN position('/' IN cidr) = 0
                            THEN CASE WHEN cmdb.ip_numero(cidr) % 256 = 0 THEN 24 ELSE 32 END
                        WHEN btrim(split_part(cidr, '/', 2)) ~ '^\d{1,2}$' THEN btrim(split_part(cidr, '/', 2))::int
                   END AS prefixo
            FROM cmdb.unidade_rede
        ) f
        WHERE f.rede IS NOT NULL AND f.prefixo BETWEEN 8 AND 32
          AND n / power(2, 32 - f.prefixo)::bigint = f.rede / power(2, 32 - f.prefixo)::bigint
        ORDER BY f.prefixo DESC
        LIMIT 1
    );
END
$$;
