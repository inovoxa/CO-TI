<!-- Mapa com um ponto arrastável: clique ou arraste para ajustar a localização da unidade -->
<template>
  <div ref="el" class="mapa"></div>
</template>

<script lang="ts" setup>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = withDefaults(
  defineProps<{ latitude?: number; longitude?: number; tiles?: string }>(),
  {
    tiles: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
  }
)
const emit = defineEmits<{ mover: [lat: number, lng: number] }>()
const el = ref<HTMLDivElement>()
const mapa = shallowRef<L.Map>()
let ponto: L.CircleMarker | L.Marker | undefined

const icone = L.divIcon({ className: 'ponto-unidade', iconSize: [18, 18], iconAnchor: [9, 9] })

const posicionar = (centralizar: boolean) => {
  if (!mapa.value || props.latitude == null || props.longitude == null) return
  const ll = L.latLng(Number(props.latitude), Number(props.longitude))
  if (!ponto) {
    ponto = L.marker(ll, { draggable: true, icon: icone }).addTo(mapa.value)
    ponto.on('dragend', () => {
      const p = (ponto as L.Marker).getLatLng()
      emit('mover', round(p.lat), round(p.lng))
    })
  } else {
    ponto.setLatLng(ll)
  }
  if (centralizar) mapa.value.setView(ll, Math.max(mapa.value.getZoom(), 16))
}
const round = (v: number) => Math.round(v * 1e7) / 1e7

onMounted(() => {
  mapa.value = L.map(el.value!, { attributionControl: true }).setView([-15.8, -47.9], 4)
  L.tileLayer(props.tiles, { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(mapa.value)
  mapa.value.on('click', (e: L.LeafletMouseEvent) =>
    emit('mover', round(e.latlng.lat), round(e.latlng.lng))
  )
  posicionar(true)
  // o diálogo abre com animação: recalcula o tamanho depois
  setTimeout(() => mapa.value?.invalidateSize(), 300)
})
watch(
  () => [props.latitude, props.longitude],
  () => posicionar(false)
)
const centralizar = () => posicionar(true)
defineExpose({ centralizar })
onBeforeUnmount(() => mapa.value?.remove())
</script>

<style lang="scss" scoped>
.mapa {
  width: 100%;
  height: 260px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

:deep(.ponto-unidade) {
  background: var(--el-color-primary);
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgb(0 0 0 / 30%);
}
</style>
