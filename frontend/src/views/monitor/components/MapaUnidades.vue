<!--
  Mapa das unidades (Leaflet + OpenStreetMap), cada uma colorida pelo status; as com problema pulsam.
  Clicar numa unidade abre o painel da rede dela (link, faixa, disponibilidade, equipamentos, chamados);
  "Filtrar por esta unidade" emite "selecionar" (a página aplica o filtro de unidade).
  Para rede sem internet, troque a URL dos tiles por um servidor de mapas interno (prop "tiles").
-->
<template>
  <div class="mapa-raiz">
    <div ref="el" class="mapa"></div>
    <!-- o Leaflet leva este bloco para dentro do popup; o Vue continua atualizando -->
    <div class="hidden">
      <div ref="painelEl" class="painel">
        <template v-if="aberta">
          <div class="painel__topo">
            <b>{{ aberta.name }}</b>
            <span :class="`selo selo--${aberta.kpi?.status || 'ok'}`">{{ rotuloLink }}</span>
          </div>
          <div class="painel__end">{{ aberta.endereco || aberta.city || 'Sem endereço' }}</div>

          <div v-if="carregando" class="painel__carregando">Carregando a rede…</div>
          <template v-else>
            <dl v-if="principal" class="painel__rede">
              <template v-if="faixa">
                <dt>Faixa</dt>
                <dd class="mono">{{ faixa }}</dd>
              </template>
              <dt>Gateway</dt>
              <dd class="mono">{{ principal.alvo }}</dd>
              <dt>Resposta</dt>
              <dd>
                <template v-if="principal.estado === 'down'">sem resposta</template>
                <template v-else>{{ principal.latenciaMs ?? '—' }} ms</template>
                <span v-if="principal.desde" class="painel__desde">
                  · {{ principal.estado === 'down' ? 'fora' : 'no ar' }} há {{ ha(principal.desde) }}</span
                >
              </dd>
              <dt>Disponível</dt>
              <dd>{{ pct(principal.uptime24h) }} em 24 h · {{ pct(principal.uptime30d) }} em 30 dias</dd>
              <template v-if="principal.estado === 'down' && principal.mensagem">
                <dt>Erro</dt>
                <dd class="painel__erro">{{ principal.mensagem }}</dd>
              </template>
            </dl>
            <div v-else class="painel__carregando">Nenhum monitor de link nesta unidade.</div>

            <div v-if="principal?.historico.length" class="painel__barras" title="Últimas verificações">
              <i v-for="(b, i) in principal.historico" :key="i" :class="`b-${b}`"></i>
            </div>

            <div class="painel__numeros">
              <div>
                <b>{{ equipamentos ?? '—' }}</b>
                <span>equipamentos</span>
              </div>
              <div>
                <b>{{ aberta.kpi?.chamadosAbertos ?? 0 }}</b>
                <span>chamados abertos</span>
              </div>
              <div :class="{ alerta: aberta.kpi?.slaVencido }">
                <b>{{ aberta.kpi?.slaVencido ?? 0 }}</b>
                <span>SLA vencido</span>
              </div>
              <div v-if="outros.length">
                <b>{{ outros.filter((l) => l.estado !== 'down').length }}/{{ outros.length }}</b>
                <span>outros serviços</span>
              </div>
            </div>
          </template>

          <div v-if="interativo" class="painel__acoes">
            <button type="button" class="primario" @click="filtrar">
              {{ selecionada === aberta.id ? 'Tirar o filtro' : 'Filtrar por esta unidade' }}
            </button>
            <button v-if="principal && rotaMonitor" type="button" @click="abrirMonitor">
              Abrir monitor
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { listarEquipamentos, listarLinks, type Link, type MonitorUnit } from '@/api/monitor'

const props = withDefaults(
  defineProps<{
    unidades: MonitorUnit[]
    selecionada?: number
    tiles?: string
    interativo?: boolean
  }>(),
  {
    tiles: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    interativo: true
  }
)
const emit = defineEmits<{ (e: 'selecionar', id: number | undefined): void }>()

const CORES = { ok: '#16a34a', atencao: '#d97706', critico: '#dc2626' } as const
const LINK = { online: 'No ar', degradado: 'Lento', offline: 'Fora do ar' } as const
const router = useRouter()
const el = ref<HTMLElement>()
const painelEl = ref<HTMLElement>()
const mapa = shallowRef<L.Map>()
const marcadores = new Map<number, L.CircleMarker>()
const pulsos = new Map<number, L.Marker>()

// ---------- painel da unidade
const abertaId = ref<number>()
const aberta = computed(() => props.unidades.find((u) => u.id === abertaId.value))
const links = ref<Link[]>([])
const equipamentos = ref<number>()
const carregando = ref(false)
const principal = computed(
  () => links.value.find((l) => l.papel === 'principal') ?? links.value.find((l) => l.tipo === 'ping')
)
const outros = computed(() => links.value.filter((l) => l !== principal.value))
const faixa = computed(() => principal.value?.descricao?.match(/Faixa\s+(\S+)/)?.[1])
const rotuloLink = computed(() => {
  const k = aberta.value?.kpi
  if (!k) return 'Sem leitura'
  return k.status === 'critico' && k.link !== 'offline' ? 'Com problema' : LINK[k.link]
})
const rotaMonitor = computed(() => router.hasRoute('MonitoramentoMonitor'))

const pct = (v?: number) => (v == null ? '—' : `${v.toFixed(v >= 99.95 || v === 0 ? 0 : 2)}%`)
const ha = (desde: number) => {
  const min = Math.max(0, Math.round((Date.now() - desde) / 60000))
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  return h < 48 ? `${h} h ${min % 60} min` : `${Math.floor(h / 24)} dias`
}

let pedido = 0
async function carregarPainel(id: number) {
  const meu = ++pedido
  carregando.value = true
  links.value = []
  equipamentos.value = undefined
  try {
    const [l, e] = await Promise.allSettled([
      listarLinks({ unidadeId: id }),
      listarEquipamentos({ unidadeId: id })
    ])
    if (meu !== pedido) return
    if (l.status === 'fulfilled') links.value = l.value.lista
    if (e.status === 'fulfilled') equipamentos.value = e.value.total
  } finally {
    if (meu === pedido) {
      carregando.value = false
      // o conteúdo cresceu: o Leaflet reposiciona o popup para caber no mapa
      setTimeout(() => mapa.value?.eachLayer((c) => c instanceof L.Popup && c.update()))
    }
  }
}

function abrir(u: MonitorUnit) {
  abertaId.value = u.id
  carregarPainel(u.id)
  L.popup({ maxWidth: 340, minWidth: 300, className: 'popup-unidade', autoPanPadding: [20, 20] })
    .setLatLng([Number(u.latitude), Number(u.longitude)])
    .setContent(painelEl.value!)
    .openOn(mapa.value!)
}

function filtrar() {
  const id = aberta.value?.id
  emit('selecionar', props.selecionada === id ? undefined : id)
  mapa.value?.closePopup()
}

function abrirMonitor() {
  if (principal.value)
    router.push({ name: 'MonitoramentoMonitor', query: { id: String(principal.value.id) } })
}

// ---------- marcadores
function desenhar() {
  if (!mapa.value) return
  const muitos = props.unidades.length > 40
  const raio = muitos ? 6 : 10
  const vistos = new Set<number>()
  props.unidades.forEach((u) => {
    vistos.add(u.id)
    const status = u.kpi?.status || 'ok'
    const pos: L.LatLngExpression = [Number(u.latitude), Number(u.longitude)]
    let m = marcadores.get(u.id)
    if (!m) {
      m = L.circleMarker(pos, { radius: raio, weight: 2, color: '#fff', fillOpacity: 0.95 }).addTo(
        mapa.value!
      )
      if (props.interativo) m.on('click', () => abrir(u))
      else m.bindTooltip(u.name)
      marcadores.set(u.id, m)
    }
    const apagado = props.selecionada && props.selecionada !== u.id
    m.setStyle({
      fillColor: CORES[status],
      fillOpacity: apagado ? 0.35 : 0.95,
      radius: raio + (status === 'critico' ? 3 : 0)
    })
    if (props.interativo) m.bindTooltip(u.name, { direction: 'top', offset: [0, -6] })
    if (status === 'critico') m.bringToFront()

    // pulso nos pontos de atenção (fora do ar pulsa forte; atenção, devagar)
    const pulso = pulsos.get(u.id)
    if (status === 'ok' || apagado) {
      pulso?.remove()
      pulsos.delete(u.id)
    } else if (!pulso || !pulso.getElement()?.classList.contains(`pulso--${status}`)) {
      pulso?.remove()
      const tam = (raio + 3) * 2
      pulsos.set(
        u.id,
        L.marker(pos, {
          interactive: false,
          keyboard: false,
          zIndexOffset: -1000,
          icon: L.divIcon({
            className: `pulso pulso--${status}`,
            iconSize: [tam, tam],
            html: '<i></i><i></i>'
          })
        }).addTo(mapa.value!)
      )
    }
  })
  // unidade que saiu da lista
  marcadores.forEach((m, id) => {
    if (vistos.has(id)) return
    m.remove()
    marcadores.delete(id)
    pulsos.get(id)?.remove()
    pulsos.delete(id)
  })
}

function enquadrar() {
  if (!mapa.value || !props.unidades.length) return
  const sel = props.unidades.find((u) => u.id === props.selecionada)
  if (sel)
    mapa.value.flyTo(
      [Number(sel.latitude), Number(sel.longitude)],
      Math.max(mapa.value.getZoom(), 15),
      { duration: 0.6 }
    )
  else
    mapa.value.fitBounds(
      L.latLngBounds(props.unidades.map((u) => [Number(u.latitude), Number(u.longitude)])),
      { padding: [30, 30] }
    )
}

onMounted(() => {
  mapa.value = L.map(el.value!, {
    zoomControl: props.interativo,
    attributionControl: true
  }).setView([-15.8, -47.9], 4)
  L.tileLayer(props.tiles, { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(mapa.value)
  mapa.value.on('popupclose', () => (abertaId.value = undefined))
  desenhar()
  enquadrar()
})
watch(() => props.unidades.map((u) => u.kpi?.status).join(), desenhar)
watch(
  () => props.unidades.length,
  () => {
    desenhar()
    enquadrar()
  }
)
watch(
  () => props.selecionada,
  () => {
    desenhar()
    enquadrar()
  }
)
// estado mudou com o painel aberto (WebSocket): recarrega a rede da unidade
watch(
  () => aberta.value?.kpi?.status,
  (s, antes) => {
    if (aberta.value && antes && s !== antes) carregarPainel(aberta.value.id)
  }
)
onBeforeUnmount(() => mapa.value?.remove())
</script>

<style scoped lang="scss">
.mapa-raiz {
  width: 100%;
  height: 100%;
}

.mapa {
  width: 100%;
  height: 100%;
  min-height: 320px;
  border-radius: 8px;
}

.painel {
  font-size: 12.5px;
  line-height: 1.45;
  color: #1f2937;
}

.painel__topo {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;

  b {
    font-size: 14px;
    line-height: 1.3;
  }
}

.selo {
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  border-radius: 999px;

  &--ok {
    background: #16a34a;
  }

  &--atencao {
    background: #d97706;
  }

  &--critico {
    background: #dc2626;
  }
}

.painel__end {
  margin-top: 2px;
  color: #6b7280;
}

.painel__carregando {
  margin: 12px 0 4px;
  color: #6b7280;
}

.painel__rede {
  display: grid;
  margin: 10px 0 0;
  grid-template-columns: 74px 1fr;
  gap: 3px 8px;

  dt {
    color: #6b7280;
  }

  dd {
    margin: 0;
  }
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.painel__desde {
  color: #6b7280;
}

.painel__erro {
  color: #b91c1c;
}

.painel__barras {
  display: flex;
  height: 18px;
  margin-top: 10px;
  gap: 2px;

  i {
    flex: 1;
    background: #16a34a;
    border-radius: 2px;
  }

  .b-p {
    background: #d97706;
  }

  .b-d {
    background: #dc2626;
  }
}

.painel__numeros {
  display: grid;
  margin-top: 10px;
  grid-template-columns: repeat(auto-fit, minmax(60px, 1fr));
  gap: 6px;

  div {
    padding: 6px;
    text-align: center;
    background: #f3f4f6;
    border-radius: 6px;
  }

  b {
    display: block;
    font-size: 16px;
    font-variant-numeric: tabular-nums;
  }

  span {
    font-size: 11px;
    color: #6b7280;
  }

  .alerta b {
    color: #b91c1c;
  }
}

.painel__acoes {
  display: flex;
  margin-top: 12px;
  gap: 6px;

  button {
    padding: 5px 10px;
    font-size: 12px;
    color: #1f2937;
    cursor: pointer;
    background: #fff;
    border: 1px solid #d1d5db;
    border-radius: 6px;

    &:hover {
      border-color: #9ca3af;
    }
  }

  .primario {
    color: #fff;
    background: #2563eb;
    border-color: #2563eb;

    &:hover {
      background: #1d4ed8;
    }
  }
}
</style>

<style>
/* fora do escopo: o Leaflet cria estes elementos */
.pulso {
  pointer-events: none;
}

.pulso i {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  opacity: 0;
  animation: pulso-onda 1.8s ease-out infinite;
}

.pulso i + i {
  animation-delay: 0.9s;
}

.pulso--critico i {
  border: 2px solid rgb(220 38 38 / 85%);
}

.pulso--atencao i {
  border: 2px solid rgb(217 119 6 / 75%);
  animation-duration: 2.8s;
}

.pulso--atencao i + i {
  animation-delay: 1.4s;
}

@keyframes pulso-onda {
  0% {
    opacity: 0.9;
    transform: scale(0.6);
  }

  100% {
    opacity: 0;
    transform: scale(2.4);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pulso i {
    opacity: 0.5;
    animation: none;
    transform: scale(1.8);
  }
}

.popup-unidade .leaflet-popup-content {
  margin: 12px 14px;
}
</style>
