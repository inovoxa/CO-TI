"""Gera um kuma.db FICTÍCIO (banco do Uptime Kuma 2.x, SQLite) para demonstrar a importação do CO-TI.

Prefeitura de exemplo com links das unidades, sistemas, um grupo, canais de aviso, janelas de manutenção e 30 dias
de batidas com quedas realistas (link que oscila, sistema que caiu numa tarde, manutenção de domingo). Nomes, IPs,
tokens e endereços são inventados; os quatro monitores "CO-TI ..." apontam para os serviços do próprio docker-compose
e continuam verificando de verdade depois de importados.

Uso:
    python exemplos/kuma/gerar_kuma_ficticio.py kuma-ficticio.db [--pausar-ficticios]
Depois: Monitoramento → Monitores → "Importar do Uptime Kuma" e envie o arquivo.

--pausar-ficticios grava os monitores de alvo inventado como pausados (com o histórico), para a demonstração não
encher de quedas assim que forem importados; os "CO-TI ..." continuam ativos.
"""
import datetime
import json
import math
import random
import sqlite3
import sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
saida = args[0] if args else "kuma-ficticio.db"
PAUSAR = "--pausar-ficticios" in sys.argv
DIAS = 30
rnd = random.Random(20261025)
agora = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None, microsecond=0)
inicio = agora - datetime.timedelta(days=DIAS)

c = sqlite3.connect(saida)
c.executescript("""
DROP TABLE IF EXISTS monitor; DROP TABLE IF EXISTS heartbeat; DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS monitor_notification; DROP TABLE IF EXISTS maintenance; DROP TABLE IF EXISTS monitor_maintenance;
DROP TABLE IF EXISTS tag; DROP TABLE IF EXISTS monitor_tag; DROP TABLE IF EXISTS setting;
CREATE TABLE monitor (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(150), active BOOLEAN DEFAULT 1, user_id INTEGER,
  interval INTEGER DEFAULT 60, url TEXT, type VARCHAR(20), weight INTEGER DEFAULT 2000, hostname VARCHAR(255), port INTEGER,
  created_date DATETIME DEFAULT (DATETIME('now')), keyword VARCHAR(255), maxretries INTEGER DEFAULT 0, ignore_tls BOOLEAN DEFAULT 0,
  upside_down BOOLEAN DEFAULT 0, maxredirects INTEGER DEFAULT 10, accepted_statuscodes_json TEXT DEFAULT '["200-299"]',
  dns_resolve_type VARCHAR(5), dns_resolve_server VARCHAR(255), dns_last_result VARCHAR(255), retry_interval INTEGER DEFAULT 0,
  push_token VARCHAR(20), method TEXT DEFAULT 'GET', body TEXT, headers TEXT, basic_auth_user TEXT, basic_auth_pass TEXT,
  auth_method VARCHAR(250), resend_interval INTEGER DEFAULT 0, timeout DOUBLE DEFAULT 0, invert_keyword BOOLEAN DEFAULT 0,
  json_path TEXT, expected_value VARCHAR(255), json_path_operator VARCHAR(255), description TEXT, parent INTEGER,
  expiry_notification BOOLEAN DEFAULT 1, packet_size INTEGER DEFAULT 56);
CREATE TABLE heartbeat (id INTEGER PRIMARY KEY AUTOINCREMENT, important BOOLEAN DEFAULT 0, monitor_id INTEGER, status SMALLINT,
  msg TEXT, time DATETIME, ping INTEGER, duration INTEGER DEFAULT 0, down_count INTEGER DEFAULT 0, retries INTEGER DEFAULT 0);
CREATE INDEX monitor_time_index ON heartbeat (monitor_id, time);
CREATE TABLE notification (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255), config TEXT, active BOOLEAN DEFAULT 1,
  user_id INTEGER, is_default BOOLEAN DEFAULT 0);
CREATE TABLE monitor_notification (id INTEGER PRIMARY KEY AUTOINCREMENT, monitor_id INTEGER, notification_id INTEGER);
CREATE TABLE maintenance (id INTEGER PRIMARY KEY AUTOINCREMENT, title VARCHAR(150), description TEXT, user_id INTEGER,
  active BOOLEAN DEFAULT 1, strategy VARCHAR(50), start_date DATETIME, end_date DATETIME, start_time TIME, end_time TIME,
  weekdays VARCHAR(250) DEFAULT '[]', days_of_month TEXT DEFAULT '[]', interval_day INTEGER, cron TEXT, timezone VARCHAR(255),
  duration INTEGER);
CREATE TABLE monitor_maintenance (id INTEGER PRIMARY KEY AUTOINCREMENT, monitor_id INTEGER, maintenance_id INTEGER);
CREATE TABLE tag (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255), color VARCHAR(255));
CREATE TABLE monitor_tag (id INTEGER PRIMARY KEY AUTOINCREMENT, monitor_id INTEGER, tag_id INTEGER, value TEXT);
CREATE TABLE setting (id INTEGER PRIMARY KEY AUTOINCREMENT, key VARCHAR(200), value TEXT, type VARCHAR(20));
INSERT INTO setting (key, value) VALUES ('database_version', '"2.5.7"');
""")
iso = lambda d: d.strftime("%Y-%m-%d %H:%M:%S")


def monitor(**k):
    if PAUSAR and not k["name"].startswith("CO-TI"):
        k["active"] = 0
    k.setdefault("user_id", 1)
    k.setdefault("created_date", iso(inicio - datetime.timedelta(days=200)))
    c.execute(f"INSERT INTO monitor ({','.join(k)}) VALUES ({','.join('?' * len(k))})", list(k.values()))
    return c.execute("SELECT last_insert_rowid()").fetchone()[0]


# ---------------------------------------------------------------- monitores
grupo = monitor(name="Datacenter da Prefeitura", type="group", description="Servidores da sala-cofre do Paço")
UNIDADES = ["Paço Municipal", "UBS Centro", "UBS Jardim América", "UBS Vila Xavier", "EMEF Parque São Paulo",
            "Hospital Municipal", "Almoxarifado Central", "Guarda Civil Municipal"]
links = {}
for i, u in enumerate(UNIDADES):
    links[u] = monitor(name=f"Link principal · {u}", type="ping", hostname=f"10.{20 + i}.0.1", interval=120, maxretries=2,
                       retry_interval=60, resend_interval=30, description=f"Roteador da operadora na {u}")
link_redundante = monitor(name="Link redundante · Paço Municipal", type="ping", hostname="10.20.0.254", interval=120, maxretries=2,
                          retry_interval=60)
sistemas = [
    monitor(name="Portal da Prefeitura", type="http", url="https://www.prefeitura-exemplo.sp.gov.br", interval=300, maxretries=1,
            retry_interval=60, timeout=24, parent=grupo),
    monitor(name="Prontuário Eletrônico (e-SUS)", type="keyword", url="https://esus.saude-exemplo.sp.gov.br/login", keyword="Entrar",
            interval=300, maxretries=2, retry_interval=60, timeout=24, parent=grupo),
    monitor(name="Sistema de Protocolo (SEI)", type="http", url="https://sei.prefeitura-exemplo.sp.gov.br/sei/", interval=300, maxretries=1,
            retry_interval=60, timeout=24, parent=grupo, accepted_statuscodes_json='["200-299","302"]'),
    monitor(name="API da Nota Fiscal", type="json-query", url="https://nfse.prefeitura-exemplo.sp.gov.br/api/status", json_path="situacao",
            json_path_operator="==", expected_value="operacional", interval=300, timeout=24, parent=grupo),
    monitor(name="E-mail institucional (webmail)", type="http", url="https://webmail.prefeitura-exemplo.sp.gov.br", interval=300, timeout=24),
    monitor(name="Servidor de arquivos (SMB)", type="port", hostname="10.20.1.15", port=445, interval=300, parent=grupo),
    monitor(name="Active Directory (LDAPS)", type="port", hostname="10.20.1.10", port=636, interval=300, parent=grupo),
    monitor(name="DNS interno", type="dns", hostname="sei.prefeitura-exemplo.sp.gov.br", dns_resolve_type="A",
            dns_resolve_server="10.20.1.10", port=53, interval=300),
    monitor(name="Backup noturno (Veeam)", type="push", push_token="FicBackupVeeam2026xy", interval=86400,
            description="O job de backup chama a URL de push ao terminar"),
    monitor(name="Câmeras do Paço (NVR)", type="http", url="http://10.20.5.20", interval=300, active=0, description="Desativado na troca do NVR"),
]
# reais: serviços do próprio docker-compose (continuam verificando depois de importados)
reais = [
    monitor(name="CO-TI · Portal", type="keyword", url="http://frontend/", keyword="<div id=\"app\"", interval=60, maxretries=1, retry_interval=30),
    monitor(name="CO-TI · Banco de dados", type="port", hostname="postgres", port=5432, interval=60),
    monitor(name="CO-TI · Cache (Redis)", type="port", hostname="redis", port=6379, interval=60),
    monitor(name="CO-TI · DNS do Docker", type="dns", hostname="postgres", dns_resolve_type="A", dns_resolve_server="127.0.0.11", port=53, interval=120),
]
monitor(name="Painel do almoxarifado (MQTT)", type="mqtt", hostname="10.26.0.30", port=1883, interval=300)  # tipo que não entra

# ---------------------------------------------------------------- batidas (30 dias)
lote = []


def batidas(mid, intervalo, base_ms, quedas, manut=(), ruido=0.25, ok_msg="", latencia=True):
    """quedas: [(início, minutos, msg)]; manut: [(início, minutos)]."""
    t = inicio
    anterior = None
    while t <= agora:
        status, msg = 1, ok_msg
        for q0, mins, qmsg in quedas:
            if q0 <= t < q0 + datetime.timedelta(minutes=mins):
                status, msg = 0, qmsg
        for m0, mins in manut:
            if m0 <= t < m0 + datetime.timedelta(minutes=mins):
                status, msg = 3, "Em manutenção"
        # horário comercial é mais lento
        carga = 1.6 if 8 <= (t.hour - 3) % 24 <= 18 and t.weekday() < 5 else 1.0
        ping = round(base_ms * carga * (1 + rnd.uniform(-ruido, ruido))) if status == 1 and latencia else None
        lote.append((1 if status != anterior else 0, mid, status, msg, iso(t), ping, intervalo))
        anterior = status
        t += datetime.timedelta(seconds=intervalo)


def dia(d, h, m=0):
    """d dias atrás, às h:m no horário de Brasília (UTC-3)."""
    x = (agora - datetime.timedelta(days=d)).replace(hour=0, minute=0, second=0) + datetime.timedelta(hours=h + 3, minutes=m)
    return x


domingos = [dia(d, 2) for d in range(DIAS) if (agora - datetime.timedelta(days=d)).weekday() == 6]
for i, (u, mid) in enumerate(links.items()):
    quedas = []
    if u == "UBS Vila Xavier":  # link que oscila
        quedas = [(dia(d, rnd.randint(8, 17), rnd.randint(0, 59)), rnd.randint(4, 25), "Tempo esgotado (sem resposta ao ping)")
                  for d in rnd.sample(range(1, DIAS), 9)]
    elif u == "EMEF Parque São Paulo":  # rompimento de fibra
        quedas = [(dia(12, 9, 40), 6 * 60 + 15, "Host inacessível")]
    elif u == "Hospital Municipal":
        quedas = [(dia(3, 22, 10), 18, "Tempo esgotado (sem resposta ao ping)")]
    else:
        quedas = [(dia(rnd.randint(1, DIAS - 1), rnd.randint(7, 20)), rnd.randint(3, 12), "Tempo esgotado (sem resposta ao ping)")] if rnd.random() < 0.6 else []
    batidas(mid, 120, 18 + 6 * i, quedas)
batidas(link_redundante, 120, 41, [(dia(12, 9, 40), 2, "Tempo esgotado")])
avisos = {
    0: [(dia(8, 14, 5), 35, "Código 502 (esperado 200-299)")],
    1: [(dia(5, 15, 20), 3 * 60 + 40, "A palavra 'Entrar' não foi encontrada"), (dia(19, 10, 0), 12, "Tempo esgotado (24 s)")],
    2: [(dia(15, 7, 55), 50, "Código 503 (esperado 200-299, 302)")],
    3: [(dia(2, 11, 30), 25, "consulta JSON não confere (instavel == operacional)")],
    4: [],
    5: [(dia(21, 23, 0), 90, "Conexão recusada")],
    6: [],
    7: [(dia(10, 4, 10), 6, "servidor DNS não respondeu")],
}
for i, mid in enumerate(sistemas[:8]):
    manut = [(d, 120) for d in domingos] if i in (0, 2, 5) else []
    batidas(mid, 300, [180, 420, 260, 140, 210, 3, 2, 4][i], avisos[i], manut, ok_msg="200 - OK" if i in (0, 2, 4) else "")
# backup: um sinal por noite; falhou há 6 dias
for d in range(DIAS, 0, -1):
    t = dia(d, 3, rnd.randint(0, 40))
    falhou = d == 6
    lote.append((1 if falhou or d == 5 else 0, sistemas[8], 0 if falhou else 1,
                 "Sem sinal (push) há 24 h" if falhou else "Backup concluído: 412 GB", iso(t), None, 86400))
batidas(sistemas[9], 300, 30, [], ok_msg="200 - OK")
for j, mid in enumerate(reais):
    batidas(mid, 300, [12, 1, 1, 2][j], [], ok_msg="")
c.executemany("INSERT INTO heartbeat (important, monitor_id, status, msg, time, ping, duration) VALUES (?,?,?,?,?,?,?)", lote)
# câmeras: desativado há 10 dias (sem batidas depois disso)
c.execute("DELETE FROM heartbeat WHERE monitor_id = ? AND time > ?", (sistemas[9], iso(agora - datetime.timedelta(days=10))))


# ---------------------------------------------------------------- notificações
def notif(nome, cfg, padrao=0):
    c.execute("INSERT INTO notification (name, config, active, user_id, is_default) VALUES (?,?,1,1,?)", (nome, json.dumps(cfg), padrao))
    return c.execute("SELECT last_insert_rowid()").fetchone()[0]


tg = notif("Telegram · NOC", {"name": "Telegram · NOC", "type": "telegram", "telegramBotToken": "000000000:FICTICIO-token-de-exemplo",
                              "telegramChatID": "-1000000000000", "isDefault": True}, 1)
mail = notif("E-mail · equipe de TI", {"name": "E-mail · equipe de TI", "type": "smtp", "smtpHost": "smtp.prefeitura-exemplo.sp.gov.br",
                                       "smtpPort": 587, "smtpTo": "noc@prefeitura-exemplo.sp.gov.br, suporte@prefeitura-exemplo.sp.gov.br"})
wh = notif("Webhook · painel da Saúde", {"name": "Webhook · painel da Saúde", "type": "webhook",
                                         "webhookURL": "https://painel.saude-exemplo.sp.gov.br/hooks/kuma", "webhookContentType": "json"})
notif("Discord · plantão", {"name": "Discord · plantão", "type": "discord", "discordWebhookUrl": "https://discord.com/api/webhooks/0/ficticio"})
for mid in list(links.values()) + [link_redundante]:
    c.execute("INSERT INTO monitor_notification (monitor_id, notification_id) VALUES (?, ?)", (mid, tg))
for mid in sistemas[:8]:
    c.execute("INSERT INTO monitor_notification (monitor_id, notification_id) VALUES (?, ?)", (mid, mail))
for mid in (links["UBS Centro"], links["UBS Jardim América"], links["UBS Vila Xavier"], links["Hospital Municipal"], sistemas[1]):
    c.execute("INSERT INTO monitor_notification (monitor_id, notification_id) VALUES (?, ?)", (mid, wh))

# ---------------------------------------------------------------- manutenções
c.execute("INSERT INTO maintenance (title, description, active, strategy, start_time, end_time, weekdays, timezone) VALUES"
          " ('Backup e atualizações de domingo', 'Reinício dos servidores após o backup', 1, 'recurring-weekday', '02:00', '04:00', '[0]', 'SAME_AS_SERVER')")
m1 = c.execute("SELECT last_insert_rowid()").fetchone()[0]
for mid in (sistemas[0], sistemas[2], sistemas[5]):
    c.execute("INSERT INTO monitor_maintenance (monitor_id, maintenance_id) VALUES (?, ?)", (mid, m1))
proxima = (agora + datetime.timedelta(days=9)).replace(hour=0, minute=0, second=0)
c.execute("INSERT INTO maintenance (title, description, active, strategy, start_date, end_date) VALUES (?, ?, 1, 'single', ?, ?)",
          ("Troca do link da EMEF Parque São Paulo", "Migração para fibra de 500 Mb", iso(proxima + datetime.timedelta(hours=22)),
           iso(proxima + datetime.timedelta(hours=26))))
m2 = c.execute("SELECT last_insert_rowid()").fetchone()[0]
c.execute("INSERT INTO monitor_maintenance (monitor_id, maintenance_id) VALUES (?, ?)", (links["EMEF Parque São Paulo"], m2))
c.execute("INSERT INTO maintenance (title, active, strategy, cron, duration) VALUES ('Limpeza de logs (cron)', 1, 'cron', '0 3 1 * *', 30)")

# ---------------------------------------------------------------- etiquetas
c.execute("INSERT INTO tag (name, color) VALUES ('saude', '#16a34a'), ('educacao', '#2563eb'), ('critico', '#dc2626')")
c.commit()
n = c.execute("SELECT count(*) FROM heartbeat").fetchone()[0]
c.close()
print(f"{saida}: {len(UNIDADES) + 1 + len(sistemas) + len(reais) + 2} monitores, 4 notificações, 3 manutenções, {n} batidas")
