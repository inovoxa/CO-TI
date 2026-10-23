#!/usr/bin/env python3
"""
Sincroniza a estrutura do órgão com um servidor Matrix Synapse, em forma de árvore:

    Space  "Diretoria de Tecnologia e Inteligência"        (#orgao)
      ├─ Sala  "Avisos gerais"                             (#avisos)        todos; só o gabinete publica
      ├─ Space "Centro de Operações"                       (#noc)
      │    ├─ Sala "NOC · alertas"                         (#noc-alertas)   bot do portal posta as ocorrências
      │    ├─ Sala "SOC · segurança"                       (#soc)
      │    ├─ Sala "Plantão de TI"                         (#plantao-ti)
      │    └─ Sala "Janela de mudanças"                    (#mudancas)
      └─ Space "<Unidade>"                                 (#<cod>)         um por unidade
           ├─ Sala "Geral · <Unidade>"                     (#<cod>-geral)   todos da unidade
           └─ Sala "<Departamento>"                        (#<cod>-<depto>) um por departamento

Cada pessoa entra no space do órgão, no space da sua unidade, na sala geral da unidade e na sala do seu
departamento. As salas de departamento têm criptografia de ponta a ponta e entrada "restrita" a quem
é membro do space da unidade (recurso de room version 8+).

Entrada: os CSVs do gerador (simulador/gerar_dados.py) ou uma exportação do banco do portal com as
mesmas colunas: unidades.csv, departamentos.csv, usuarios.csv.

Segurança: por padrão roda em modo SIMULAÇÃO (só mostra o plano). Para aplicar, use --executar.
O token deve ser de um usuário administrador do Synapse. Só a biblioteca padrão do Python é usada.

Exemplos:
    python3 sincronizar_synapse.py --dados ../../simulador/dados --dominio orgao.gov.br
    python3 sincronizar_synapse.py --dados dados --dominio orgao.gov.br \
        --servidor https://matrix.orgao.gov.br --token "$SYNAPSE_ADMIN_TOKEN" --executar
"""
import argparse, csv, json, os, re, sys, time, unicodedata, urllib.error, urllib.parse, urllib.request

ROOM_VERSION = "10"


def slug(s):
    s = unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def ler_csv(pasta, nome):
    with open(os.path.join(pasta, nome), encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


# --------------------------------------------------------------------------------------
# Cliente HTTP mínimo para o Synapse (com tratamento de limite de taxa)
# --------------------------------------------------------------------------------------
class Synapse:
    def __init__(self, servidor, token, executar):
        self.base = servidor.rstrip("/") if servidor else ""
        self.token = token
        self.executar = executar
        self.chamadas = 0

    def req(self, metodo, caminho, corpo=None, ok404=False):
        self.chamadas += 1
        if not self.executar:
            return {}
        url = self.base + caminho
        dados = json.dumps(corpo).encode() if corpo is not None else None
        for tentativa in range(8):
            r = urllib.request.Request(url, data=dados, method=metodo,
                                       headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(r, timeout=30) as resp:
                    corpo_resp = resp.read()
                    return json.loads(corpo_resp) if corpo_resp else {}
            except urllib.error.HTTPError as e:
                txt = e.read().decode(errors="replace")
                if e.code == 404 and ok404:
                    return None
                if e.code == 429:  # limite de taxa: espera o tempo que o servidor pedir
                    try:
                        espera = json.loads(txt).get("retry_after_ms", 1000) / 1000
                    except ValueError:
                        espera = 1
                    time.sleep(espera)
                    continue
                if e.code >= 500 and tentativa < 3:
                    time.sleep(2 ** tentativa)
                    continue
                raise RuntimeError(f"{metodo} {caminho} → HTTP {e.code}: {txt[:300]}")
        raise RuntimeError(f"{metodo} {caminho}: limite de taxa persistente")

    # ---- usuários (Admin API) ----
    def garantir_usuario(self, mxid, nome, senha=None, admin=False):
        corpo = {"displayname": nome, "admin": admin}
        if senha:
            corpo["password"] = senha
        return self.req("PUT", f"/_synapse/admin/v2/users/{urllib.parse.quote(mxid)}", corpo)

    def sem_limite_de_taxa(self, mxid):
        return self.req("POST", f"/_synapse/admin/v1/users/{urllib.parse.quote(mxid)}/override_ratelimit",
                        {"messages_per_second": 0, "burst_count": 0})

    # ---- salas e spaces (Client-Server API) ----
    def sala_por_alias(self, alias):
        r = self.req("GET", f"/_matrix/client/v3/directory/room/{urllib.parse.quote(alias)}", ok404=True)
        return r.get("room_id") if r else None

    def criar_sala(self, alias_local, nome, topico, space=False, pai=None, dominio="", e2ee=False, restrita=False, poder=None):
        estado = []
        if pai:
            estado.append({"type": "m.space.parent", "state_key": pai, "content": {"via": [dominio], "canonical": True}})
        if restrita and pai:
            estado.append({"type": "m.room.join_rules", "state_key": "",
                           "content": {"join_rule": "restricted", "allow": [{"type": "m.room_membership", "room_id": pai}]}})
        if e2ee:
            estado.append({"type": "m.room.encryption", "state_key": "", "content": {"algorithm": "m.megolm.v1.aes-sha2"}})
        corpo = {"room_alias_name": alias_local, "name": nome, "topic": topico, "preset": "private_chat",
                 "visibility": "private", "room_version": ROOM_VERSION, "initial_state": estado,
                 "creation_content": {"m.federate": False}}
        if space:
            corpo["creation_content"]["type"] = "m.space"
        if poder:
            corpo["power_level_content_override"] = poder
        r = self.req("POST", "/_matrix/client/v3/createRoom", corpo)
        return r.get("room_id", f"!simulado-{alias_local}")

    def ligar_filho(self, pai, filho, dominio):
        return self.req("PUT", f"/_matrix/client/v3/rooms/{urllib.parse.quote(pai)}/state/m.space.child/{urllib.parse.quote(filho)}",
                        {"via": [dominio], "suggested": True})

    def colocar_na_sala(self, sala, mxid):
        return self.req("POST", f"/_synapse/admin/v1/join/{urllib.parse.quote(sala)}", {"user_id": mxid})

    def membros(self, sala):
        r = self.req("GET", f"/_matrix/client/v3/rooms/{urllib.parse.quote(sala)}/joined_members")
        return set((r or {}).get("joined", {}).keys())

    def remover(self, sala, mxid, motivo):
        return self.req("POST", f"/_matrix/client/v3/rooms/{urllib.parse.quote(sala)}/kick", {"user_id": mxid, "reason": motivo})


# --------------------------------------------------------------------------------------
# Plano: árvore de spaces/salas e quem entra onde
# --------------------------------------------------------------------------------------
def montar_plano(unidades, deptos, pessoas, dominio, limite):
    if limite:
        pessoas = pessoas[:limite]
    mx = lambda login: f"@{login}:{dominio}"
    un_por_id = {u["id"]: u for u in unidades}
    dep_por_id = {d["id"]: d for d in deptos}
    raiz = next(d for d in deptos if d["pai"] == "0")

    nos = []  # cada nó: chave, alias, nome, tópico, space?, pai(chave), e2ee, restrita, poder
    nos.append(dict(chave="orgao", alias="orgao", nome=raiz["nome"], topico="Space do órgão: todas as unidades e salas", space=True, pai=None))
    gabinete = {p["login"] for p in pessoas if p["departamento"].startswith("Gabinete")}
    nos.append(dict(chave="avisos", alias="avisos", nome="Avisos gerais", topico="Comunicados da diretoria. Só o gabinete publica.",
                    pai="orgao", poder={"events_default": 50, "users": {mx(l): 50 for l in gabinete}}))
    nos.append(dict(chave="noc", alias="noc", nome="Centro de Operações", topico="Operação de TI, segurança e plantão", space=True, pai="orgao"))
    for chave, alias, nome, top, e2ee in [("noc-alertas", "noc-alertas", "NOC · alertas", "Ocorrências postadas pelo bot do portal", False),
                                          ("soc", "soc", "SOC · segurança", "Incidentes de segurança e resposta", True),
                                          ("plantao", "plantao-ti", "Plantão de TI", "Escala de plantão e passagem de turno", True),
                                          ("mudancas", "mudancas", "Janela de mudanças", "Mudanças programadas", True)]:
        nos.append(dict(chave=chave, alias=alias, nome=nome, topico=top, pai="noc", e2ee=e2ee))
    for u in unidades:
        cod = slug(u.get("codigo") or u["nome"])
        nos.append(dict(chave=f"u{u['id']}", alias=cod, nome=u["nome"], topico=f"{u['nome']} · {u.get('cidade','')}/{u.get('uf','')}", space=True, pai="orgao"))
        nos.append(dict(chave=f"u{u['id']}-geral", alias=f"{cod}-geral", nome=f"Geral · {u['nome'].replace('Regional ', '')}",
                        topico=f"Todos de {u['nome']}", pai=f"u{u['id']}", e2ee=True, restrita=True))
    for d in deptos:
        if d["pai"] == "0" or not d.get("unidade") or d["id"] == str(un_por_id.get(d["unidade"], {}).get("dept_id")):
            continue
        u = un_por_id[d["unidade"]]
        nos.append(dict(chave=f"d{d['id']}", alias=f"{slug(u.get('codigo') or u['nome'])}-{slug(d['nome'])}", nome=d["nome"],
                        topico=f"{d['nome']} · {u['nome']}", pai=f"u{u['id']}", e2ee=True, restrita=True))

    # quem entra onde
    noc_deptos = re.compile(r"SOC|Infraestrutura|Redes|Suporte|Sistemas|Datacenter|Backup")
    entradas = {}
    for p in pessoas:
        salas = ["orgao", "avisos", f"u{p['unidade']}", f"u{p['unidade']}-geral", f"d{p['dept_id']}"]
        if p["unidade"] in ("1", "2") and noc_deptos.search(p["departamento"]):
            salas += ["noc", "noc-alertas", "plantao", "mudancas"]
        if "SOC" in p["departamento"]:
            salas.append("soc")
        entradas[mx(p["login"])] = (p["nome"], salas)
    return nos, entradas


def main():
    ap = argparse.ArgumentParser(description="Cria no Synapse a árvore órgão → unidades → departamentos e coloca cada pessoa nas suas salas.")
    ap.add_argument("--dados", required=True, help="pasta com unidades.csv, departamentos.csv e usuarios.csv")
    ap.add_argument("--dominio", required=True, help="server_name do Synapse, ex.: orgao.gov.br")
    ap.add_argument("--servidor", help="URL do Synapse, ex.: https://matrix.orgao.gov.br")
    ap.add_argument("--token", default=os.environ.get("SYNAPSE_ADMIN_TOKEN"), help="token de um administrador (ou variável SYNAPSE_ADMIN_TOKEN)")
    ap.add_argument("--executar", action="store_true", help="aplica de verdade; sem isso, só mostra o plano")
    ap.add_argument("--senha-teste", help="define esta senha para todos (SÓ em ambiente de teste; em produção use SSO/LDAP)")
    ap.add_argument("--bot", default="centro-operacoes", help="login do bot que posta alertas (padrão: centro-operacoes)")
    ap.add_argument("--remover", action="store_true", help="tira das salas de departamento quem não pertence mais a elas")
    ap.add_argument("--limite-usuarios", type=int, help="processa só as N primeiras pessoas (para testar)")
    a = ap.parse_args()

    if a.executar and not (a.servidor and a.token):
        sys.exit("Para --executar informe --servidor e --token (ou SYNAPSE_ADMIN_TOKEN).")

    unidades = ler_csv(a.dados, "unidades.csv")
    deptos = ler_csv(a.dados, "departamentos.csv")
    pessoas = ler_csv(a.dados, "usuarios.csv")
    nos, entradas = montar_plano(unidades, deptos, pessoas, a.dominio, a.limite_usuarios)
    s = Synapse(a.servidor, a.token, a.executar)
    bot = f"@{a.bot}:{a.dominio}"

    n_spaces = sum(1 for n in nos if n.get("space"))
    n_joins = sum(len(v[1]) for v in entradas.values())
    print(f"Plano: {n_spaces} spaces, {len(nos) - n_spaces} salas, {len(entradas)} pessoas, {n_joins} entradas em salas.")
    print("Modo:", "EXECUTANDO no servidor " + a.servidor if a.executar else "SIMULAÇÃO (use --executar para aplicar)")
    print("\nÁrvore (primeiros itens):")
    filhos = {}
    for n in nos:
        filhos.setdefault(n["pai"], []).append(n)

    def mostrar(chave, nivel, lim=[0]):
        for n in filhos.get(chave, []):
            if lim[0] >= 40:
                return
            lim[0] += 1
            print("  " * nivel + ("▸ " if n.get("space") else "# ") + f"{n['nome']}  (#{n['alias']}:{a.dominio})" + ("  🔒" if n.get("e2ee") else ""))
            mostrar(n["chave"], nivel + 1)
    print(f"▸ {nos[0]['nome']}  (#{nos[0]['alias']}:{a.dominio})")
    mostrar("orgao", 1)
    if len(nos) > 41:
        print(f"  … e mais {len(nos) - 41} spaces/salas")

    t0 = time.time()
    # 1) bot e limite de taxa do administrador
    s.garantir_usuario(bot, "Centro de Operações", senha=a.senha_teste)

    # 2) spaces e salas, de cima para baixo (pai antes do filho), idempotente pelo alias
    ids = {}
    for n in nos:
        alias = f"#{n['alias']}:{a.dominio}"
        rid = s.sala_por_alias(alias) if a.executar else None
        if not rid:
            rid = s.criar_sala(n["alias"], n["nome"], n["topico"], space=n.get("space", False), pai=ids.get(n["pai"]),
                               dominio=a.dominio, e2ee=n.get("e2ee", False), restrita=n.get("restrita", False), poder=n.get("poder"))
        ids[n["chave"]] = rid
        if n["pai"]:
            s.ligar_filho(ids[n["pai"]], rid, a.dominio)
    for chave in ("noc-alertas", "soc"):
        s.colocar_na_sala(ids[chave], bot)

    # 3) pessoas e entradas
    for i, (mxid, (nome, salas)) in enumerate(entradas.items(), 1):
        s.garantir_usuario(mxid, nome, senha=a.senha_teste)
        for chave in salas:
            if chave in ids:
                s.colocar_na_sala(ids[chave], mxid)
        if a.executar and i % 500 == 0:
            print(f"  {i}/{len(entradas)} pessoas sincronizadas ({time.time() - t0:.0f} s)")

    # 4) opcional: remover quem saiu do departamento
    if a.remover:
        esperado = {}
        for mxid, (_, salas) in entradas.items():
            for c in salas:
                esperado.setdefault(c, set()).add(mxid)
        for n in nos:
            if n["chave"].startswith("d"):
                atuais = s.membros(ids[n["chave"]]) if a.executar else set()
                for mxid in atuais - esperado.get(n["chave"], set()) - {bot}:
                    if mxid.endswith(":" + a.dominio) and not mxid.startswith("@admin"):
                        s.remover(ids[n["chave"]], mxid, "Mudou de departamento (sincronização da estrutura)")

    print(f"\n{'Concluído' if a.executar else 'Simulação concluída'}: {s.chamadas} chamadas à API"
          + (f" em {time.time() - t0:.0f} s." if a.executar else "."))
    if not a.executar:
        print("Dica: para muitas pessoas, libere o limite de taxa do administrador antes:")
        print("  POST /_synapse/admin/v1/users/@admin:" + a.dominio + "/override_ratelimit  {\"messages_per_second\":0,\"burst_count\":0}")


if __name__ == "__main__":
    main()
