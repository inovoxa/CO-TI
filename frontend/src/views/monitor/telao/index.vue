<!--
  Rota: /telao (FORA do layout: sem menu, sem abas) · Permissão: system:monitor-screen:query
  Para a TV da sala de operações. Gira entre 4 quadros a cada 30 s; clique em "Tela cheia" uma vez.
  Parâmetros opcionais na URL: ?unidade=7 (só uma unidade) · ?intervalo=20 (segundos por quadro)
  Visual escuro fixo de propósito: é feito para TV, longe e de dia e de noite.
-->
<template>
  <div class="tv" @mousemove="mostrarControles">
    <header class="tv__top">
      <div>
        <h1>Centro de Operações de TI</h1>
        <p>{{ store.unidade ? store.unidade.name : `${store.unidades.length} unidades` }} · {{ QUADROS[atual].titulo }}</p>
      </div>
      <div class="tv__status">
        <span class="chip ok">{{ store.contagemStatus.ok }} normais</span>
        <span class="chip warn">{{ store.contagemStatus.atencao }} atenção</span>
        <span class="chip crit">{{ store.contagemStatus.critico }} críticas</span>
        <span class="tv__clock">{{ relogio }}</span>
        <span class="tv__live" :class="status === 'OPEN' ? 'on' : 'off'"><i></i>{{ status === 'OPEN' ? 'ao vivo' : 'reconectando' }}</span>
      </div>
    </header>

    <main class="tv__body">
      <!-- 1. mapa e situação -->
      <section v-if="QUADROS[atual].id === 'mapa'" class="q-mapa">
        <MapaUnidades class="mapa" :unidades="unidades" :selecionada="store.unidadeId" :interativo="false" />
        <div class="lado">
          <div v-for="k in resumo" :key="k.l" class="big" :class="k.n"><span>{{ k.l }}</span><strong>{{ k.v }}</strong><em>{{ k.a }}</em></div>
        </div>
      </section>

      <!-- 2. ocorrências -->
      <section v-else-if="QUADROS[atual].id === 'ocorrencias'" class="q-lista">
        <ol>
          <li v-for="(o, i) in ocorrencias" :key="o.momento + '-' + i" :class="`n-${o.nivel}`">
            <time>{{ hora(o.momento) }}</time><b>{{ store.nomeUnidade(o.unitId) }}</b><span>{{ o.texto }}</span>
          </li>
          <li v-if="!ocorrencias.length" class="vazio">Nenhuma ocorrência de atenção nos últimos 30 minutos.</li>
        </ol>
      </section>

      <!-- 3. links com problema -->
      <section v-else class="q-lista">
        <ol>
          <li v-for="l in links" :key="l.id" :class="l.estado === 'down' ? 'n-critico' : 'n-atencao'">
            <time>{{ l.estado === 'down' ? 'FORA' : `${l.latenciaMs} ms` }}</time><b>{{ l.nome }}</b>
            <span class="beats"><i v-for="(b, j) in l.historico" :key="j" :class="b"></i></span>
          </li>
          <li v-if="!links.length" class="vazio ok">Todos os links e serviços no ar.</li>
        </ol>
      </section>
    </main>

    <footer class="tv__foot" :class="{ visivel: controlesVisiveis }">
      <div class="dots">
        <button v-for="(q, i) in QUADROS" :key="q.id" type="button" :class="{ on: i === atual }" :aria-label="q.titulo" @click="irPara(i)">{{ q.titulo }}</button>
      </div>
      <div class="acoes">
        <button type="button" @click="pausado = !pausado">{{ pausado ? 'Retomar rotação' : 'Pausar rotação' }}</button>
        <button type="button" @click="telaCheia">Tela cheia</button>
      </div>
      <div class="barra"><i :style="{ width: `${progresso}%` }"></i></div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as Api from '@/api/monitor'
import type { Link } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import MapaUnidades from '../components/MapaUnidades.vue'
import { dec, hora, int } from '../components/util'

defineOptions({ name: 'MonitorTelao' })

const QUADROS = [
  { id: 'mapa', titulo: 'Mapa e situação' },
  { id: 'ocorrencias', titulo: 'Ocorrências' },
  { id: 'links', titulo: 'Links com problema' }
] as const

const route = useRoute()
const store = useMonitorStore()
const { status } = useMonitorSocket()
const intervalo = Math.max(10, Number(route.query.intervalo) || 30) * 1000
store.setUnidade(Number(route.query.unidade) || undefined)
store.carregar()

const unidades = computed(() => store.unidades)
const base = computed(() => store.unidades.filter((u) => !store.unidadeId || u.id === store.unidadeId))
const resumo = computed(() => {
  let on = 0, off = 0, ch = 0, sla = 0, sOn = 0, sTot = 0, disp = 0, n = 0
  base.value.forEach(({ kpi: k }) => {
    if (!k) return
    n++
    if (k.link === 'online') on++
    if (k.link === 'offline') off++
    ch += k.chamadosAbertos
    sla += k.slaVencido
    sOn += k.servicosNoAr
    sTot += k.servicosTotal
    disp += k.disponibilidade
  })
  return [
    { l: 'Links online', v: `${on}/${n}`, a: off ? `${off} fora do ar` : 'nenhum fora do ar', n: off ? 'crit' : on < n ? 'warn' : 'ok' },
    { l: 'Disponibilidade 24 h', v: `${dec(n ? disp / n : 100, 2)}%`, a: 'média das unidades', n: '' },
    { l: 'Chamados abertos', v: int(ch), a: `${sla} com SLA vencido`, n: sla ? 'warn' : '' },
    { l: 'Serviços no ar', v: `${sOn}/${sTot}`, a: sTot - sOn ? `${sTot - sOn} fora do ar` : 'todos no ar', n: sOn < sTot ? 'warn' : sTot ? 'ok' : '' }
  ]
})
const ocorrencias = computed(() => {
  const limite = Date.now() - 30 * 60_000
  return store.ocorrenciasFiltradas.filter((o) => o.nivel !== 'info' && o.momento > limite).slice(0, 12)
})

// dados dos quadros que vêm de outros módulos (se o perfil do telão não tiver a permissão, o quadro fica vazio)
const links = ref<Link[]>([])
async function atualizarQuadro() {
  const id = QUADROS[atual.value].id
  try {
    if (id === 'links') links.value = (await Api.listarLinks({ unidadeId: store.unidadeId, filtro: 'problema' })).lista.slice(0, 12)
  } catch (e) {
    console.warn('[telão] quadro sem dados', id, e)
  }
}

// rotação
const atual = ref(0)
const pausado = ref(false)
const inicio = ref(Date.now())
const agora = ref(Date.now())
const progresso = computed(() => (pausado.value ? 0 : Math.min(100, ((agora.value - inicio.value) / intervalo) * 100)))
const relogio = computed(() => new Date(agora.value).toLocaleString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }))
function irPara(i: number) {
  atual.value = (i + QUADROS.length) % QUADROS.length
  inicio.value = Date.now()
}
watch(atual, atualizarQuadro)
const t1 = setInterval(() => {
  agora.value = Date.now()
  if (!pausado.value && agora.value - inicio.value >= intervalo) irPara(atual.value + 1)
}, 500)
const t2 = setInterval(atualizarQuadro, 15000)

// controles aparecem quando o mouse se mexe e somem depois de 4 s
const controlesVisiveis = ref(true)
let tCtl: ReturnType<typeof setTimeout> | undefined
function mostrarControles() {
  controlesVisiveis.value = true
  clearTimeout(tCtl)
  tCtl = setTimeout(() => (controlesVisiveis.value = false), 4000)
}

// manter a TV acordada (quando o navegador permitir) e tela cheia sob comando
let wakeLock: any = null
async function manterAcordado() {
  try {
    wakeLock = await (navigator as any).wakeLock?.request('screen')
  } catch {
    /* navegador não permite: tudo bem */
  }
}
function telaCheia() {
  document.documentElement.requestFullscreen?.().catch(() => undefined)
  manterAcordado()
}
onMounted(() => {
  mostrarControles()
  manterAcordado()
  atualizarQuadro()
})
onBeforeUnmount(() => {
  clearInterval(t1)
  clearInterval(t2)
  clearTimeout(tCtl)
  wakeLock?.release?.()
})
</script>

<style scoped lang="scss">
.tv {
  --bg: #070c16; --panel: #0f1829; --line: #1f2b44; --fg: #e6ecf7; --muted: #8b98b3;
  --ok: #22c55e; --warn: #f59e0b; --crit: #f87171;
  position: fixed; inset: 0; display: flex; flex-direction: column; gap: 14px; padding: 18px 24px;
  color: var(--fg); background: var(--bg); font-size: 18px; overflow: hidden;
}
.tv__top {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px;
  h1 { margin: 0; font-size: 28px; font-weight: 600; }
  p { margin: 2px 0 0; color: var(--muted); font-size: 16px; }
}
.tv__status { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.chip { padding: 4px 12px; border-radius: 20px; font-size: 15px; font-weight: 600; border: 1px solid; &.ok { color: var(--ok); } &.warn { color: var(--warn); } &.crit { color: var(--crit); } }
.tv__clock { font-variant-numeric: tabular-nums; font-size: 20px; text-transform: capitalize; }
.tv__live { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; i { width: 9px; height: 9px; border-radius: 50%; background: currentColor; } &.on { color: var(--ok); } &.off { color: var(--warn); } }
.tv__body { flex: 1; min-height: 0; }
.q-mapa { display: grid; grid-template-columns: minmax(0, 1fr) 420px; gap: 16px; height: 100%; }
.mapa { height: 100%; }
.lado { display: flex; flex-direction: column; gap: 12px; }
.big {
  padding: 14px 18px; border: 1px solid var(--line); border-top: 4px solid var(--line); border-radius: 10px; background: var(--panel);
  span { display: block; font-size: 14px; letter-spacing: 0.05em; text-transform: uppercase; color: var(--muted); }
  strong { display: block; font-size: 44px; font-weight: 600; font-variant-numeric: tabular-nums; line-height: 1.15; }
  em { display: block; font-size: 15px; font-style: normal; color: var(--muted); }
  &.ok { border-top-color: var(--ok); } &.warn { border-top-color: var(--warn); } &.crit { border-top-color: var(--crit); strong { color: var(--crit); } }
}
.q-lista ol { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 8px; }
.q-lista li {
  display: grid; grid-template-columns: 110px 340px minmax(0, 1fr); gap: 16px; align-items: center;
  padding: 12px 16px; border-left: 6px solid var(--line); border-radius: 8px; background: var(--panel); font-size: 20px;
  time { color: var(--muted); font-variant-numeric: tabular-nums; } b { font-weight: 600; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  &.n-critico { border-left-color: var(--crit); } &.n-atencao { border-left-color: var(--warn); } &.n-info { border-left-color: var(--ok); }
  &.vazio { display: block; color: var(--muted); &.ok { color: var(--ok); } }
}
.beats { display: flex; gap: 3px; i { width: 8px; height: 26px; border-radius: 2px; background: var(--line); } i.u { background: var(--ok); } i.p { background: var(--warn); } i.d { background: var(--crit); } }
.tv__foot {
  position: relative; display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; opacity: 0.25; transition: opacity 0.3s;
  &.visivel, &:hover, &:focus-within { opacity: 1; }
  button { padding: 6px 14px; border: 1px solid var(--line); border-radius: 18px; color: var(--fg); background: var(--panel); font-size: 14px; cursor: pointer; }
  button:focus-visible { outline: 2px solid #6aa3ff; outline-offset: 2px; }
  .dots, .acoes { display: flex; flex-wrap: wrap; gap: 8px; }
  .dots button.on { border-color: #6aa3ff; color: #6aa3ff; }
  .barra { position: absolute; left: 0; right: 0; bottom: -10px; height: 3px; background: var(--line); i { display: block; height: 100%; background: #6aa3ff; } }
}
@media (prefers-reduced-motion: reduce) { .tv__foot { transition: none; } }
@media (max-width: 1100px) { .q-mapa { grid-template-columns: 1fr; } .q-lista li { grid-template-columns: 80px 1fr; } }
</style>
