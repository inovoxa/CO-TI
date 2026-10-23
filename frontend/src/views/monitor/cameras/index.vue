<!-- Rota: /operacoes/cameras · Permissão: system:monitor-cameras:query -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Câmeras e acesso" fonte="Frigate (detecção local) + controle de acesso + cadastro da recepção" />

    <div class="grid-kpi">
      <Kpi label="Câmeras" :valor="`${online}/${d.cameras.length}`" :apoio="d.cameras.length - online ? `${d.cameras.length - online} gravando só na unidade (link fora)` : 'IA na borda de cada unidade'" :nivel="online < d.cameras.length ? 'warn' : ''" />
      <Kpi label="Veículos hoje" :valor="int(d.veiculosEntraram)" :apoio="`${int(d.veiculosSairam)} saíram`" />
      <Kpi label="No estacionamento" :valor="`${int(d.noEstacionamento)}/${int(d.vagas)}`" :apoio="`${d.vagas ? Math.round((d.noEstacionamento / d.vagas) * 100) : 0}% das vagas`" :nivel="d.vagas && d.noEstacionamento / d.vagas > 0.9 ? 'warn' : ''" />
      <Kpi label="Funcionários no prédio" :valor="int(d.funcionarios)" apoio="entradas por crachá" />
      <Kpi label="Visitantes no prédio" :valor="int(d.visitantes)" apoio="registrados na recepção" />
      <Kpi label="Alertas de acesso hoje" :valor="d.alertas" apoio="carona na catraca, placa sem cadastro" :nivel="d.alertas ? 'warn' : ''" />
    </div>

    <div class="cols">
      <el-card shadow="never">
        <template #header><b>{{ store.unidade ? store.unidade.name : 'Todas as unidades' }}</b> <small class="sub">· {{ d.cameras.length }} câmeras{{ d.cameras.length > MAX_CAMS ? `, mostrando ${MAX_CAMS}` : '' }}</small></template>
        <div class="grid-cam">
          <figure v-for="cam in d.cameras.slice(0, MAX_CAMS)" :key="cam.id" class="cam" :class="{ off: !cam.online }">
            <img v-if="cam.snapshotUrl && cam.online" :src="`${cam.snapshotUrl}?t=${tick}`" :alt="`Câmera ${cam.nome} · ${cam.unidade}`" loading="lazy" />
            <div v-else class="ph">{{ cam.online ? 'Sem imagem no modo simulado' : 'Link fora · gravando na unidade' }}</div>
            <figcaption><span class="dot" :class="cam.online ? 'on' : 'offd'"></span>{{ cam.nome }} · {{ cam.unidade }}</figcaption>
          </figure>
        </div>
      </el-card>

      <div class="side">
        <el-card shadow="never" :body-style="{ padding: '0' }">
          <template #header><b>Eventos de acesso</b></template>
          <ul class="ev">
            <li v-for="(e, i) in d.eventos" :key="e.ts + '-' + i" :class="{ alerta: e.alerta }">
              <time>{{ hora(e.ts) }}</time>
              <div><b>{{ e.camera }}</b> · {{ store.nomeUnidade(e.unidadeId) }}<br />{{ e.texto }}</div>
            </li>
            <li v-if="!d.eventos.length" class="sub">Nenhum evento ainda.</li>
          </ul>
        </el-card>
        <div class="lgpd">
          <b>Como a identificação funciona.</b> A IA conta veículos e pessoas sem identificar ninguém. Funcionário ou visitante vem do
          <b>crachá</b>, do <b>cadastro da recepção</b> e da <b>placa</b> vinculada ao veículo do servidor. Reconhecimento facial fica
          desligado: é dado biométrico sensível pela LGPD e só deve ser ativado com base legal definida e RIPD aprovado pelo encarregado.
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import * as Api from '@/api/monitor'
import type { CameraResumo } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import { debounce, hora, int } from '../components/util'

defineOptions({ name: 'MonitorCameras' })

const MAX_CAMS = 24 // mais que isso numa tela só pesa no navegador e na rede; filtre por unidade
const store = useMonitorStore()
const d = ref<CameraResumo>({ veiculosEntraram: 0, veiculosSairam: 0, noEstacionamento: 0, vagas: 0, funcionarios: 0, visitantes: 0, alertas: 0, cameras: [], eventos: [] })
const online = computed(() => d.value.cameras.filter((c) => c.online).length)

async function carregar() {
  d.value = await Api.resumoCameras({ unidadeId: store.unidadeId })
}
watch(() => store.unidadeId, carregar, { immediate: true })
const recarregar = debounce(carregar, 1000)
useMonitorSocket().on('monitor-camera', (l: Array<{ unidades: number[] }>) => {
  if (!store.unidadeId || l.some((x) => x.unidades.includes(store.unidadeId!))) recarregar()
})

// imagens: o Frigate devolve a foto mais recente de cada câmera; renova a cada 2 s
const tick = ref(0)
const timer = setInterval(() => tick.value++, 2000)
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.cols { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 14px; align-items: start; }
.side { display: flex; flex-direction: column; gap: 14px; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.grid-cam { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 10px; }
.cam {
  margin: 0; overflow: hidden; border-radius: 8px; background: #05080f;
  img, .ph { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; }
  .ph { display: grid; place-items: center; color: #8b98b3; font-size: 12px; }
  figcaption { display: flex; align-items: center; gap: 6px; padding: 6px 8px; font-size: 12px; color: #e6ecf7; background: rgba(0, 0, 0, 0.6); }
  &.off .ph { color: #f59e0b; }
}
.dot { width: 8px; height: 8px; border-radius: 50%; &.on { background: #22c55e; } &.offd { background: #f59e0b; } }
.ev {
  max-height: 420px; margin: 0; padding: 0; overflow: auto; list-style: none;
  li { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: 8px; padding: 7px 12px; border-top: 1px solid var(--el-border-color-lighter); font-size: 13px; }
  li.alerta { background: var(--el-color-danger-light-9); }
  time { font-size: 12px; color: var(--el-text-color-secondary); font-variant-numeric: tabular-nums; }
  b { font-weight: 500; }
}
.lgpd { padding: 10px 12px; border: 1px dashed var(--el-border-color); border-radius: 8px; font-size: 12.5px; line-height: 1.55; color: var(--el-text-color-secondary); b { color: var(--el-text-color-primary); } }
@media (max-width: 1280px) { .grid-kpi { grid-template-columns: repeat(3, minmax(0, 1fr)); } .cols { grid-template-columns: minmax(0, 1fr); } }
</style>
