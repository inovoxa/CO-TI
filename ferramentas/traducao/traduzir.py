"""
Tradução zh-CN → pt-BR do código herdado do RuoYi (só texto visível; comentários ficam como estão).

  python traduzir.py extrair   → grava pendentes.json com as unidades ainda sem tradução
  python traduzir.py aplicar   → troca no código as unidades que estão nos dicionarios/*.json
  python traduzir.py conferir  → lista o que ainda está em chinês no escopo

Depois de puxar uma atualização do RuoYi: rode "aplicar"; o que for novo aparece em "extrair".
O escopo (o que o CO-TI exibe) está em ESCOPO abaixo.
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import lexico  # noqa: E402

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
F = 'frontend/src/'
B = 'backend/'

ESCOPO = [
    # frontend: base
    F + 'layout/**/*', F + 'utils/**/*', F + 'hooks/**/*', F + 'store/**/*', F + 'router/**/*',
    F + 'directives/**/*', F + 'plugins/**/*', F + 'config/**/*', F + 'permission.ts', F + 'main.ts',
    F + 'views/Login/**/*', F + 'views/Profile/**/*', F + 'views/Home/**/*', F + 'views/Error/**/*',
    F + 'views/Redirect/**/*',
    # componentes usados pelas telas mantidas
    F + 'components/Crontab/**/*', F + 'components/UploadFile/**/*', F + 'components/Verifition/**/*',
    F + 'components/UserSelectForm/**/*', F + 'components/DeptSelectForm/**/*',
    F + 'components/ShortcutDateRangePicker/**/*', F + 'components/CountTo/**/*', F + 'components/FilePreview/**/*',
    F + 'components/Table/**/*', F + 'components/RouterSearch/**/*', F + 'components/OperateLogV2/**/*',
    F + 'components/Draggable/**/*', F + 'components/Editor/**/*', F + 'components/JsonEditor/**/*',
    F + 'components/Dialog/**/*', F + 'components/DocAlert/**/*', F + 'components/Icon/**/*',
    F + 'components/MarkdownView/**/*', F + 'components/Pagination/**/*', F + 'components/Form/**/*',
    F + 'components/Search/**/*', F + 'components/Descriptions/**/*', F + 'components/DictTag/**/*',
    F + 'components/ContentWrap/**/*', F + 'components/InputPassword/**/*', F + 'components/Cropper/**/*',
    F + 'components/ImageViewer/**/*', F + 'components/Qrcode/**/*', F + 'components/Highlight/**/*',
    F + 'components/Tooltip/**/*', F + 'components/XButton/**/*', F + 'components/Infotip/**/*',
    F + 'components/IFrame/**/*', F + 'components/ContentDetailWrap/**/*', F + 'components/Echart/**/*',
    F + 'components/Error/**/*', F + 'components/Sticky/**/*', F + 'components/DictSelect/**/*',
    # telas de Sistema e Infraestrutura mantidas no CO-TI
    *[F + 'views/system/%s/**/*' % d for d in
      ['user', 'dept', 'role', 'menu', 'post', 'dict', 'notice', 'notify', 'mail', 'loginlog', 'operatelog']],
    *[F + 'views/infra/%s/**/*' % d for d in
      ['job', 'file', 'fileConfig', 'config', 'apiAccessLog', 'apiErrorLog', 'redis', 'webSocket', 'codegen']],
    F + 'api/system/**/*', F + 'api/infra/**/*', F + 'api/login/**/*',
    # backend: mensagens que chegam ao usuário
    B + 'yudao-framework/yudao-common/src/main/java/**/exception/enums/*.java',
    B + 'yudao-module-system/src/main/java/**/enums/ErrorCodeConstants.java',
    B + 'yudao-module-infra/src/main/java/**/enums/ErrorCodeConstants.java',
    B + 'yudao-module-system/src/main/java/**/controller/admin/**/vo/**/*.java',
    B + 'yudao-module-infra/src/main/java/**/controller/admin/**/vo/**/*.java',
    B + 'yudao-module-system/src/main/java/**/service/oauth2/OAuth2TokenServiceImpl.java',
    # backend: respostas de erro do framework (validação, tratador global, proteção, sessão)
    B + 'yudao-framework/yudao-common/src/main/java/**/validation/*.java',
    B + 'yudao-framework/yudao-common/src/main/java/**/pojo/PageParam.java',
    B + 'yudao-framework/yudao-common/src/main/java/**/util/object/PageUtils.java',
    B + 'yudao-framework/yudao-spring-boot-starter-web/src/main/java/**/handler/GlobalExceptionHandler.java',
    B + 'yudao-framework/yudao-spring-boot-starter-web/src/main/java/**/encrypt/core/filter/ApiEncryptFilter.java',
    B + 'yudao-framework/yudao-spring-boot-starter-security/src/main/java/**/filter/TokenAuthenticationFilter.java',
    B + 'yudao-framework/yudao-spring-boot-starter-protection/src/main/java/**/annotation/*.java',
    B + 'yudao-framework/yudao-spring-boot-starter-protection/src/main/java/**/signature/core/aop/ApiSignatureAspect.java',
    B + 'yudao-framework/yudao-spring-boot-starter-excel/src/main/java/**/dict/validation/InDict.java',
    B + 'yudao-framework/yudao-spring-boot-starter-excel/src/main/java/**/handler/SelectSheetWriteHandler.java',
]
EXTENSOES = ('.vue', '.ts', '.js', '.java')


def arquivos():
    vistos = set()
    for padrao in ESCOPO:
        for p in glob.glob(os.path.join(RAIZ, padrao), recursive=True):
            if os.path.isfile(p) and p.endswith(EXTENSOES) and p not in vistos:
                vistos.add(p)
                yield p


def dicionario():
    d = {}
    for p in sorted(glob.glob(os.path.join(os.path.dirname(__file__), 'dicionarios', '*.json'))):
        d.update(json.load(open(p, encoding='utf-8')))
    return d


def principal(cmd):
    dic = dicionario()
    faltando = {}
    alterados = 0
    for p in arquivos():
        ext = os.path.splitext(p)[1]
        texto = open(p, encoding='utf-8').read()
        if cmd == 'aplicar':
            novo = lexico.traduzir_arquivo(texto, ext, dic)
            if novo != texto:
                open(p, 'w', encoding='utf-8', newline='').write(novo)
                alterados += 1
        for u in lexico.unidades(texto if cmd != 'aplicar' else novo, ext):
            if u not in dic:
                faltando.setdefault(u, os.path.relpath(p, RAIZ).replace('\\', '/'))
    if cmd == 'extrair':
        destino = os.path.join(os.path.dirname(__file__), 'pendentes.json')
        json.dump(faltando, open(destino, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print(f'{len(faltando)} unidades sem tradução → {destino}')
    elif cmd == 'aplicar':
        print(f'{alterados} arquivos alterados; {len(faltando)} unidades ainda sem tradução')
    else:
        for u, p in sorted(faltando.items(), key=lambda x: x[1]):
            print(f'{p}: {u}')
        print(f'{len(faltando)} unidades sem tradução')


if __name__ == '__main__':
    principal(sys.argv[1] if len(sys.argv) > 1 else 'conferir')
