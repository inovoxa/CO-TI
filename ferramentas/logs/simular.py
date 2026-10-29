"""
Simulador de logs para o CO-TI: FortiGate (filtro web, tráfego, vírus/IPS), controladores de domínio (logons,
bloqueios, contas e grupos), servidor de arquivos (acessos e exclusões) e estações (erros, serviços, desligamentos).

    python ferramentas/logs/simular.py --historico 7 --volume 300000   # preenche 7 dias (Syslog TCP, sem perda)
    python ferramentas/logs/simular.py --ao-vivo --taxa 50              # 50 mensagens/s agora (Syslog UDP + GELF UDP)
    python ferramentas/logs/simular.py --historico 7 --inventario maquinas.csv  # máquinas, IPs e usuários do inventário

maquinas.csv (nome;ip;login), tirado do CO-TI:
    docker exec coti-postgres-1 psql -U coti -d coti -At -F ";" -c "SELECT nome, ip, usuario_login FROM cmdb.ativo
        WHERE deleted = 0 AND tipo IN ('desktop', 'notebook') AND ip IS NOT NULL" > maquinas.csv

Tudo fictício (usuários, máquinas, IPs internos). Só biblioteca padrão do Python 3.10+.
"""
import argparse
import json
import random
import socket
import sys
import time
import zlib
from datetime import datetime, timedelta

SITES = [  # (hostname, categoria, peso)
    ('www.youtube.com', 'Streaming Media and Download', 30), ('accounts.youtube.com', 'Streaming Media and Download', 6),
    ('www.google.com', 'Search Engines and Portals', 25), ('mail.google.com', 'Web-based Email', 8),
    ('www.gov.br', 'Government and Legal Organizations', 10), ('www.araraquara.sp.gov.br', 'Government and Legal Organizations', 12),
    ('portal.sus.gov.br', 'Health and Wellness', 6), ('web.whatsapp.com', 'Instant Messaging', 14),
    ('www.instagram.com', 'Social Networking', 9), ('www.facebook.com', 'Social Networking', 7),
    ('g1.globo.com', 'News and Media', 8), ('www.uol.com.br', 'News and Media', 5),
    ('login.microsoftonline.com', 'Information Technology', 9), ('outlook.office365.com', 'Web-based Email', 7),
    ('www.netflix.com', 'Streaming Media and Download', 2), ('open.spotify.com', 'Streaming Media and Download', 3),
    ('www.bet365.com', 'Gambling', 0.6), ('blaze.com', 'Gambling', 0.4),
    ('www.xvideos.com', 'Pornography', 0.15), ('pt.pornhub.com', 'Pornography', 0.05),
    ('proxysite.com', 'Proxy Avoidance', 0.2), ('free-malware-download.ru', 'Malicious Websites', 0.05),
    ('poki.com', 'Games', 0.8), ('www.mercadolivre.com.br', 'Shopping', 3),
]
# bytes recebidos por sessão de internet (mín., máx.): vídeo pesa muito mais que um site comum
BYTES_SESSAO = {'www.youtube.com': (8_000_000, 450_000_000), 'www.netflix.com': (60_000_000, 1_800_000_000),
                'open.spotify.com': (2_000_000, 70_000_000), 'mail.google.com': (200_000, 15_000_000),
                'outlook.office365.com': (200_000, 20_000_000), 'web.whatsapp.com': (100_000, 30_000_000)}
BLOQUEADAS = {'Pornography', 'Gambling', 'Proxy Avoidance', 'Malicious Websites', 'Games'}
NOMES = ['joao.silva', 'maria.santos', 'ana.oliveira', 'carlos.souza', 'paula.lima', 'pedro.alves', 'juliana.costa',
         'marcos.pereira', 'fernanda.rocha', 'lucas.martins', 'camila.ribeiro', 'rafael.gomes', 'beatriz.carvalho',
         'gustavo.araujo', 'larissa.melo', 'thiago.barbosa', 'aline.cardoso', 'bruno.teixeira', 'patricia.dias',
         'rodrigo.moreira', 'vanessa.nunes', 'felipe.castro', 'renata.freitas', 'diego.ramos', 'tatiane.vieira']
SECRETARIAS = ['SAUDE', 'EDUC', 'FAZ', 'ADM', 'OBRAS', 'ASSIST', 'TI', 'GAB']
PASTAS = {'SAUDE': ['Agendamentos', 'Farmacia', 'Vigilancia'], 'EDUC': ['Matriculas', 'Merenda'],
          'FAZ': ['IPTU', 'Contabilidade'], 'ADM': ['RH', 'Licitacoes'], 'OBRAS': ['Projetos'],
          'ASSIST': ['CRAS', 'Cadastros'], 'TI': ['Instaladores', 'Documentacao'], 'GAB': ['Oficios']}


class Mundo:
    def __init__(self, rnd, inventario=None):
        self.rnd = rnd
        if inventario:
            self._do_inventario(inventario)
        else:
            self.usuarios = [n + ('' if i == 0 else str(i)) for i in range(0, 8) for n in NOMES][:180]
            self.maquinas = ['PMA-%s-%03d' % (rnd.choice(SECRETARIAS), n) for n in range(1, 241)]
            self.ip_de = {}
            for i, m in enumerate(self.maquinas):
                self.ip_de[m] = '172.%d.%d.%d' % (rnd.choice([17, 20, 21]), rnd.randint(1, 60), rnd.randint(10, 250))
            self.dono = {m: rnd.choice(self.usuarios) for m in self.maquinas}
        self.sites = [s[0] for s in SITES]
        self.pesos = [s[2] for s in SITES]
        self.cat = {s[0]: s[1] for s in SITES}

    def _do_inventario(self, arquivo):
        """Máquinas, IPs e usuários reais do inventário (nome;ip;login, login com ou sem o domínio)."""
        self.maquinas, self.ip_de, self.dono = [], {}, {}
        with open(arquivo, encoding='utf-8') as f:
            for linha in f:
                partes = linha.strip().split(';')
                if len(partes) < 2 or not partes[1]:
                    continue
                nome, ip = partes[0], partes[1]
                self.maquinas.append(nome)
                self.ip_de[nome] = ip
                if len(partes) > 2 and partes[2]:
                    self.dono[nome] = partes[2].split('\\')[-1]
        if not self.maquinas:
            sys.exit('Nenhuma máquina com IP em ' + arquivo)
        self.usuarios = sorted(set(self.dono.values())) or [n for n in NOMES]


def forti(mundo, quando):
    r = mundo.rnd
    maquina = r.choice(mundo.maquinas)
    srcip = mundo.ip_de[maquina]
    eventtime = int(quando.timestamp() * 1_000_000_000) + r.randint(0, 999_999_999)
    base = ('date=%s time=%s devname="FGT-PMA-01" devid="FG4H0FT924903004" eventtime=%d tz="-0300"'
            % (quando.strftime('%Y-%m-%d'), quando.strftime('%H:%M:%S'), eventtime))
    x = r.random()
    if x < 0.50:
        host = r.choices(mundo.sites, mundo.pesos)[0]
        cat = mundo.cat[host]
        bloqueado = cat in BLOQUEADAS
        return 5 if bloqueado else 6, (
            '%s logid="%s" type="utm" subtype="webfilter" eventtype="%s" level="%s" vd="root" policyid=%d '
            'sessionid=%d srcip=%s srcport=%d srcintf="LAN-PMA" srcintfrole="lan" dstip=%s dstport=443 '
            'dstintf="x5" dstintfrole="wan" proto=6 service="HTTPS" hostname="%s" profile="PMA-Padrao" '
            'action="%s" reqtype="direct" url="https://%s/" sentbyte=%d rcvdbyte=%d direction="outgoing" '
            'msg="%s" method="domain" cat=%d catdesc="%s"'
            % (base, '0316013056' if bloqueado else '0315012545', 'ftgd_blk' if bloqueado else 'urlfilter',
               'warning' if bloqueado else 'information', r.choice([19, 98, 133]), r.randint(3_000_000, 3_400_000),
               srcip, r.randint(40000, 65000), '%d.%d.%d.%d' % (r.choice([142, 157, 31, 89, 104]), r.randint(0, 255),
                                                               r.randint(0, 255), r.randint(1, 254)),
               host, 'blocked' if bloqueado else 'passthrough', host, r.randint(400, 3000), r.randint(0, 6000),
               'URL belongs to a denied category in policy' if bloqueado else 'URL belongs to an allowed category in policy',
               r.randint(1, 90), cat))
    if x < 0.80:
        # sessão de internet encerrada: o log de tráfego traz os bytes da sessão inteira
        host = r.choices(mundo.sites, mundo.pesos)[0]
        cat = mundo.cat[host]
        if cat in BLOQUEADAS:
            host, cat = 'www.google.com', mundo.cat['www.google.com']
        menor, maior = BYTES_SESSAO.get(host, (20_000, 4_000_000))
        recebidos = int(menor * (maior / menor) ** r.random())  # distribuição logarítmica: muitas pequenas, poucas grandes
        return 6, ('%s logid="0000000013" type="traffic" subtype="forward" eventtype="forward" level="notice" vd="root" '
                   'srcip=%s srcport=%d srcintf="LAN-PMA" srcintfrole="lan" dstip=%s dstport=443 dstintf="x5" '
                   'dstintfrole="wan" proto=6 action="close" policyid=%d service="HTTPS" hostname="%s" catdesc="%s" '
                   'duration=%d sentbyte=%d rcvdbyte=%d sentpkt=%d rcvdpkt=%d appcat="unscanned"'
                   % (base, srcip, r.randint(40000, 65000), '%d.%d.%d.%d' % (r.choice([142, 157, 31, 89, 104]),
                      r.randint(0, 255), r.randint(0, 255), r.randint(1, 254)), r.choice([19, 98, 133]), host, cat,
                      max(1, recebidos // r.randint(150_000, 600_000)), max(800, recebidos // r.randint(20, 60)), recebidos,
                      r.randint(10, 5000), max(10, recebidos // 1400)))
    if x < 0.97:
        return 6, ('%s logid="0000000013" type="traffic" subtype="forward" level="notice" vd="root" srcip=%s '
                   'srcport=%d srcintf="LAN-PMA" dstip=10.%d.%d.%d dstport=%d dstintf="DMZ" proto=%d action="%s" '
                   'service="%s" duration=%d sentbyte=%d rcvdbyte=%d'
                   % (base, srcip, r.randint(40000, 65000), r.randint(0, 3), r.randint(0, 9), r.randint(2, 40),
                      r.choice([53, 389, 445, 3389, 1433, 80]), r.choice([6, 17]),
                      r.choice(['accept'] * 20 + ['deny', 'close']), r.choice(['DNS', 'LDAP', 'SMB', 'RDP', 'MSSQL', 'HTTP']),
                      r.randint(1, 600), r.randint(100, 50000), r.randint(100, 500000)))
    sub = r.choice(['virus', 'ips'])
    return 4, ('%s logid="%s" type="utm" subtype="%s" eventtype="%s" level="warning" vd="root" srcip=%s '
               'dstip=185.%d.%d.%d action="%s" attack="%s" virus="%s" msg="%s"'
               % (base, '0211008192' if sub == 'virus' else '0419016384', sub, 'infected' if sub == 'virus' else 'signature',
                  srcip, r.randint(0, 255), r.randint(0, 255), r.randint(1, 254), 'blocked' if sub == 'virus' else 'dropped',
                  r.choice(['SMB.Login.Brute.Force', 'HTTP.URI.SQL.Injection', 'Apache.Log4j.RCE']) if sub == 'ips' else '',
                  r.choice(['W32/Agent.ABC!tr', 'JS/Miner.BP!tr', 'PDF/Phishing.A!tr']) if sub == 'virus' else '',
                  'File is infected.' if sub == 'virus' else 'attack detected'))


def evento_dc(mundo, quando):
    r = mundo.rnd
    x = r.random()
    maquina = r.choice(mundo.maquinas)
    usuario = mundo.dono[maquina] if maquina in mundo.dono and r.random() < 0.85 else r.choice(mundo.usuarios)
    if x < 0.80:
        eid, curta = 4624, 'An account was successfully logged on.'
        if r.random() < 0.35:
            usuario = maquina + '$'
        elif r.random() < 0.004:
            usuario = 'ANONYMOUS LOGON'
    elif x < 0.95:
        eid, curta = 4625, 'An account failed to log on.'
        if r.random() < 0.3:
            usuario = r.choice(['administrador', 'admin', 'root', 'teste', usuario])
    elif x < 0.975:
        eid, curta = 4740, 'A user account was locked out.'
    else:
        eid, curta = r.choice([(4720, 'A user account was created.'), (4738, 'A user account was changed.'),
                               (4738, 'A user account was changed.'), (4723, 'An attempt was made to change an account\'s password.'),
                               (4724, 'An attempt was made to reset an account\'s password.'),
                               (4728, 'A member was added to a security-enabled global group.'),
                               (4729, 'A member was removed from a security-enabled global group.'),
                               (4726, 'A user account was deleted.')])
    dc = r.choice(['DC01', 'DC02'])
    campos = {'EventID': eid, 'Channel': 'Security', 'Hostname': dc + '.pma.local', 'TargetUserName': usuario,
              'TargetDomainName': 'PMA', 'WorkstationName': maquina, 'IpAddress': mundo.ip_de[maquina],
              'LogonType': r.choice([2, 3, 3, 3, 10]) if eid in (4624, 4625) else None,
              'SubjectUserName': r.choice(['adm.ti', 'svc.provisionamento']) if eid in (4720, 4726, 4738, 4724, 4728, 4729) else dc + '$'}
    return dc, 6 if eid != 4625 else 5, curta, campos


def evento_arquivo(mundo, quando):
    r = mundo.rnd
    sec = r.choice(SECRETARIAS)
    pasta = r.choice(PASTAS[sec])
    usuario = r.choice(mundo.usuarios)
    arquivo = '%s_%s.%s' % (r.choice(['relatorio', 'planilha', 'oficio', 'lista', 'contrato', 'foto']),
                            r.randint(1, 999), r.choice(['xlsx', 'docx', 'pdf', 'jpg']))
    x = r.random()
    if x < 0.7:
        eid, curta, mascara = 5145, 'A network share object was checked to see whether client can be granted desired access.', '0x1'
    elif x < 0.93:
        eid, curta, mascara = 4663, 'An attempt was made to access an object.', r.choice(['0x1', '0x2', '0x6'])
    elif x < 0.995:
        eid, curta, mascara = 4663, 'An attempt was made to access an object.', '0x10000'
    else:
        eid, curta, mascara = 4670, 'Permissions on an object were changed.', None
    campos = {'EventID': eid, 'Channel': 'Security', 'Hostname': 'FS01.pma.local', 'SubjectUserName': usuario,
              'SubjectDomainName': 'PMA', 'ObjectName': 'D:\\Compartilhado\\%s\\%s\\%s' % (sec, pasta, arquivo),
              'ShareName': '\\\\*\\%s' % sec, 'AccessMask': mascara, 'IpAddress': r.choice(list(mundo.ip_de.values()))}
    return 'FS01', 6, curta, campos


def evento_maquina(mundo, quando):
    r = mundo.rnd
    maquina = r.choice(mundo.maquinas)
    x = r.random()
    if x < 0.55:
        eid, nivel, canal, curta = 7036, 6, 'System', 'O serviço %s entrou no estado %s.' % (
            r.choice(['Windows Update', 'Spooler de Impressão', 'Antivírus', 'Agente OCS']), r.choice(['em execução', 'parado']))
    elif x < 0.75:
        eid, nivel, canal, curta = 1000, 3, 'Application', 'Nome do aplicativo com falha: %s' % r.choice(
            ['EXCEL.EXE', 'chrome.exe', 'sigss.exe', 'OUTLOOK.EXE', 'java.exe'])
    elif x < 0.88:
        eid, nivel, canal, curta = 7034, 3, 'System', 'O serviço %s foi encerrado inesperadamente.' % r.choice(
            ['Spooler de Impressão', 'Agente OCS', 'Sincronizador de ponto'])
    elif x < 0.95:
        eid, nivel, canal, curta = 1074, 6, 'System', 'O processo C:\\Windows\\system32\\shutdown.exe iniciou o reinício.'
    elif x < 0.985:
        eid, nivel, canal, curta = 6008, 3, 'System', 'O desligamento anterior do sistema foi inesperado.'
    else:
        eid, nivel, canal, curta = 41, 2, 'System', 'O sistema reiniciou sem desligar corretamente antes.'
    campos = {'EventID': eid, 'Channel': canal, 'Hostname': maquina, 'SourceName': canal}
    return maquina, nivel, curta, campos


def linux(mundo, quando):
    r = mundo.rnd
    host = r.choice(['srv-web01', 'srv-banco01', 'srv-backup', 'srv-dns01'])
    app, texto, nivel = r.choice([
        ('sshd', 'Failed password for invalid user %s from 45.%d.%d.%d port %d ssh2' % (
            r.choice(['admin', 'oracle', 'test', 'ubuntu']), r.randint(0, 255), r.randint(0, 255), r.randint(1, 254), r.randint(20000, 60000)), 4),
        ('sshd', 'Accepted publickey for deploy from 172.21.9.140 port 52144 ssh2', 6),
        ('CRON', '(root) CMD (/usr/local/bin/backup.sh)', 6),
        ('kernel', 'EXT4-fs warning: mounting fs with errors, running e2fsck is recommended', 4),
        ('postgres', 'LOG:  checkpoint complete: wrote 1234 buffers', 6),
        ('nginx', 'upstream timed out (110: Connection timed out) while reading response header', 3),
    ])
    carimbo = '%s %d %s' % (quando.strftime('%b'), quando.day, quando.strftime('%H:%M:%S'))  # %e não existe no Windows
    return nivel, '%s %s %s[%d]: %s' % (carimbo, host, app, r.randint(100, 9999), texto)


def peso_hora(h, dow):
    expediente = 1.0 if 8 <= h < 18 else (0.35 if h in (7, 18, 19) else 0.06)
    return expediente * (0.12 if dow >= 5 else 1.0)


def gerar(mundo, quando):
    """Uma mensagem: ('syslog', texto) ou ('windows', (host, nivel, curta, campos, quando))."""
    r = mundo.rnd
    x = r.random()
    if x < 0.62:
        nivel, corpo = forti(mundo, quando)
        return 'syslog', '<%d>%s' % (16 * 8 + nivel, corpo)
    if x < 0.82:
        return 'windows', evento_dc(mundo, quando) + (quando,)
    if x < 0.90:
        return 'windows', evento_arquivo(mundo, quando) + (quando,)
    if x < 0.97:
        return 'windows', evento_maquina(mundo, quando) + (quando,)
    nivel, linha = linux(mundo, quando)
    return 'syslog', '<%d>%s' % (8 + nivel, linha)


def gelf(host, nivel, curta, campos, quando):
    d = {'version': '1.1', 'host': host, 'short_message': curta, 'timestamp': quando.timestamp(), 'level': nivel}
    for k, v in campos.items():
        if v is not None:
            d['_' + k] = v
    return json.dumps(d, ensure_ascii=False)


def windows_syslog(host, nivel, curta, campos, quando):
    """Evento do Windows como o NXLog manda por Syslog (RFC 5424 com corpo JSON)."""
    corpo = {k: v for k, v in campos.items() if v is not None}
    corpo['Message'] = curta
    return '<%d>1 %s %s Microsoft-Windows - - - %s' % (
        8 * 10 + nivel, quando.astimezone().isoformat(timespec='milliseconds'), host.split('.')[0],
        json.dumps(corpo, ensure_ascii=False))


def historico(args, mundo):
    agora = datetime.now()
    inicio = agora - timedelta(days=args.historico)
    horas = []
    t = inicio.replace(minute=0, second=0, microsecond=0)
    while t < agora:
        horas.append((t, peso_hora(t.hour, t.weekday()) * mundo.rnd.uniform(0.8, 1.2)))
        t += timedelta(hours=1)
    soma = sum(p for _, p in horas)
    s = socket.create_connection((args.host, args.porta_syslog))
    enviadas, lote, ini = 0, [], time.time()
    for hora, p in horas:
        n = round(args.volume * p / soma)
        for _ in range(n):
            quando = hora + timedelta(seconds=mundo.rnd.uniform(0, 3600))
            if quando > agora:
                continue
            tipo, dado = gerar(mundo, quando)
            texto = dado if tipo == 'syslog' else windows_syslog(*dado)
            lote.append(texto.encode('utf-8') + b'\n')
            if len(lote) >= 2000:
                s.sendall(b''.join(lote))
                enviadas += len(lote)
                lote.clear()
                if enviadas % 50000 < 2000:
                    print('  %s mensagens (%s)' % (f'{enviadas:,}'.replace(',', '.'), hora.strftime('%d/%m %Hh')), flush=True)
                time.sleep(0.05)  # dá fôlego à fila do CO-TI
    if lote:
        s.sendall(b''.join(lote))
        enviadas += len(lote)
    s.close()
    print('Histórico: %s mensagens em %.0f s' % (f'{enviadas:,}'.replace(',', '.'), time.time() - ini))


def ao_vivo(args, mundo):
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print('Mandando %d mensagens/s para %s (Syslog UDP %d, GELF UDP %d). Ctrl+C para parar.'
          % (args.taxa, args.host, args.porta_syslog, args.porta_gelf))
    total = 0
    try:
        while True:
            ini = time.time()
            for _ in range(args.taxa):
                tipo, dado = gerar(mundo, datetime.now())
                if tipo == 'syslog':
                    udp.sendto(dado.encode('utf-8'), (args.host, args.porta_syslog))
                else:
                    corpo = gelf(*dado).encode('utf-8')
                    udp.sendto(zlib.compress(corpo) if mundo.rnd.random() < 0.5 else corpo, (args.host, args.porta_gelf))
                total += 1
            time.sleep(max(0.0, 1 - (time.time() - ini)))
            if total % (args.taxa * 30) < args.taxa:
                print('  %d mensagens enviadas' % total, flush=True)
    except KeyboardInterrupt:
        print('\n%d mensagens enviadas' % total)


def main():
    ap = argparse.ArgumentParser(description='Simulador de logs do CO-TI')
    ap.add_argument('--host', default='127.0.0.1')
    ap.add_argument('--porta-syslog', type=int, default=514)
    ap.add_argument('--porta-gelf', type=int, default=12201)
    ap.add_argument('--historico', type=float, help='preenche N dias para trás (Syslog TCP)')
    ap.add_argument('--volume', type=int, default=200_000, help='mensagens no histórico (padrão 200 mil)')
    ap.add_argument('--ao-vivo', action='store_true', help='manda mensagens agora, sem parar')
    ap.add_argument('--taxa', type=int, default=20, help='mensagens por segundo no modo ao vivo')
    ap.add_argument('--inventario', help='CSV nome;ip;login com as máquinas do inventário (padrão: máquinas fictícias)')
    ap.add_argument('--seed', type=int, default=2026)
    a = ap.parse_args()
    if not a.historico and not a.ao_vivo:
        ap.print_help()
        sys.exit(1)
    mundo = Mundo(random.Random(a.seed), a.inventario)
    if a.historico:
        historico(a, mundo)
    if a.ao_vivo:
        ao_vivo(a, mundo)


if __name__ == '__main__':
    main()
