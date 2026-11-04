-- Manutenção predial por último no catálogo, depois de Sistemas administrativos (ordem 1000). A condição na ordem antiga
-- evita mexer em quem já reordenou os formulários pela tela.
UPDATE itsm.formulario
SET ordem = ordem + 500, update_time = CURRENT_TIMESTAMP
WHERE deleted = 0 AND codigo IN ('6.1', '6.2', '6.3', '6.4') AND ordem BETWEEN 600 AND 603;
