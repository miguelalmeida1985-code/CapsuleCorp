# Suporte Redmi Pad 2 (11") para a tampa do quadro eléctrico

Suporte tipo *sleeve*: o tablet (na horizontal) entra por cima e desliza até à calha inferior.

| Ficheiro | Conteúdo |
|---|---|
| `suporte_redmi_pad2_2pecas.3mf` | as duas peças na mesma mesa (ocupa ~287 x 162 mm) |
| `suporte_redmi_pad2_esquerda.3mf` / `_direita.3mf` | cada peça em separado |
| `gerar_suporte.py` | gerador paramétrico (`pip install manifold3d numpy`) |

**Medidas finais:** 264.6 x 162 x 13.6 mm, centrado na tampa de 380 x 335 mm
(sobram ~58 mm de cada lado, por isso a fechadura da tampa fica acessível).

## Detalhes
- Bolso para 254.6 x 166.0 x 7.4 mm, com 1 mm de folga de cada lado e na espessura.
  Se usar capa, ajuste `FOLGA_XY` / `FOLGA_Z` no script.
- Aba frontal de 5 mm sobre a moldura do ecrã (laterais e fundo), com chanfros.
- Ranhura de 30 mm nos **dois** lados para o USB-C (o tablet pode entrar em qualquer sentido) e para o som das colunas.
- Entalhe para o polegar ao centro em baixo: ajuda a empurrar o tablet para cima ao tirá-lo.
- As duas metades encaixam com 3 caudas de andorinha nas costas (folga de 0.15 mm). Cole-as com cola de cianoacrilato ou epóxi.
- 4 furos escareados para M3/M4 (ou parafusos autoperfurantes na tampa) e janelas nas costas para poupar material.

## Impressão
- PETG (ou PLA), camada de 0.2 mm, 3 perímetros, enchimento de 20 %.
- Imprima com as costas na mesa, sem suportes. A aba de 5 mm fica em balanço e imprime bem.

## Montagem
1. Cole as duas metades pelas caudas de andorinha.
2. Fixe à tampa com fita dupla face VHB (3M) e/ou com os 4 parafusos. Se fizer furos na tampa, tenha cuidado com o que está por trás.
3. Introduza o tablet por cima e ligue o cabo pela ranhura lateral (um cabo em L fica mais limpo).
