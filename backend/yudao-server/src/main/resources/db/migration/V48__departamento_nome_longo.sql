-- Unidades reais têm nomes longos ("SEC GESTAO Assistência Social Conselho Tutelar II"); cada unidade tem o seu departamento
ALTER TABLE system_dept ALTER COLUMN name TYPE varchar(100);
