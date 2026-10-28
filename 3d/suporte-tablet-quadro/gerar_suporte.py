"""Suporte "sleeve" para Xiaomi Redmi Pad 2 (11") montado na tampa do quadro eléctrico.

O tablet entra por cima (lado aberto) e desliza até à calha inferior.
Duas peças (esquerda/direita) unidas por encaixes em cauda de andorinha,
impressas deitadas (costas na mesa), sem suportes.

Eixos: X = largura, Y = altura (vertical na parede), Z = profundidade (0 = parede).

Uso: python3 gerar_suporte.py   ->  gera os .3mf nesta pasta
"""
import os
import zipfile

import numpy as np
from manifold3d import CrossSection, JoinType, Manifold

# --- Tablet (Redmi Pad 2: 254.58 x 166.04 x 7.36 mm) ------------------------
TAB_W, TAB_H, TAB_T = 254.58, 166.04, 7.36
FOLGA_XY = 1.0          # folga de cada lado (largura)
FOLGA_Z = 1.0           # folga total na espessura (usa ~2.5 se tiver capa fina)

# --- Suporte ----------------------------------------------------------------
PAREDE = 4.0            # espessura das paredes laterais e calha inferior
COSTAS = 3.0            # espessura das costas
ABA = 2.2               # espessura da aba frontal
ABA_SOBREPOE = 5.0      # quanto a aba cobre a moldura do ecrã
ALTURA = 162.0          # altura total (deixa ~8 mm do tablet de fora para pegar)
RAIO = 6.0              # raio dos cantos
CHANFRO = 1.0           # chanfro da aresta frontal exterior

RANHURA_USB_H = 30.0    # altura da ranhura lateral para o cabo USB-C
POLEGAR_R = 14.0        # entalhe para o polegar na aba inferior
FURO_D, FURO_CAB_D = 4.2, 8.4   # furos escareados p/ parafuso M4 (ou M3)
FOLGA_ENCAIXE = 0.15    # folga das caudas de andorinha

PW = TAB_W + 2 * FOLGA_XY          # largura do bolso
PD = TAB_T + FOLGA_Z               # profundidade do bolso
W = PW + 2 * PAREDE                # largura total
D = COSTAS + PD + ABA              # profundidade total
Y_BOLSO = PAREDE                   # fundo do bolso (onde o tablet assenta)
Y_CENTRO_TAB = Y_BOLSO + TAB_H / 2
BIG = 1000.0

def box(x0, x1, y0, y1, z0, z1):
    return Manifold.cube((x1 - x0, y1 - y0, z1 - z0)).translate((x0, y0, z0))


def rrect(w, h, r):
    """Retângulo de cantos arredondados centrado na origem."""
    return CrossSection.square((w - 2 * r, h - 2 * r), center=True).offset(
        r, JoinType.Round, circular_segments=64)


def corpo():
    # Contorno exterior com chanfro na frente
    base = Manifold.extrude(rrect(W, ALTURA, RAIO), D - CHANFRO)
    topo = Manifold.extrude(rrect(W, ALTURA, RAIO), CHANFRO,
                            scale_top=((W - 2 * CHANFRO) / W,
                                       (ALTURA - 2 * CHANFRO) / ALTURA))
    s = base + topo.translate((0, 0, D - CHANFRO))
    s = s.translate((0, ALTURA / 2, 0))

    # Bolso do tablet (aberto em cima)
    s -= box(-PW / 2, PW / 2, Y_BOLSO, BIG, COSTAS, COSTAS + PD)

    # Abertura frontal (deixa a aba de ABA_SOBREPOE mm) com chanfro interior
    ow = PW - 2 * ABA_SOBREPOE
    oy0 = Y_BOLSO + ABA_SOBREPOE
    oh = 2 * (ALTURA + 10 - oy0)             # centrado em oy0+oh/2, sai por cima
    ab = Manifold.extrude(rrect(ow, oh, 3), D).translate((0, oy0 + oh / 2, COSTAS + PD - 0.01))
    c = 1.2
    ch = Manifold.extrude(rrect(ow, oh, 3), c + 0.01,
                          scale_top=((ow + 2 * c) / ow, (oh + 2 * c) / oh))
    ch = ch.translate((0, oy0 + oh / 2, D - c))
    s -= ab + ch

    # Entalhe para o polegar (empurrar o tablet para cima ao retirar)
    s -= Manifold.cylinder(D, POLEGAR_R, circular_segments=96).translate(
        (0, oy0, COSTAS + PD - 0.01))

    # Ranhuras laterais para o cabo USB-C (nos dois lados)
    for sx in (-1, 1):
        x0 = sx * (PW / 2 - ABA_SOBREPOE - 1)
        s -= box(min(x0, sx * (W / 2 + 1)), max(x0, sx * (W / 2 + 1)),
                 Y_CENTRO_TAB - RANHURA_USB_H / 2, Y_CENTRO_TAB + RANHURA_USB_H / 2,
                 COSTAS, BIG)

    # Janelas nas costas (poupa material e tempo)
    for sx in (-1, 1):
        x0, x1, y0, y1 = 22, PW / 2 - 18, 26, ALTURA - 26
        jan = Manifold.extrude(rrect(x1 - x0, y1 - y0, 10), COSTAS + 2)
        s -= jan.translate((sx * (x0 + x1) / 2, (y0 + y1) / 2, -1))

    # Furos escareados (cabeça do lado do tablet, fica rasa)
    for sx in (-1, 1):
        for fy in (14, ALTURA - 14):
            p = (sx * (PW / 2 - 12), fy)
            s -= Manifold.cylinder(BIG, FURO_D / 2, circular_segments=48).translate((*p, -1))
            cone = Manifold.cylinder(FURO_CAB_D / 2 - FURO_D / 2 + 0.01, FURO_D / 2,
                                     FURO_CAB_D / 2, circular_segments=48)
            s -= cone.translate((*p, COSTAS - (FURO_CAB_D / 2 - FURO_D / 2)))
            s -= Manifold.cylinder(5, FURO_CAB_D / 2, circular_segments=48).translate(
                (*p, COSTAS - 0.001))
    return s


def cauda(folga=0.0):
    """Caudas de andorinha na junta central (x=0), espessura das costas."""
    pescoco, ponta, prof = 10.0, 16.0, 10.0
    out = Manifold()
    for cy in (22.0, ALTURA / 2, ALTURA - 22.0):
        pts = [(-1.0, cy - pescoco / 2 - folga), (prof + folga, cy - ponta / 2 - folga),
               (prof + folga, cy + ponta / 2 + folga), (-1.0, cy + pescoco / 2 + folga)]
        out += Manifold.extrude(CrossSection([pts]), COSTAS + (0.2 if folga else 0)).translate(
            (0, 0, -0.1 if folga else 0))
    return out


def pecas():
    s = corpo()
    esq = (s ^ box(-BIG, 0, -BIG, BIG, -BIG, BIG)) + (cauda() ^ box(-1, BIG, -BIG, BIG, -BIG, BIG))
    dir_ = (s ^ box(0, BIG, -BIG, BIG, -BIG, BIG)) - cauda(FOLGA_ENCAIXE)
    return esq, dir_


# --- 3MF ----------------------------------------------------------------------
def mesh_xml(man, oid, nome):
    mesh = man.to_mesh()
    v = np.asarray(mesh.vert_properties)[:, :3]
    t = np.asarray(mesh.tri_verts)
    vs = "".join(f'<vertex x="{a:.4f}" y="{b:.4f}" z="{c:.4f}"/>' for a, b, c in v)
    ts = "".join(f'<triangle v1="{a}" v2="{b}" v3="{c}"/>' for a, b, c in t)
    return (f'<object id="{oid}" name="{nome}" type="model"><mesh><vertices>{vs}</vertices>'
            f'<triangles>{ts}</triangles></mesh></object>')


def escrever_3mf(caminho, objetos):
    """objetos: lista de (nome, manifold, (dx, dy)) — já pousados em z=0."""
    res, itens = [], []
    for i, (nome, man, (dx, dy)) in enumerate(objetos, start=1):
        res.append(mesh_xml(man, i, nome))
        itens.append(f'<item objectid="{i}" transform="1 0 0 0 1 0 0 0 1 {dx:.3f} {dy:.3f} 0"/>')
    modelo = ('<?xml version="1.0" encoding="UTF-8"?>'
              '<model unit="millimeter" xml:lang="pt-PT" '
              'xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">'
              '<metadata name="Title">Suporte Redmi Pad 2 - quadro electrico</metadata>'
              f'<resources>{"".join(res)}</resources><build>{"".join(itens)}</build></model>')
    tipos = ('<?xml version="1.0" encoding="UTF-8"?>'
             '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
             '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
             '<Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>'
             '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Target="/3D/3dmodel.model" Id="rel0" '
            'Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/></Relationships>')
    with zipfile.ZipFile(caminho, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", tipos)
        z.writestr("_rels/.rels", rels)
        z.writestr("3D/3dmodel.model", modelo)


def pousar(man):
    """Move a peça para que o canto mínimo fique em (0,0,0)."""
    b = man.bounding_box()
    return man.translate((-b[0], -b[1], -b[2]))


if __name__ == "__main__":
    aqui = os.path.dirname(os.path.abspath(__file__))
    esq, dir_ = pecas()
    for n, p in (("esquerda", esq), ("direita", dir_)):
        assert p.status().name == "NoError", p.status()
        print(n, "vol %.0f cm3" % (p.volume() / 1000), "bbox", np.round(p.bounding_box(), 1))
    e, d = pousar(esq), pousar(dir_)
    we = e.bounding_box()[3]
    escrever_3mf(os.path.join(aqui, "suporte_redmi_pad2_esquerda.3mf"), [("Esquerda", e, (10, 60))])
    escrever_3mf(os.path.join(aqui, "suporte_redmi_pad2_direita.3mf"), [("Direita", d, (10, 60))])
    escrever_3mf(os.path.join(aqui, "suporte_redmi_pad2_2pecas.3mf"),
                 [("Esquerda", e, (6, 60)), ("Direita", d, (6 + we + 6, 60))])
    print("W=%.1f H=%.1f D=%.1f" % (W, ALTURA, D))
