<!-- Fontes de logs: situação das entradas (portas), volume, quem está mandando e como configurar cada equipamento. -->
<template>
  <el-scrollbar height="calc(100vh - 88px - 40px - 50px)">
    <el-row :gutter="12">
      <el-col :span="24">
        <el-card shadow="hover">
          <el-descriptions title="Recepção" :column="4" border>
            <template #extra>
              <el-button size="small" @click="carregar"><Icon class="mr-4px" icon="ep:refresh" />Atualizar</el-button>
            </template>
            <el-descriptions-item v-for="e in situacao?.entradas ?? []" :key="e.nome + e.protocolo" :label="`${e.nome} ${e.protocolo}:`">
              <el-tag :type="e.ativa ? 'success' : 'danger'" size="small" disable-transitions>
                {{ e.ativa ? 'ouvindo' : 'parada' }}
              </el-tag>
              porta <b>{{ e.portaPublica || e.porta }}</b> · {{ Api.numero(e.recebidas) }} recebidas
              <span v-if="e.recusadas"> · {{ Api.numero(e.recusadas) }} recusadas</span>
              <div v-if="e.erro" class="text-12px text-[var(--el-color-danger)]">{{ e.erro }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="Gravadas:">{{ Api.numero(situacao?.gravadas) }}</el-descriptions-item>
            <el-descriptions-item label="Na fila:">{{ Api.numero(situacao?.fila) }}</el-descriptions-item>
            <el-descriptions-item label="Descartadas (fila cheia):">
              <span :class="{ 'text-[var(--el-color-danger)]': situacao?.descartadas }">{{ Api.numero(situacao?.descartadas) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="Ilegíveis:">{{ Api.numero(situacao?.invalidas) }}</el-descriptions-item>
            <el-descriptions-item label="Guardadas por:">{{ situacao?.retencaoDias }} dias</el-descriptions-item>
            <el-descriptions-item label="Espaço em disco:">{{ situacao?.tamanho }}</el-descriptions-item>
            <el-descriptions-item label="Últimas 24 h:">{{ Api.numero(total24h) }} mensagens</el-descriptions-item>
          </el-descriptions>
          <div class="nota">Contadores desde que o backend subiu. Com mais de uma instância, cada uma conta as suas.</div>
        </el-card>
      </el-col>

      <el-col :md="10" :span="24" class="mt-3">
        <el-card shadow="hover">
          <Echart :options="graficoFluxos" :height="360" />
        </el-card>
      </el-col>

      <el-col :md="14" :span="24" class="mt-3">
        <el-card shadow="hover">
          <template #header>Quem mandou logs nas últimas 24 h</template>
          <el-table :data="origens" size="small" height="300" v-loading="carregando">
            <el-table-column label="Origem" prop="origem" min-width="160" show-overflow-tooltip />
            <el-table-column label="Fluxo" prop="fluxo" width="100">
              <template #default="{ row }">{{ rotuloFluxo(row.fluxo) }}</template>
            </el-table-column>
            <el-table-column label="Chegou de" prop="ip" width="120" />
            <el-table-column align="right" label="Mensagens" width="110">
              <template #default="{ row }">{{ Api.numero(row.total) }}</template>
            </el-table-column>
            <el-table-column label="Última" width="150">
              <template #default="{ row }">{{ quando(row.ultima) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="24" class="mt-3">
        <el-card shadow="hover">
          <template #header>Como mandar os logs para o CO-TI</template>
          <el-tabs>
            <el-tab-pane label="FortiGate">
              <p>Na CLI do FortiGate (formato padrão, chave=valor), apontando para este servidor:</p>
              <pre>{{ fortigate }}</pre>
            </el-tab-pane>
            <el-tab-pane label="Windows (NXLog)">
              <p>
                Instale o NXLog Community nos controladores de domínio, no servidor de arquivos e nas estações e use
                a saída GELF. Ative antes a auditoria de logon, de contas e de acesso a objetos (GPO).
              </p>
              <pre>{{ nxlog }}</pre>
            </el-tab-pane>
            <el-tab-pane label="Linux (rsyslog)">
              <p>Em <code>/etc/rsyslog.d/90-coti.conf</code> (TCP, @@; para UDP use um @ só):</p>
              <pre>{{ rsyslog }}</pre>
            </el-tab-pane>
            <el-tab-pane label="Campos e fluxos">
              <ul class="lista">
                <li><b>Firewall</b>: mensagens do FortiGate, com todos os campos (hostname, srcip, action, catdesc...).</li>
                <li>
                  <b>Contas</b>: eventos do Windows de logon e de contas (4624, 4625, 4740, 4720...), com
                  <code>usuario</code>, <code>computador</code>, <code>ip_origem</code> e <code>acao</code> em português.
                </li>
                <li><b>Arquivos</b>: acesso a arquivos e compartilhamentos (4663, 5145...), com <code>objeto</code> e <code>compartilhamento</code>.</li>
                <li><b>Máquinas</b>: os demais eventos do Windows e o Syslog dos servidores.</li>
              </ul>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </el-scrollbar>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as Api from '@/api/logs'
import { quando } from '../contexto'

defineOptions({ name: 'LogsFontes' })

const situacao = ref<Api.Situacao>()
const origens = ref<Api.Origem[]>([])
const carregando = ref(false)
const servidor = window.location.hostname

const rotuloFluxo = (f: string) => Api.FLUXOS.find((x) => x.valor === f)?.rotulo ?? f
const total24h = computed(() => Object.values(situacao.value?.ultimas24h ?? {}).reduce((a, b) => a + b, 0))
const porta = (nome: string, protocolo: string) => {
  const e = situacao.value?.entradas.find((x) => x.nome === nome && x.protocolo === protocolo)
  return e?.portaPublica || e?.porta || ''
}

const graficoFluxos = computed<EChartsOption>(() => ({
  title: { text: 'Mensagens nas últimas 24 h', left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  series: [
    {
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['50%', '55%'],
      data: Object.entries(situacao.value?.ultimas24h ?? {})
        .filter(([, n]) => n > 0)
        .map(([f, n]) => ({ name: rotuloFluxo(f), value: n }))
    }
  ]
}))

const fortigate = computed(
  () => `config log syslogd setting
    set status enable
    set server "${servidor}"
    set port ${porta('Syslog', 'UDP')}
    set mode udp
    set format default
end
config log syslogd filter
    set severity information
    set forward-traffic enable
    set web enable
end`
)

const nxlog = computed(
  () => `<Extension gelf>
    Module      xm_gelf
</Extension>

<Input eventos>
    Module      im_msvistalog
    <QueryXML>
        <QueryList>
            <Query Id="0">
                <Select Path="Security">*</Select>
                <Select Path="System">*[System[(Level=1 or Level=2 or Level=3)] or System[(EventID=1074 or EventID=6005 or EventID=6006 or EventID=6008)]]</Select>
                <Select Path="Application">*[System[(Level=1 or Level=2)]]</Select>
            </Query>
        </QueryList>
    </QueryXML>
</Input>

<Output coti>
    Module      om_udp
    Host        ${servidor}
    Port        ${porta('GELF', 'UDP')}
    OutputType  GELF_UDP
</Output>

<Route r>
    Path        eventos => coti
</Route>`
)

const rsyslog = computed(() => `*.* @@${servidor}:${porta('Syslog', 'TCP')}`)

const carregar = async () => {
  carregando.value = true
  try {
    const [s, o] = await Promise.all([Api.getSituacao(), Api.getOrigens()])
    situacao.value = s
    origens.value = o
  } finally {
    carregando.value = false
  }
}

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  carregar()
  timer = setInterval(() => !document.hidden && carregar(), 15000)
})
onBeforeUnmount(() => timer && clearInterval(timer))
</script>

<style lang="scss" scoped>
.nota {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

pre {
  padding: 10px 12px;
  overflow-x: auto;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

p {
  margin: 0 0 8px;
  font-size: 13px;
}

.lista {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
}

code {
  padding: 0 4px;
  background: var(--el-fill-color);
  border-radius: 3px;
}
</style>
