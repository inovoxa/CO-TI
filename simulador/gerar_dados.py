#!/usr/bin/env python3
"""
Gerador de dados simulados para o Centro de Operações de TI.

Usa só a biblioteca padrão do Python 3.8+. Dois modos:

1) CARGA (padrão): gera arquivos para popular o sistema e testar as telas.
     python3 gerar_dados.py --escala nacional --saida dados/

   Saída:
     sql/01_estrutura.sql      departamentos (system_dept) + unidades (monitor_unit)
     unidades.csv              unidades com coordenadas
     departamentos.csv         árvore órgão → unidades → departamentos
     usuarios.csv              pessoas (para importar na tela de usuários do sistema)
     equipamentos.csv          inventário no formato do OCS (hostname, SO, hardware, antivírus)
     monitores.csv             links e serviços (para cadastrar no Uptime Kuma/Zabbix)
     chamados.csv              chamados no formato do GLPI
     metricas.csv              latência/estado de cada monitor a cada minuto, últimas 24 h
     logs.jsonl                logs no formato GELF (Graylog), últimas 24 h
     eventos_cameras.jsonl     eventos de câmera (veículos, catracas, recepção), últimas 24 h
     resumo.json               contagens e taxas da escala gerada

2) FLUXO: gera eventos contínuos na taxa pedida, para teste de carga do barramento.
     python3 gerar_dados.py --escala nacional --fluxo --taxa 5000 | kcat -P -b localhost:9092 -t eventos
     python3 gerar_dados.py --escala regional --fluxo --gelf-udp 127.0.0.1:12201   # direto no Graylog

Todos os nomes, placas, IPs e hostnames são fictícios.
"""
import argparse, csv, gzip, json, math, os, random, socket, sys, time, unicodedata, zlib
from datetime import datetime, timedelta, timezone

AQUI = os.path.dirname(os.path.abspath(__file__))
TZ = timezone(timedelta(hours=-3))

ESCALAS = {
    #            unidades  pessoas/depto regional   sede      DC      câmeras/regional  logs 24h
    "piloto":   dict(n=12,  ppd=(2, 5),   sede=(4, 8),    dc=(3, 5),   cams=3, logs=50_000),
    "regional": dict(n=40,  ppd=(14, 28), sede=(16, 30),  dc=(6, 10),  cams=6, logs=300_000),
    "nacional": dict(n=108, ppd=(75, 110), sede=(60, 110), dc=(12, 20), cams=8, logs=1_000_000),
}

DEPT_TPL = {
    "sede": [("Gabinete da Diretoria", ["Diretor de Tecnologia e Inteligência", "Assessor"]),
             ("Segurança da Informação (SOC)", ["Analista de Segurança", "Coordenador do SOC"]),
             ("Infraestrutura e Datacenter", ["Analista de Infraestrutura", "Técnico de Datacenter"]),
             ("Redes e Telecomunicações", ["Analista de Redes", "Técnico de Telecom"]),
             ("Sistemas e Dados", ["Desenvolvedor", "Analista de Dados", "Arquiteto de Software"]),
             ("Inteligência e Análise", ["Analista de Inteligência", "Cientista de Dados"]),
             ("Suporte (Service Desk)", ["Técnico de Suporte", "Coordenador de Atendimento"]),
             ("Administração", ["Assistente Administrativo", "Gestor Administrativo"])],
    "dc": [("Operação do Datacenter", ["Operador de Datacenter", "Analista de Infraestrutura"]),
           ("Backup e Recuperação", ["Analista de Backup"])],
    "reg": [("Atendimento ao Público", ["Atendente", "Supervisor de Atendimento"]),
            ("Administrativo", ["Assistente Administrativo", "Chefe de Seção"]),
            ("Suporte Local de TI", ["Técnico de Suporte"])],
}
NOMES = "Ana Bruno Carla Daniel Eduarda Felipe Gabriela Henrique Isabela João Karina Lucas Mariana Nicolas Olívia Paulo Rafaela Rodrigo Sabrina Thiago Vanessa Vinícius Larissa Marcelo Patrícia Gustavo Juliana André Camila Fernando Letícia Ricardo Beatriz Diego Aline Leonardo Renata Márcio Tatiane Fábio Helena Igor Jéssica Kleber Luana Mateus Natália Otávio Priscila Raul Simone Túlio Valéria Wagner Yasmin".split()
SOBRENOMES = "Silva Santos Oliveira Souza Rodrigues Ferreira Alves Pereira Lima Gomes Costa Ribeiro Martins Carvalho Almeida Lopes Soares Fernandes Vieira Barbosa Rocha Dias Nascimento Andrade Moreira Nunes Marques Machado Mendes Freitas Cardoso Teixeira Araújo Cavalcanti Monteiro Correia Pinto Batista Campos Moura".split()
CPUS = ["Intel Core i5-12500", "Intel Core i7-12700", "Intel Core i5-10400", "AMD Ryzen 5 5600G", "Intel Core i5-1235U", "Intel Core i7-1255U"]
SRVCPU = ["Intel Xeon Silver 4314", "AMD EPYC 7313", "Intel Xeon Gold 6338"]
CATEGORIAS = {
    "Rede": ["Sem acesso à rede", "Wi-Fi instável na sala de reuniões", "Ponto de rede sem sinal"],
    "Hardware": ["Notebook não liga", "Monitor piscando", "Teclado com teclas falhando"],
    "Software": ["Erro ao abrir o SEI", "Atualizar pacote Office", "Instalar leitor de certificado digital"],
    "Acesso e senha": ["Desbloqueio de senha de rede", "Liberar acesso à pasta do setor", "Criar conta para novo servidor"],
    "Impressão": ["Impressora sem toner", "Impressora não aparece na rede"],
    "Segurança": ["E-mail suspeito recebido (phishing)", "Mensagem de antivírus na tela"],
    "Telefonia": ["Ramal mudo", "Transferir ramal para outra mesa"],
}
PRIORIDADES = [("Crítica", 4, .05), ("Alta", 8, .2), ("Média", 24, .45), ("Baixa", 72, .3)]
LETRAS = "ABCDEFGHIJKLMNOPRSTUVWXYZ"


def sem_acento(s):
    return unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()


def iso(ts):
    return datetime.fromtimestamp(ts, TZ).isoformat(timespec="seconds")


def placa(r):
    return f"{r.choice(LETRAS)}{r.choice(LETRAS)}{r.choice(LETRAS)}{r.randint(0,9)}{r.choice(LETRAS)}{r.randint(0,9)}{r.randint(0,9)}"


def ip_externo(r):
    return f"{r.choice([177,186,191,45,103,185,201])}.{r.randint(1,254)}.{r.randint(1,254)}.{r.randint(1,254)}"


def diurno(ts):
    """Fator de carga ao longo do dia: ~0,35 de madrugada, 1,0 no meio do dia."""
    h = datetime.fromtimestamp(ts, TZ)
    h = h.hour + h.minute / 60
    return .35 + .65 * max(0.0, math.sin((h - 6) / 12 * math.pi))


# --------------------------------------------------------------------------------------
# Modelo
# --------------------------------------------------------------------------------------
class Modelo:
    def __init__(self, escala, seed):
        self.cfg = ESCALAS[escala]
        self.escala = escala
        self.r = random.Random(seed)
        cidades = json.load(open(os.path.join(AQUI, "cidades.json"), encoding="utf8"))
        self.unidades, self.deptos, self.pessoas, self.equip, self.monitores, self.chamados = [], [], [], [], [], []
        self._unidades(cidades[: self.cfg["n"]])
        self._pessoas()
        self._equipamentos()
        self._monitores()
        self.cameras = 4 + 6 + (len(self.unidades) - 2) * self.cfg["cams"]

    def _unidades(self, cidades):
        for i, (c, uf, lat, lon) in enumerate(cidades):
            # siglas fixas para as 12 primeiras (estilo aeroporto); as demais levam o índice e nunca colidem
            fixos = ["BSB", "SPO", "OSA", "CPS", "RIO", "BHZ", "CWB", "POA", "SSA", "REC", "BEL", "MAO"]
            code = fixos[i] if i < len(fixos) else sem_acento(c).replace(" ", "")[:3].upper() + f"{i:02d}"
            if i == 0:
                u = dict(id=1, codigo="BSB", nome="Sede · Datacenter principal", tipo="sede")
            elif i == 1:
                u = dict(id=2, codigo="SPO", nome="Datacenter de contingência", tipo="datacenter")
            else:
                u = dict(id=i + 1, codigo=code, nome=f"Regional {c}", tipo="regional")
            u.update(cidade=c, uf=uf, latitude=lat, longitude=lon)
            self.unidades.append(u)

    def _pessoas(self):
        r, logins, did, pid = self.r, set(), 10000, 100000
        self.raiz = dict(id=did, nome="Diretoria de Tecnologia e Inteligência", pai=0, unidade=None)
        self.deptos.append(self.raiz)
        for u in self.unidades:
            did += 1
            u["dept_id"] = did
            self.deptos.append(dict(id=did, nome=u["nome"], pai=self.raiz["id"], unidade=u["id"]))
            tpl = DEPT_TPL["sede" if u["tipo"] == "sede" else "dc" if u["tipo"] == "datacenter" else "reg"]
            faixa = self.cfg["sede" if u["tipo"] == "sede" else "dc" if u["tipo"] == "datacenter" else "ppd"]
            for nome_d, cargos in tpl:
                did += 1
                d = dict(id=did, nome=nome_d, pai=u["dept_id"], unidade=u["id"])
                self.deptos.append(d)
                for i in range(r.randint(*faixa)):
                    f, s = r.choice(NOMES), r.choice(SOBRENOMES)
                    meio = (" " + r.choice(SOBRENOMES)) if r.random() < .4 else ""
                    base = sem_acento(f"{f}.{s}"); login = base; k = 1
                    while login in logins:
                        k += 1; login = f"{base}{k}"
                    logins.add(login)
                    pid += 1
                    cargo = cargos[-1] if i == 0 and len(cargos) > 1 else cargos[i % max(1, len(cargos) - 1)]
                    self.pessoas.append(dict(id=pid, nome=f"{f}{meio} {s}", login=login, email=f"{login}@orgao.gov.br",
                                             cargo=cargo, unidade=u["id"], dept_id=d["id"], departamento=nome_d,
                                             ramal=f"{r.randint(2,9)}{r.randint(100,999)}"))

    def _equipamentos(self):
        r, seq = self.r, 0
        cod = {u["id"]: u["codigo"] for u in self.unidades}
        for p in self.pessoas:
            seq += 1
            nb = r.random() < .35; x = r.random()
            so = "Windows 11 Pro" if x < .55 else "Windows 10 Pro" if x < .8 else "Ubuntu 24.04 LTS" if x < .92 else "Ubuntu 22.04 LTS"
            av = ("Atualizado" if r.random() < .85 else "Ausente") if so.startswith("Ubuntu") else \
                 ("Atualizado" if r.random() < .88 else "Desatualizado" if r.random() < .6 else "Ausente")
            ult = time.time() - (r.randint(1, 240) * 60 if r.random() < .93 else r.randint(8, 40) * 86400)
            self.equip.append(dict(hostname=f"{cod[p['unidade']]}-{'NB' if nb else 'DT'}-{seq:05d}", tipo="Notebook" if nb else "Desktop",
                                   unidade=p["unidade"], usuario=p["login"], so=so, cpu=r.choice(CPUS), ram_gb=r.choice([8, 16, 16, 16, 32]),
                                   disco_gb=r.choice([256, 512, 512, 1024]), disco_livre_pct=r.randint(6, 78), antivirus=av,
                                   ultima_comunicacao=iso(ult), ip=f"10.{p['unidade']}.{r.randint(10,60)}.{r.randint(10,250)}"))
        fator = {"nacional": 6, "regional": 2.5}.get(self.escala, 1)
        servidores = [(1, "Windows Server 2022", 14), (1, "Red Hat Enterprise Linux 9", 10), (1, "Ubuntu Server 24.04", 12),
                      (2, "Windows Server 2022", 8), (2, "Ubuntu Server 24.04", 7), (2, "Debian 12", 5)]
        servidores = [(u, so, round(n * fator)) for u, so, n in servidores]
        servidores += [(u["id"], so, 1) for u in self.unidades if u["tipo"] == "regional" for so in ("Windows Server 2022", "Ubuntu Server 24.04")]
        for un, so, n in servidores:
            for _ in range(n):
                seq += 1
                self.equip.append(dict(hostname=f"{cod[un]}-SRV-{seq:05d}", tipo="Servidor", unidade=un, usuario="", so=so,
                                       cpu=r.choice(SRVCPU), ram_gb=r.choice([64, 128, 256]), disco_gb=r.choice([2048, 4096, 8192]),
                                       disco_livre_pct=r.randint(9, 60), antivirus="Atualizado", ultima_comunicacao=iso(time.time() - r.randint(1, 30) * 60),
                                       ip=f"10.{un}.1.{r.randint(10,250)}"))

    def _monitores(self):
        mid = 0
        for u in self.unidades:
            curto = u["nome"].replace("Regional ", "")
            for papel, oct_ in (("principal", 1), ("redundante", 2)):
                mid += 1
                self.monitores.append(dict(id=mid, nome=f"Link {papel} · {curto}", tipo="ping", alvo=f"10.{u['id']}.0.{oct_}", unidade=u["id"], papel=papel, intervalo_s=30))
        for nome, tipo, alvo in [("Portal da intranet", "http", "https://intranet.orgao.gov.br"), ("SEI · processo eletrônico", "http", "https://sei.orgao.gov.br"),
                                 ("E-mail institucional", "port", "smtp.orgao.gov.br:587"), ("VPN de acesso remoto", "port", "vpn.orgao.gov.br:443"),
                                 ("DNS interno", "dns", "dns1.orgao.local"), ("Active Directory", "port", "dc01.orgao.local:636"),
                                 ("Graylog", "http", "https://logs.orgao.local"), ("Kafka · barramento", "port", "kafka01.orgao.local:9093")]:
            mid += 1
            self.monitores.append(dict(id=mid, nome=nome, tipo=tipo, alvo=alvo, unidade=1, papel="servico", intervalo_s=60))

    # ---------------- taxas (as mesmas usadas na prévia) ----------------
    def taxas(self):
        srv = sum(1 for e in self.equip if e["tipo"] == "Servidor")
        return {
            "logs": dict(eps=len(self.pessoas) * .15 + srv * 2, bytes=650),
            "metricas": dict(eps=len(self.equip) * 20 / 60 + len(self.monitores), bytes=120),
            "cameras": dict(eps=self.cameras * .03, bytes=450),
            "inventario": dict(eps=len(self.equip) / 86400 * 3, bytes=40_000),
            "chamados": dict(eps=len(self.pessoas) / 400 / 3600 * 8, bytes=3000),
        }

    # ---------------- geradores de eventos ----------------
    def log(self, ts):
        r = self.r; p = r.choice(self.pessoas); u = self.unidades[p["unidade"] - 1]; x = r.random()
        if x < .32:   src, lv, msg = "ad-dc01", 6, f"Logon bem-sucedido: {p['login']}"
        elif x < .42: src, lv, msg = "ad-dc01", 4, f"Falha de logon: {p['login']} (senha incorreta)"
        elif x < .62: src, lv, msg = f"fw-{u['codigo'].lower()}", 6, f"Conexão bloqueada de {ip_externo(r)} para 10.{u['id']}.1.{r.randint(10,99)}:{r.choice([22,3389,445,23,1433])}"
        elif x < .72: src, lv, msg = "vpn-gw", 6, f"Usuário {p['login']} conectou à VPN a partir de {ip_externo(r)}"
        elif x < .82: src, lv, msg = f"proxy-{u['codigo'].lower()}", 6, f"Acesso negado para {p['login']}: categoria {r.choice(['jogos','streaming','apostas','sites não classificados'])}"
        elif x < .95: src, lv, msg = f"{u['codigo'].lower()}-srv", 4, r.choice(["Uso de disco acima de 85% em /var", "Serviço reiniciado automaticamente", "Tempo de resposta do banco acima de 2 s"])
        elif x < .999: src, lv, msg = f"{u['codigo'].lower()}-srv", 3, r.choice(["Falha ao gravar no volume de dados", "Timeout na replicação do AD"])
        else:          src, lv, msg = "fw-bsb", 2, f"Varredura de portas detectada a partir de {ip_externo(r)}"
        # formato GELF 1.1 (Graylog). Campos extras começam com "_".
        return {"version": "1.1", "host": src, "short_message": msg, "timestamp": round(ts, 3), "level": lv,
                "_unidade": u["codigo"], "_unidade_id": u["id"], "_usuario": p["login"], "_tipo": "log"}

    def metrica(self, ts, m, estado):
        lat = None if estado == "down" else max(2, int(self.r.gauss(35 if m["papel"] != "servico" else 80, 10)) + (120 if estado == "lento" else 0))
        return {"_tipo": "metrica", "timestamp": round(ts, 3), "monitor": m["nome"], "unidade_id": m["unidade"], "estado": estado, "latencia_ms": lat}

    def evento_camera(self, ts):
        r = self.r; u = self.unidades[0] if r.random() < .3 else r.choice(self.unidades); x = r.random()
        cam = f"{u['codigo']}-CAM-{r.randint(1, 4 if u['id']==1 else 6 if u['id']==2 else self.cfg['cams']):02d}"
        if x < .45:
            tag = r.choices(["servidor", "visitante", "sem_cadastro"], [76, 16, 8])[0]
            ev = {"evento": "veiculo_entrou" if r.random() < .55 else "veiculo_saiu", "placa": placa(r), "classificacao": tag, "confianca": round(r.uniform(.86, .98), 2)}
        elif x < .9:
            carona = r.random() < .01
            ev = {"evento": "pessoa_catraca", "credencial": "cracha" if not carona else "carona_suspeita", "confianca": round(r.uniform(.85, .98), 2)}
        else:
            ev = {"evento": "visitante_registrado", "cracha": f"V-{r.randint(1,999):03d}"}
        ev.update({"_tipo": "camera", "timestamp": round(ts, 3), "camera": cam, "unidade_id": u["id"]})
        return ev

    def chamados_iniciais(self, agora):
        r = self.r; n = max(46, min(5000, round(len(self.pessoas) / 18)))
        suporte = [p for p in self.pessoas if "Suporte" in p["departamento"]]
        pedintes = [p for p in self.pessoas if "Suporte" not in p["departamento"]]
        out = []
        for i in range(n):
            p = r.choice(pedintes); cat = r.choice(list(CATEGORIAS))
            pri, horas, _ = r.choices(PRIORIDADES, [w for *_, w in PRIORIDADES])[0]
            aberto = agora - r.randint(5, 60 * 30) * 60
            st = r.choices(["Novo", "Em atendimento", "Pendente", "Solucionado"], [25, 35, 15, 25])[0]
            sol = min(agora, aberto + r.randint(20, 300) * 60) if st == "Solucionado" else None
            out.append(dict(id=26041 + i, titulo=r.choice(CATEGORIAS[cat]), categoria=cat, prioridade=pri, status=st,
                            unidade=p["unidade"], solicitante=p["login"], tecnico="" if st == "Novo" else r.choice(suporte)["login"],
                            aberto_em=iso(aberto), prazo_sla=iso(aberto + horas * 3600), solucionado_em=iso(sol) if sol else ""))
        return out


# --------------------------------------------------------------------------------------
# Saída: modo carga
# --------------------------------------------------------------------------------------
def escrever_csv(caminho, linhas, campos=None):
    campos = campos or list(linhas[0].keys())
    with open(caminho, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=campos, extrasaction="ignore")
        w.writeheader(); w.writerows(linhas)


def sql_str(s):
    return "NULL" if s is None else "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def modo_carga(m, saida, n_logs, gz):
    os.makedirs(os.path.join(saida, "sql"), exist_ok=True)
    agora = time.time(); inicio = agora - 86400
    abrir = (lambda p: gzip.open(p + ".gz", "wt", encoding="utf8")) if gz else (lambda p: open(p, "w", encoding="utf8"))

    escrever_csv(os.path.join(saida, "unidades.csv"), m.unidades)
    escrever_csv(os.path.join(saida, "departamentos.csv"), m.deptos)
    escrever_csv(os.path.join(saida, "usuarios.csv"), m.pessoas)
    escrever_csv(os.path.join(saida, "equipamentos.csv"), m.equip)
    escrever_csv(os.path.join(saida, "monitores.csv"), m.monitores)
    ch = m.chamados_iniciais(agora)
    escrever_csv(os.path.join(saida, "chamados.csv"), ch)

    # SQL: departamentos (tabela system_dept do RuoYi-Vue-Pro) + unidades (monitor_unit deste pacote)
    with open(os.path.join(saida, "sql", "01_estrutura.sql"), "w", encoding="utf8") as f:
        f.write("-- Gerado por gerar_dados.py. IDs de departamento começam em 10000 para não colidir com os do sistema.\n")
        f.write("-- tenant_id = 1 é a empresa padrão do RuoYi-Vue-Pro; ajuste se usar outra.\n\n")
        for i in range(0, len(m.deptos), 500):
            f.write("INSERT INTO system_dept (id, name, parent_id, sort, status, creator, updater, deleted, tenant_id) VALUES\n")
            f.write(",\n".join(f"({d['id']}, {sql_str(d['nome'])}, {d['pai']}, {k}, 0, 'simulador', 'simulador', b'0', 1)"
                               for k, d in enumerate(m.deptos[i:i + 500], start=i)))
            f.write(";\n\n")
        f.write("INSERT INTO monitor_unit (dept_id, name, city, latitude, longitude, status) VALUES\n")
        f.write(",\n".join(f"({u['dept_id']}, {sql_str(u['nome'])}, {sql_str(u['cidade'] + '/' + u['uf'])}, {u['latitude']}, {u['longitude']}, 0)" for u in m.unidades))
        f.write(";\n")

    # Métricas: cada monitor a cada minuto nas últimas 24 h, com quedas e lentidões
    with abrir(os.path.join(saida, "metricas.csv")) as f:
        f.write("timestamp,monitor_id,monitor,unidade_id,estado,latencia_ms\n")
        estado = {mo["id"]: "up" for mo in m.monitores}
        for minuto in range(1440):
            ts = inicio + minuto * 60
            for mo in m.monitores:
                e = estado[mo["id"]]; x = m.r.random()
                if e == "up":
                    e = "down" if x < .0004 else "lento" if x < .0015 else "up"
                elif x < .2:
                    e = "up"
                estado[mo["id"]] = e
                lat = "" if e == "down" else max(2, int(m.r.gauss(35, 10)) + (120 if e == "lento" else 0))
                f.write(f"{iso(ts)},{mo['id']},\"{mo['nome']}\",{mo['unidade']},{e},{lat}\n")

    # Logs GELF com perfil diurno (mais volume no horário comercial)
    with abrir(os.path.join(saida, "logs.jsonl")) as f:
        pesos = [diurno(inicio + h * 3600 + 1800) for h in range(24)]
        tot = sum(pesos)
        for h, p in enumerate(pesos):
            n = int(n_logs * p / tot)
            for ts in sorted(inicio + h * 3600 + m.r.random() * 3600 for _ in range(n)):
                f.write(json.dumps(m.log(ts), ensure_ascii=False) + "\n")

    # Eventos de câmera
    n_cam = int(m.taxas()["cameras"]["eps"] * 86400 * .6)
    with abrir(os.path.join(saida, "eventos_cameras.jsonl")) as f:
        for ts in sorted(inicio + m.r.random() * 86400 for _ in range(n_cam)):
            f.write(json.dumps(m.evento_camera(ts), ensure_ascii=False) + "\n")

    tx = m.taxas()
    resumo = dict(escala=m.escala, gerado_em=iso(agora), unidades=len(m.unidades), departamentos=len(m.deptos), pessoas=len(m.pessoas),
                  equipamentos=len(m.equip), monitores=len(m.monitores), cameras=m.cameras, chamados=len(ch),
                  linhas_logs=n_logs, linhas_metricas=1440 * len(m.monitores), eventos_camera=n_cam,
                  taxas_em_producao_por_segundo={k: round(v["eps"], 2) for k, v in tx.items()},
                  bytes_por_dia_estimado={k: round(v["eps"] * v["bytes"] * 86400) for k, v in tx.items()})
    json.dump(resumo, open(os.path.join(saida, "resumo.json"), "w", encoding="utf8"), ensure_ascii=False, indent=2)
    return resumo


# --------------------------------------------------------------------------------------
# Saída: modo fluxo (teste de carga)
# --------------------------------------------------------------------------------------
def modo_fluxo(m, taxa, duracao, gelf):
    tx = m.taxas()
    if not taxa:
        taxa = sum(v["eps"] for v in tx.values())
    pesos = {"logs": tx["logs"]["eps"], "metricas": tx["metricas"]["eps"], "cameras": tx["cameras"]["eps"]}
    tipos, ws = list(pesos), list(pesos.values())
    sock, destino = None, None
    if gelf:
        host, porta = gelf.rsplit(":", 1); destino = (host, int(porta))
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        tipos, ws = ["logs"], [1]
    out = sys.stdout
    estado = {mo["id"]: "up" for mo in m.monitores}
    fim = time.time() + duracao if duracao else float("inf")
    enviados, t_rel, lote = 0, time.time(), max(1, int(taxa / 20))
    print(f"[fluxo] escala={m.escala} taxa alvo={taxa:.0f} eventos/s destino={'GELF UDP ' + gelf if gelf else 'stdout'}", file=sys.stderr)
    try:
        while time.time() < fim:
            t0 = time.time()
            for _ in range(lote):
                tipo = m.r.choices(tipos, ws)[0]; ts = time.time()
                if tipo == "logs":
                    ev = m.log(ts)
                elif tipo == "metricas":
                    mo = m.r.choice(m.monitores); e = estado[mo["id"]]; x = m.r.random()
                    e = ("down" if x < .0004 else "lento" if x < .0015 else "up") if e == "up" else ("up" if x < .2 else e)
                    estado[mo["id"]] = e; ev = m.metrica(ts, mo, e)
                else:
                    ev = m.evento_camera(ts)
                if sock:
                    sock.sendto(zlib.compress(json.dumps(ev, ensure_ascii=False).encode()), destino)
                else:
                    out.write(json.dumps(ev, ensure_ascii=False) + "\n")
            enviados += lote
            espera = lote / taxa - (time.time() - t0)
            if espera > 0:
                time.sleep(espera)
            if time.time() - t_rel >= 5:
                print(f"[fluxo] {enviados:,} eventos enviados".replace(",", "."), file=sys.stderr); t_rel = time.time()
    except (KeyboardInterrupt, BrokenPipeError):
        pass
    print(f"[fluxo] fim: {enviados:,} eventos".replace(",", "."), file=sys.stderr)


def main():
    ap = argparse.ArgumentParser(description="Gera dados simulados para o Centro de Operações de TI.")
    ap.add_argument("--escala", choices=ESCALAS, default="piloto", help="piloto (12 unidades), regional (40) ou nacional (108)")
    ap.add_argument("--saida", default="dados", help="pasta de saída (modo carga)")
    ap.add_argument("--seed", type=int, default=42, help="semente: mesma semente gera os mesmos dados")
    ap.add_argument("--logs", type=int, help="quantidade de linhas de log nas 24 h (padrão depende da escala)")
    ap.add_argument("--gz", action="store_true", help="compactar os arquivos grandes (.gz)")
    ap.add_argument("--fluxo", action="store_true", help="em vez de arquivos, gerar eventos contínuos")
    ap.add_argument("--taxa", type=float, help="eventos por segundo no modo fluxo (padrão: taxa real da escala)")
    ap.add_argument("--duracao", type=float, default=0, help="segundos de fluxo (0 = até Ctrl+C)")
    ap.add_argument("--gelf-udp", help="enviar logs direto a uma entrada GELF UDP do Graylog, ex.: 127.0.0.1:12201")
    a = ap.parse_args()

    m = Modelo(a.escala, a.seed)
    if a.fluxo:
        modo_fluxo(m, a.taxa, a.duracao, a.gelf_udp)
        return
    t = time.time()
    res = modo_carga(m, a.saida, a.logs or ESCALAS[a.escala]["logs"], a.gz)
    print(json.dumps(res, ensure_ascii=False, indent=2))
    print(f"\nPronto em {time.time() - t:.1f} s. Arquivos em: {os.path.abspath(a.saida)}", file=sys.stderr)


if __name__ == "__main__":
    main()
