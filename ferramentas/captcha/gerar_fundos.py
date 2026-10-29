"""
Gera os fundos do captcha de login (310x155): paisagens abstratas, céu, cidade e rede, sem fotos de terceiros.

    python ferramentas/captcha/gerar_fundos.py

Grava em backend/yudao-module-system/src/main/resources/images/{jigsaw/original,pic-click}. A mesma semente gera as
mesmas imagens. Precisa do Pillow (pip install pillow).
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

RAIZ = os.path.join(os.path.dirname(__file__), '..', '..', 'backend', 'yudao-module-system', 'src', 'main',
                    'resources', 'images')
L, A = 310, 155
ESCALA = 2  # desenha em dobro e reduz, para bordas suaves

PALETAS = [  # (céu em cima, céu embaixo, morros, luzes)
    ((16, 42, 92), (64, 156, 214), (12, 30, 60), (255, 220, 140)),
    ((250, 140, 90), (255, 214, 150), (90, 50, 80), (255, 250, 220)),
    ((20, 90, 110), (140, 210, 190), (16, 60, 64), (230, 255, 240)),
    ((60, 30, 110), (230, 120, 160), (40, 20, 70), (255, 230, 250)),
    ((30, 60, 40), (170, 210, 120), (30, 70, 40), (250, 255, 210)),
    ((10, 20, 40), (40, 80, 140), (8, 14, 30), (120, 220, 255)),
    ((120, 170, 230), (230, 240, 250), (70, 110, 150), (255, 255, 255)),
    ((200, 80, 40), (250, 190, 90), (70, 40, 30), (255, 240, 200)),
]


def mistura(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def degrade(img, topo, base):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for y in range(h):
        d.line([(0, y), (w, y)], fill=mistura(topo, base, y / (h - 1)))


def bolhas(img, rnd, cor, qtd):
    camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)
    w, h = img.size
    for _ in range(qtd):
        r = rnd.randint(6, 40) * ESCALA
        x, y = rnd.randint(0, w), rnd.randint(0, int(h * 0.75))
        d.ellipse([x - r, y - r, x + r, y + r], fill=cor + (rnd.randint(25, 90),))
    camada = camada.filter(ImageFilter.GaussianBlur(3 * ESCALA))
    img.alpha_composite(camada)


def morros(img, rnd, cor, camadas=3):
    w, h = img.size
    for c in range(camadas):
        base = h * (0.55 + 0.13 * c)
        amp = h * rnd.uniform(0.06, 0.16)
        f1, f2 = rnd.uniform(1.0, 3.0), rnd.uniform(3.0, 7.0)
        fase = rnd.uniform(0, 6.28)
        pts = [(0, h)]
        for x in range(0, w + 1, 4):
            t = x / w * 2 * math.pi
            pts.append((x, base - amp * (math.sin(t * f1 + fase) * 0.7 + math.sin(t * f2 + fase * 2) * 0.3)))
        pts.append((w, h))
        claro = 0.45 - 0.18 * c
        ImageDraw.Draw(img).polygon(pts, fill=mistura(cor, (255, 255, 255), max(0, claro)) + (255,))


def cidade(img, rnd, cor, luz):
    d = ImageDraw.Draw(img)
    w, h = img.size
    x = 0
    while x < w:
        lw = rnd.randint(14, 34) * ESCALA
        lh = rnd.randint(int(h * 0.18), int(h * 0.55))
        d.rectangle([x, h - lh, x + lw, h], fill=cor + (255,))
        for jy in range(h - lh + 6 * ESCALA, h - 4 * ESCALA, 7 * ESCALA):
            for jx in range(x + 3 * ESCALA, x + lw - 4 * ESCALA, 6 * ESCALA):
                if rnd.random() < 0.45:
                    d.rectangle([jx, jy, jx + 2 * ESCALA, jy + 3 * ESCALA], fill=luz + (255,))
        x += lw + rnd.randint(0, 4) * ESCALA


def rede(img, rnd, cor):
    camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)
    w, h = img.size
    nos = [(rnd.randint(0, w), rnd.randint(0, h)) for _ in range(34)]
    for i, a in enumerate(nos):
        for b in nos[i + 1:]:
            if math.dist(a, b) < 90 * ESCALA:
                d.line([a, b], fill=cor + (110,), width=ESCALA)
    for x, y in nos:
        r = rnd.randint(2, 5) * ESCALA
        d.ellipse([x - r, y - r, x + r, y + r], fill=cor + (230,))
    img.alpha_composite(camada)


def sol(img, rnd, cor):
    camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)
    w, h = img.size
    x, y, r = rnd.randint(int(w * 0.15), int(w * 0.85)), rnd.randint(int(h * 0.15), int(h * 0.4)), 22 * ESCALA
    for k in range(6, 0, -1):
        d.ellipse([x - r * k / 2, y - r * k / 2, x + r * k / 2, y + r * k / 2], fill=cor + (int(170 / k),))
    img.alpha_composite(camada.filter(ImageFilter.GaussianBlur(2 * ESCALA)))


def textura(img, rnd):
    """Granulado leve: deixa o encaixe da peça visível sem ficar óbvio para um robô."""
    w, h = img.size
    px = img.load()
    for _ in range(w * h // 6):
        x, y = rnd.randrange(w), rnd.randrange(h)
        r, g, b, a = px[x, y]
        v = rnd.randint(-14, 14)
        px[x, y] = (max(0, min(255, r + v)), max(0, min(255, g + v)), max(0, min(255, b + v)), a)


def fundo(n, rnd):
    topo, base, terra, luz = PALETAS[n % len(PALETAS)]
    img = Image.new('RGBA', (L * ESCALA, A * ESCALA))
    degrade(img, topo, base)
    estilo = n % 4
    if estilo in (0, 2):
        sol(img, rnd, luz)
    bolhas(img, rnd, luz, 26)
    if estilo == 1:
        rede(img, rnd, luz)
        morros(img, rnd, terra, 2)
    elif estilo == 3:
        rede(img, rnd, luz)
        cidade(img, rnd, terra, luz)
    else:
        morros(img, rnd, terra, 3)
        if estilo == 2:
            cidade(img, rnd, mistura(terra, (0, 0, 0), 0.3), luz)
    img = img.resize((L, A), Image.LANCZOS)
    textura(img, rnd)
    return img.convert('RGB')


def main():
    rnd = random.Random(2026)
    for pasta, qtd, prefixo in [('jigsaw/original', 12, 0), ('pic-click', 10, 5)]:
        destino = os.path.normpath(os.path.join(RAIZ, pasta))
        for f in os.listdir(destino):
            if f.lower().endswith('.png'):
                os.remove(os.path.join(destino, f))
        for i in range(qtd):
            fundo(i + prefixo, rnd).save(os.path.join(destino, 'bg%d.png' % (i + 1)), optimize=True)
        print('%s: %d imagens' % (destino, qtd))


if __name__ == '__main__':
    main()
