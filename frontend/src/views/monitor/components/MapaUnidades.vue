<!--
  Mapa das unidades (Leaflet + OpenStreetMap), cada uma colorida pelo status.
  Clicar numa unidade emite "selecionar" (a página aplica o filtro de unidade).
  Para rede sem internet, troque a URL dos tiles por um servidor de mapas interno (prop "tiles").
-->
<template>
  <div ref="el" class="mapa"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { MonitorUnit } from '@/api/monitor'

const props = withDefaults(defineProps<{ unidades: MonitorUnit[]; selecionada?: number; tiles?: string; interativo?: boolean }>(), {
  tiles: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  interativo: true
})
const emit = defineEmits<{ (e: 'selecionar', id: number | undefined): void }>()

const CORES = { ok: '#16a34a', atencao: '#d97706', critico: '#dc2626' } as const
const LINK = { online: 'Online', degradado: 'Lento', offline: 'Fora do ar' } as const
const el = ref<HTMLElement>()
const mapa = shallowRef<L.Map>()
const marcadores = new Map<number, L.CircleMarker>()

function popup(u: MonitorUnit) {
  const k = u.kpi
  if (!k) return `<b>${u.name}</b><br>${u.city || ''}`
  return `<b>${u.name}</b><br><small>${u.city || ''}</small><br>` +
    `Link: ${LINK[k.link]}${k.link !== 'offline' ? ` · ${k.latenciaMs} ms` : ''}<br>` +
    `Servidores: ${k.hostsOnline}/${k.hostsTotal} · ${k.temperaturaC.toFixed(1)} °C<br>` +
    `Chamados: ${k.chamadosAbertos} (${k.slaVencido} SLA vencido) · Incidentes: ${k.incidentesSeg}`
}

function desenhar() {
  if (!mapa.value) return
  const muitos = props.unidades.length > 40
  props.unidades.forEach((u) => {
    const cor = CORES[u.kpi?.status || 'ok']
    let m = marcadores.get(u.id)
    if (!m) {
      m = L.circleMarker([Number(u.latitude), Number(u.longitude)], { radius: muitos ? 6 : 10, weight: 2, color: '#fff', fillOpacity: 0.95 })
        .addTo(mapa.value!)
      if (props.interativo) m.on('click', () => emit('selecionar', props.selecionada === u.id ? undefined : u.id))
      marcadores.set(u.id, m)
    }
    const apagado = props.selecionada && props.selecionada !== u.id
    m.setStyle({ fillColor: cor, fillOpacity: apagado ? 0.35 : 0.95, radius: (muitos ? 6 : 10) + (u.kpi?.status === 'critico' ? 3 : 0) })
    m.bindPopup(popup(u))
  })
}

function enquadrar() {
  if (!mapa.value || !props.unidades.length) return
  const sel = props.unidades.find((u) => u.id === props.selecionada)
  if (sel) mapa.value.flyTo([Number(sel.latitude), Number(sel.longitude)], 10, { duration: 0.6 })
  else mapa.value.fitBounds(L.latLngBounds(props.unidades.map((u) => [Number(u.latitude), Number(u.longitude)])), { padding: [30, 30] })
}

onMounted(() => {
  mapa.value = L.map(el.value!, { zoomControl: props.interativo, attributionControl: true }).setView([-15.8, -47.9], 4)
  L.tileLayer(props.tiles, { maxZoom: 18, attribution: '&copy; OpenStreetMap' }).addTo(mapa.value)
  desenhar()
  enquadrar()
})
watch(() => props.unidades.map((u) => u.kpi?.status).join(), desenhar)
watch(() => props.unidades.length, () => { desenhar(); enquadrar() })
watch(() => props.selecionada, () => { desenhar(); enquadrar() })
onBeforeUnmount(() => mapa.value?.remove())
</script>

<style scoped>
.mapa {
  width: 100%;
  height: 100%;
  min-height: 320px;
  border-radius: 8px;
}
</style>
