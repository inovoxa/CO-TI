-- O formulário 1.6 buscava o gestor no AD: sem vínculo com o AD, o pedido nem abria. Agora o solicitante indica o
-- superior direto, como nos demais pedidos de usuários e acessos. O comparativo pelo texto evita repetir a pergunta
-- se ela já tiver sido incluída pela tela.
UPDATE itsm.formulario
SET aprovacao = 'campo',
    aprovador_campo = 'email_superior',
    definicao = CASE
        WHEN definicao LIKE '%"email_superior"%' THEN definicao
        ELSE jsonb_set(definicao::jsonb, '{secoes,0,perguntas}',
                (definicao::jsonb #> '{secoes,0,perguntas}')
                || '[{"name":"email_superior","label":"E-mail do superior direto (quem vai aprovar)","type":"email","required":true,"values":null,"regex":null}]'::jsonb)::text
    END
WHERE codigo = '1.6' AND acao_ad = 'CriarEmail' AND aprovacao = 'gestor';
