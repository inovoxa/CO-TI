-- Logs: consumo de banda no painel Firewall (soma de sentbyte + rcvdbyte dos logs de sessão do FortiGate).
-- Só acrescenta os quadros se o painel ainda não os tiver (quem já mexeu no painel não perde nada).
UPDATE logs.painel
SET widgets = widgets || '[
  {"id":"f11","tipo":"contador","titulo":"Banda de internet","fluxo":"firewall","consulta":"type:traffic AND dstintfrole:wan","soma":"bytes","periodo":"1d","largura":1},
  {"id":"f12","tipo":"ranking","titulo":"Quem mais consumiu banda","fluxo":"firewall","consulta":"type:traffic AND dstintfrole:wan","campo":"srcip","soma":"bytes","limite":10,"periodo":"1d","largura":2},
  {"id":"f13","tipo":"ranking","titulo":"Sites que mais consomem banda","fluxo":"firewall","consulta":"type:traffic AND dstintfrole:wan","campo":"hostname","soma":"bytes","limite":10,"periodo":"1d","largura":1},
  {"id":"f14","tipo":"histograma","titulo":"Banda de internet ao longo do dia","fluxo":"firewall","consulta":"type:traffic AND dstintfrole:wan","soma":"bytes","periodo":"1d","largura":4}
]'::jsonb
WHERE nome = 'Firewall' AND NOT widgets @> '[{"id":"f11"}]'::jsonb;
