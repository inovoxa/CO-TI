"""
Troca as unidades e os monitores do CO-TI pelos da planilha de faixas de IP da rede (ip_unidades_*.csv).

    python ferramentas/unidades/importar_planilha.py ip_unidades.csv > importar.sql
    docker compose exec -T postgres psql -U coti -d coti -v ON_ERROR_STOP=1 -1 -f - < importar.sql
    docker compose restart backend      # os monitores voltam a ser agendados a partir do banco

Faça um backup antes (docker compose exec backup bash /backup/backup.sh agora): o SQL APAGA todos os monitores, com o
histórico de verificações, e desativa as unidades atuais. Para cada linha da planilha cria:
  - um departamento com o nome da unidade, dentro da secretaria deduzida do nome (USF/CMS -> Saúde, EMEF/CER ->
    Educação, CRAS -> Assistência Social...; sem palpite, direto na raiz);
  - a unidade no mapa (endereço e coordenadas da planilha). Linha sem coordenadas não vira unidade: fica só o monitor;
  - o monitor do link: ping no .1 da faixa, papel "principal".

Colunas: id, faixa_ip (CIDR), nome_unidade, logradouro, localizacao_geografica ("'-21.79, -48.17").
Só biblioteca padrão do Python 3.10+.
"""
import csv
import ipaddress
import re
import sys
import unicodedata

# (secretaria, padrões no nome sem acento e em maiúsculas); a primeira que casar vale
SECRETARIAS = [
    ('Saúde', r'\b(USF|UBS|CMS|PSF|ESF|CAPS|UPA|CEO|CEREST|SAMU|UMED|ODONTO|HOSPITAL|SAUDE|CTA DST|POSTO DE ATO|'
              r'VIG\. EPIDEM|FAUNA|CMRAUTISMO|REABILITACAO|AMB\.)'),
    ('Educação', r'\b(EMEF|CER|CE|CEC|CAEE|NEJA|PEC|CURSINHO|TRANSPORTE ESCOLAR|ALIMENTACAO ESCOLAR|SME|POLO UNIVESP)\b'),
    ('Assistência Social', r'\b(CRAS|CRASS|CRASMAA|CREAS|CRIA|CASA ABRIGO|CASA DE ACOLHIDA|CENTRO DIA|CENTRO POPULAR|'
                           r'ASSISTENCIA SOCIAL|CONSELHO TUTELAR|RESTAURANTE POPULAR|FUNDO SOCIAL|VILA DIGNIDADE|RECANTO FELIZ)'),
    ('Cultura', r'\b(BIBLIOTECA|MUSEU|TEATRO|ARQUIVO HISTORICO|CEU ARTES|CENTRO ARTES|ESCOLA DE DANCA|SEC CULTURA)'),
    ('Esportes e Lazer', r'\b(GINASIO|GINASTICA|COMPLEXO AQUATICO|GIGANTAO)'),
    ('Obras e Serviços Públicos', r'\b(CEMITERIO|SERRALHEIRO)'),
    ('Meio Ambiente', r'\b(BOTANICO|PARQUE BASALTO)'),
]
CEP = re.compile(r'\b(\d{5})-?(\d{3})\b')


def sem_acento(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s) if unicodedata.category(c) != 'Mn')


def nome_limpo(bruto):
    """'CMS SANTA LUCIA III (172.19.40.0)' -> 'CMS SANTA LUCIA III'; 'CER_EDUARDO_BORGES' -> 'CER EDUARDO BORGES'."""
    s = re.sub(r'\s*\(\d{1,3}(\.\d{1,3}){3}\)\s*', ' ', bruto).replace('_', ' ')
    return re.sub(r'\s+', ' ', s).strip()


def secretaria(nome):
    chave = sem_acento(nome).upper()
    for sec, padrao in SECRETARIAS:
        if re.search(padrao, chave):
            return sec
    return None


def endereco(texto):
    """'R. X, 115 - Jardim Iedda, Araraquara - SP, 14801-140' -> logradouro, número, bairro, CEP."""
    t = (texto or '').strip()
    if not t:
        return None, None, None, None
    cep = CEP.search(t)
    cep = f'{cep.group(1)}-{cep.group(2)}' if cep else None
    t = CEP.sub('', t)
    t = re.sub(r',?\s*Araraquara\s*-\s*SP\b', '', t, flags=re.I).strip(' ,-')
    rua, _, bairro = t.partition(' - ')
    partes = [p.strip() for p in rua.split(',')]
    numero = partes[1] if len(partes) > 1 and re.fullmatch(r'(N°\s*)?(\d+\w?|s/n)', partes[1], re.I) else None
    logradouro = partes[0] if numero else rua.strip()
    bairro = re.sub(r'\s*\(.*\)$', '', bairro.split(',')[0]).strip() or None
    return logradouro[:200] or None, numero, bairro[:100] if bairro else None, cep


def coordenadas(texto):
    t = (texto or '').strip().lstrip("'")
    m = re.fullmatch(r'(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)', t)
    return (m.group(1), m.group(2)) if m else (None, None)


def q(v):
    return 'NULL' if v is None else "'" + str(v).replace("'", "''") + "'"


def main(caminho):
    linhas = list(csv.DictReader(open(caminho, encoding='utf-8-sig')))
    out = ['-- gerado por ferramentas/unidades/importar_planilha.py a partir de ' + caminho.replace('\\', '/').split('/')[-1],
           'DELETE FROM monitor.batida;', 'DELETE FROM monitor.batida_hora;', 'DELETE FROM monitor.manutencao_monitor;',
           'DELETE FROM monitor.manutencao;', 'DELETE FROM monitor.monitor_canal;', 'DELETE FROM monitor.monitor;',
           "UPDATE monitor_unit SET deleted = 1, updater = '1', update_time = now() WHERE deleted = 0;",
           'CREATE TEMP TABLE _sec (nome varchar, id bigint) ON COMMIT DROP;',
           'CREATE TEMP TABLE _dept (n int, d bigint, u bigint) ON COMMIT DROP;',
           "INSERT INTO _sec SELECT s.nome, COALESCE((SELECT id FROM system_dept WHERE name = s.nome AND deleted = 0 ORDER BY id LIMIT 1),"
           " (SELECT id FROM system_dept WHERE parent_id = 0 AND deleted = 0 ORDER BY id LIMIT 1))"
           " FROM (VALUES " + ', '.join(f'({q(s)})' for s, _ in SECRETARIAS) + ", ('')) AS s(nome);"]
    resumo = {}
    for i, x in enumerate(linhas, 1):
        nome = nome_limpo(x['nome_unidade'])
        rede = ipaddress.ip_network(x['faixa_ip'].strip(), strict=False)
        alvo = str(rede.network_address + 1)
        sec = secretaria(nome)
        resumo[sec or '(raiz)'] = resumo.get(sec or '(raiz)', 0) + 1
        lat, lon = coordenadas(x['localizacao_geografica'])
        rua, numero, bairro, cep = endereco(x['logradouro'])
        dept = f'(SELECT d FROM _dept WHERE n = {i})'
        out.append(f"WITH novo AS (INSERT INTO system_dept (id, name, parent_id, sort, status, creator, updater, deleted, tenant_id)"
                   f" SELECT nextval('system_dept_seq'), {q(nome)}, id, {i}, 0, '1', '1', 0, 1 FROM _sec WHERE nome = {q(sec or '')}"
                   f" RETURNING id) INSERT INTO _dept (n, d) SELECT {i}, id FROM novo;")
        unidade = 'NULL'
        if lat:
            out.append(f"WITH novo AS (INSERT INTO monitor_unit (id, dept_id, name, city, uf, latitude, longitude, status, cep,"
                       f" logradouro, numero, bairro, creator, updater, deleted) VALUES (nextval('monitor_unit_seq'), {dept},"
                       f" {q(nome)}, 'Araraquara', 'SP', {lat}, {lon}, 0, {q(cep)}, {q(rua)}, {q(numero)}, {q(bairro)}, '1', '1', 0)"
                       f" RETURNING id) UPDATE _dept SET u = (SELECT id FROM novo) WHERE n = {i};")
            unidade = f'(SELECT u FROM _dept WHERE n = {i})'
        descricao = f'Faixa {rede}' + ('' if lat else ' · sem localização na planilha')
        out.append(f"INSERT INTO monitor.monitor (id, nome, descricao, tipo, alvo, intervalo_s, tentativas, intervalo_tentativa_s,"
                   f" tempo_limite_s, lento_ms, unidade_id, papel, dept_id, creator, updater) VALUES (nextval('monitor.monitor_seq'),"
                   f" {q('Link · ' + nome)}, {q(descricao)}, 'ping', {q(alvo)}, 60, 1, 30, 10, 1500, {unidade},"
                   f" {'principal' if lat else 'servico'!r}, {dept}, '1', '1');")
    sys.stdout.reconfigure(encoding='utf-8')
    print('\n'.join(out))
    print(f'{len(linhas)} linhas: ' + ', '.join(f'{k} {v}' for k, v in sorted(resumo.items())), file=sys.stderr)


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
