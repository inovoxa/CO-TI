-- Modelos que "pensam" antes de responder (Qwen3.5, DeepSeek-R1...) gastam tempo e tokens no raciocínio: num servidor sem
-- placa de vídeo isso passa do limite de espera ou esgota os tokens da resposta. Com sem_raciocinio, o pedido leva
-- reasoning_effort = "none" e o modelo responde direto.
ALTER TABLE atendimento.ia_provedor ADD COLUMN sem_raciocinio bool NOT NULL DEFAULT false;
