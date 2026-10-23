"""
Localiza texto visível ao usuário em arquivos .vue / .ts / .js / .java, sem tocar em comentários.

Uma "unidade" é o conteúdo inteiro de:
  - um literal de string ('...', "...", `...`) que contenha CJK;
  - um nó de texto de template Vue (entre tags), já sem espaços nas pontas;
  - um valor de atributo não-ligado de template Vue (placeholder="...", label="...").
Atributos ligados (:x, v-, @, #) e {{ }} são tratados como JavaScript.

traduzir_arquivo(texto, extensao, dicionario) devolve o texto com as unidades trocadas.
"""
import re

CJK = re.compile(r'[　-〿㐀-䶿一-鿿＀-￯]')


def tem_cjk(s):
    return bool(CJK.search(s))


# ---------------------------------------------------------------- JS / TS / Java

def spans_js(texto, inicio=0, fim=None, java=False):
    """Devolve (ini, fim) do CONTEÚDO de cada literal de string entre inicio e fim."""
    fim = len(texto) if fim is None else fim
    i = inicio
    out = []
    while i < fim:
        c = texto[i]
        if c == '/' and i + 1 < fim and texto[i + 1] == '/':
            j = texto.find('\n', i)
            i = fim if j < 0 or j > fim else j
        elif c == '/' and i + 1 < fim and texto[i + 1] == '*':
            j = texto.find('*/', i + 2)
            i = fim if j < 0 else j + 2
        elif c in '"\'' or (c == '`' and not java):
            j = i + 1
            while j < fim:
                if texto[j] == '\\':
                    j += 2
                    continue
                if texto[j] == c:
                    break
                if c == '`' and texto[j] == '$' and j + 1 < fim and texto[j + 1] == '{':
                    # pula a expressão interpolada inteira (pode ter strings dentro)
                    prof = 1
                    j += 2
                    while j < fim and prof:
                        if texto[j] == '{':
                            prof += 1
                        elif texto[j] == '}':
                            prof -= 1
                        j += 1
                    continue
                if c != '`' and texto[j] == '\n':
                    break  # literal mal formado; não arrisca
                j += 1
            out.append((i + 1, j))
            i = j + 1
        else:
            i += 1
    return out


# ---------------------------------------------------------------- template Vue

def spans_template(texto, inicio, fim):
    """Unidades de um bloco <template>: nós de texto, atributos simples e JS dos ligados/mustache."""
    out = []
    i = inicio
    while i < fim:
        if texto.startswith('<!--', i):
            j = texto.find('-->', i)
            i = fim if j < 0 else j + 3
        elif texto[i] == '<':
            i = _tag(texto, i, fim, out)
        else:
            j = i
            while j < fim and texto[j] != '<':
                if texto.startswith('{{', j):
                    k = texto.find('}}', j)
                    j = fim if k < 0 else k + 2
                else:
                    j += 1
            _texto_no(texto, i, j, out)
            i = j
    return out


def _texto_no(texto, ini, fim, out):
    trecho = texto[ini:fim]
    if not tem_cjk(trecho):
        return
    # unidade = texto sem espaços nas pontas (mantém {{ }} dentro, o tradutor preserva)
    a = ini + (len(trecho) - len(trecho.lstrip()))
    b = fim - (len(trecho) - len(trecho.rstrip()))
    # se só há CJK dentro de mustache, trata como JS
    sem_mustache = re.sub(r'\{\{.*?\}\}', '', texto[a:b], flags=re.S)
    if tem_cjk(sem_mustache):
        out.append((a, b))
    else:
        for m in re.finditer(r'\{\{(.*?)\}\}', texto[a:b], flags=re.S):
            out.extend(spans_js(texto, a + m.start(1), a + m.end(1)))


def _tag(texto, i, fim, out):
    j = i + 1
    while j < fim and texto[j] != '>':
        c = texto[j]
        if c in '"\'':
            k = texto.find(c, j + 1)
            k = fim if k < 0 else k
            # nome do atributo: palavra antes do =
            m = re.search(r'([:@#]?[\w\-.:@#\[\]]+)\s*=\s*$', texto[i:j])
            nome = m.group(1) if m else ''
            if nome.startswith((':', '@', '#', 'v-')):
                out.extend(spans_js(texto, j + 1, k))
            elif tem_cjk(texto[j + 1:k]):
                out.append((j + 1, k))
            j = k + 1
        else:
            j += 1
    return j + 1


# ---------------------------------------------------------------- arquivo inteiro

def spans_arquivo(texto, ext):
    if ext == '.vue':
        out = []
        for m in re.finditer(r'<template[^>]*>', texto):
            # o primeiro <template> de nível superior vai até o último </template>
            fim = texto.rfind('</template>')
            out.extend(spans_template(texto, m.end(), fim))
            break
        for m in re.finditer(r'<script[^>]*>(.*?)</script>', texto, flags=re.S):
            out.extend(spans_js(texto, m.start(1), m.end(1)))
        return out
    spans = spans_js(texto, java=(ext == '.java'))
    if ext == '.java' and 'ErrorCode' not in texto:
        # VO do backend: só mensagens de validação chegam ao usuário (@Schema é documentação de API)
        spans = [(a, b) for a, b in spans if re.search(r'message\s*=\s*$', texto[max(0, a - 40):a - 1])]
    return spans


def unidades(texto, ext):
    return [texto[a:b] for a, b in spans_arquivo(texto, ext) if tem_cjk(texto[a:b])]


def traduzir_arquivo(texto, ext, dic, faltando=None):
    partes = []
    ult = 0
    for a, b in sorted(set(spans_arquivo(texto, ext))):
        u = texto[a:b]
        if not tem_cjk(u):
            continue
        if u in dic:
            partes.append(texto[ult:a])
            partes.append(dic[u])
            ult = b
        elif faltando is not None:
            faltando.add(u)
    partes.append(texto[ult:])
    return ''.join(partes)
