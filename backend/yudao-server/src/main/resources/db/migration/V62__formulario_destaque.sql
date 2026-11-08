-- Formulário em destaque no catálogo. Os que já existem continuam como estão.
ALTER TABLE itsm.formulario ADD COLUMN destaque bool NOT NULL DEFAULT false;
