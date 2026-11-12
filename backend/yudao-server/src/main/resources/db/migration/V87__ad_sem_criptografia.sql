-- AD sem criptografia (ldap:// na 389 sem StartTLS), para DC sem certificado na rede interna, como o GLPI sem SSL.
ALTER TABLE identidade.ad_config DROP CONSTRAINT ck_identidade_ad_config_seguranca;
ALTER TABLE identidade.ad_config ADD CONSTRAINT ck_identidade_ad_config_seguranca
    CHECK (seguranca IN ('ldaps', 'starttls', 'nenhuma'));
