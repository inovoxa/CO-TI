-- Inventário: todos os IPs da máquina e a unidade sem palpite.
-- A unidade de um ativo é a escolhida à mão; senão, a da faixa (Unidades e endereços / Inventário → Rede) do primeiro
-- IP da máquina que cair numa faixa (o principal, depois os das outras placas); senão, nenhuma. A unidade da pessoa
-- logada deixa de ser usada como palpite.

-- IPs da máquina, o principal primeiro, separados por vírgula
ALTER TABLE cmdb.ativo ADD COLUMN ips varchar(1000) NULL;

-- preenche com o que já está nas placas de rede (a descrição traz o IP); as físicas antes das virtuais
UPDATE cmdb.ativo a SET ips = x.ips
FROM (
    SELECT t.id, string_agg(t.ip, ',' ORDER BY t.ordem, t.pos) AS ips
    FROM (
        SELECT DISTINCT ON (id, ip) id, ip, ordem, pos FROM (
            SELECT a.id, btrim(a.ip) AS ip, 0 AS ordem, 0::bigint AS pos FROM cmdb.ativo a WHERE a.ip IS NOT NULL
            UNION ALL
            SELECT c.ativo_id, m[1],
                   CASE WHEN c.descricao ~* '(virtual|loopback|vpn|hyper-v)' THEN 2 ELSE 1 END, c.id
            FROM cmdb.ativo_componente c, regexp_matches(c.descricao, '(\d{1,3}(?:\.\d{1,3}){3})', 'g') AS m
            WHERE c.tipo = 'rede' AND c.deleted = 0
        ) u
        WHERE cmdb.ip_numero(ip) IS NOT NULL AND ip NOT LIKE '127.%' AND ip NOT LIKE '169.254.%' AND ip <> '0.0.0.0'
        ORDER BY id, ip, ordem, pos
    ) t
    GROUP BY t.id
) x
WHERE x.id = a.id;

-- Unidade da primeira faixa em que um dos IPs cai (a mais específica para aquele IP); null se nenhum cai
CREATE OR REPLACE FUNCTION cmdb.unidade_dos_ips(p_ips text) RETURNS bigint
LANGUAGE plpgsql STABLE AS $$
DECLARE
    ip text;
    n bigint;
    d bigint;
BEGIN
    IF p_ips IS NULL THEN
        RETURN NULL;
    END IF;
    FOREACH ip IN ARRAY string_to_array(p_ips, ',') LOOP
        n := cmdb.ip_numero(ip);
        CONTINUE WHEN n IS NULL;
        SELECT f.dept_id INTO d
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
        LIMIT 1;
        IF d IS NOT NULL THEN
            RETURN d;
        END IF;
    END LOOP;
    RETURN NULL;
END
$$;

-- Unidade da pessoa (tela de usuários): a da máquina dela pela mesma regra
CREATE OR REPLACE FUNCTION cmdb.unidade_do_usuario(p_usuario bigint) RETURNS bigint
LANGUAGE plpgsql STABLE AS $$
DECLARE
    a record;
BEGIN
    SELECT ip, ips, dept_id, dept_manual INTO a FROM cmdb.ativo
    WHERE usuario_id = p_usuario AND deleted = 0 AND situacao <> 'baixado' AND mesclado_em IS NULL
    ORDER BY ultimo_contato DESC NULLS LAST, id DESC LIMIT 1;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    IF a.dept_manual THEN
        RETURN a.dept_id;
    END IF;
    RETURN cmdb.unidade_dos_ips(coalesce(a.ips, a.ip));
END
$$;
