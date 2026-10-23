-- Papel da aplicação (coti_app): só DML. O dono (coti) roda as migrations.
-- No Docker o papel é criado com senha por deploy/postgres/050_papel_aplicacao.sh; fora dele, crie com
--   CREATE ROLE coti_app LOGIN PASSWORD '...';
-- Se ainda não existir, criamos sem login para as permissões abaixo já ficarem prontas.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coti_app') THEN
        CREATE ROLE coti_app NOLOGIN;
    END IF;
END
$$;

DO $$
DECLARE
    s text;
BEGIN
    FOREACH s IN ARRAY ARRAY['public', 'itsm', 'cmdb', 'atendimento', 'monitor', 'logs', 'cofre', 'ia', 'identidade']
    LOOP
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO coti_app', s);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO coti_app', s);
        EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA %I TO coti_app', s);
        -- tabelas e sequências criadas pelas próximas migrations (sempre pelo dono, coti)
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coti_app', s);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO coti_app', s);
    END LOOP;
END
$$;

-- Trilha de auditoria: a aplicação só lê e insere (o trigger continua como segunda barreira)
REVOKE UPDATE, DELETE, TRUNCATE ON identidade.auditoria FROM coti_app;
-- O histórico do Flyway não é da conta da aplicação
REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM coti_app;
